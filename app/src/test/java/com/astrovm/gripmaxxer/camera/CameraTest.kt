package com.astrovm.gripmaxxer.camera

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.os.Looper
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.pose.PoseDetectorWrapper
import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        unmockkAll()
    }

    private class ImmediateFuture<T>(private val value: T?, private val error: Throwable? = null) : ListenableFuture<T> {
        override fun addListener(listener: Runnable, executor: Executor) = executor.execute(listener)
        override fun cancel(mayInterruptIfRunning: Boolean) = false
        override fun isCancelled() = false
        override fun isDone() = true
        override fun get(): T = error?.let { throw java.util.concurrent.ExecutionException(it) } ?: value!!
        override fun get(timeout: Long, unit: TimeUnit): T = get()
    }

    /**
     * Allocates a framework media image without running its constructor. The analyzer only hands it to
     * ML Kit, which is stubbed in these tests, so no image data is needed.
     */
    private fun bareMediaImage(): Image {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val imageClass = Class.forName("android.media.ImageReader\$SurfaceImage", false, Image::class.java.classLoader)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, imageClass) as Image
    }

    private fun yuvImage(width: Int, height: Int, rotation: Int = 0, withMedia: Boolean = true): ImageProxy {
        fun plane(size: Int, rowStride: Int, value: Byte): ImageProxy.PlaneProxy = mockk {
            every { buffer } returns ByteBuffer.wrap(ByteArray(size) { value })
            every { this@mockk.rowStride } returns rowStride
            every { pixelStride } returns 1
        }
        val planes = arrayOf(
            plane(width * height, width, 100),
            plane(width * height / 4, width / 2, 90),
            plane(width * height / 4, width / 2, 160),
        )
        val info = mockk<ImageInfo> { every { rotationDegrees } returns rotation }
        return mockk(relaxed = true) {
            every { this@mockk.width } returns width
            every { this@mockk.height } returns height
            every { this@mockk.planes } returns planes
            every { imageInfo } returns info
            every { image } returns if (withMedia) bareMediaImage() else null
        }
    }

    @Test
    fun `yuv frames convert to rotated mirrored and scaled bitmaps`() {
        val scaled = ImageProxyBitmapConverter.toDebugBitmap(yuvImage(16, 8), rotationDegrees = 90, mirrorHorizontally = true, maxWidth = 4)
        assertNotNull(scaled)
        assertEquals(4, scaled!!.width)
        assertEquals(8, scaled.height)

        val plain = ImageProxyBitmapConverter.toDebugBitmap(yuvImage(16, 8), rotationDegrees = 0, mirrorHorizontally = false)
        assertEquals(16, plain!!.width)
        assertEquals(8, plain.height)

        val mirroredOnly = ImageProxyBitmapConverter.toDebugBitmap(yuvImage(16, 8), rotationDegrees = 0, mirrorHorizontally = true)
        assertEquals(16, mirroredOnly!!.width)
    }

    @Test
    fun `camera manager binds image analysis to the front camera and stops cleanly`() = runBlocking {
        val provider = mockk<ProcessCameraProvider>(relaxed = true)
        val owner = mockk<LifecycleOwner>(relaxed = true)
        var requests = 0
        val manager = FrontCameraManager(context, owner) {
            requests++
            ImmediateFuture(provider)
        }
        val analyzer = mockk<PoseFrameAnalyzer>(relaxed = true)

        manager.start(analyzer)
        manager.start(analyzer)
        assertEquals(1, requests)
        verify(exactly = 2) { provider.unbindAll() }
        verify(exactly = 2) { provider.bindToLifecycle(owner, any(), *anyVararg()) }

        manager.stop()
        verify(exactly = 3) { provider.unbindAll() }

        // Stopping from a background thread posts the unbind to the main thread.
        manager.start(analyzer)
        val thread = Thread { manager.stop() }
        thread.start()
        thread.join()
        shadowOf(Looper.getMainLooper()).idle()
        verify(exactly = 5) { provider.unbindAll() }

        // Stopping before starting is harmless.
        FrontCameraManager(context, owner) { ImmediateFuture(provider) }.stop()
    }

    @Test
    fun `camera manager surfaces provider failures`() = runBlocking {
        val manager = FrontCameraManager(context, mockk(relaxed = true)) {
            ImmediateFuture(null, IllegalStateException("no camera"))
        }
        try {
            manager.start(mockk(relaxed = true))
            fail("expected failure")
        } catch (expected: Exception) {
            assertTrue(expected is java.util.concurrent.ExecutionException || expected.cause != null || expected is IllegalStateException)
        }
    }

    private fun analyzer(
        detector: PoseDetectorWrapper,
        frames: MutableList<PoseFrame>,
        debugFrames: MutableList<Bitmap>,
        emitDebug: Boolean = true,
        ticks: MutableList<Unit> = CopyOnWriteArrayList(),
        interval: Long = 0L,
    ) = PoseFrameAnalyzer(
        detectorWrapper = detector,
        featureExtractor = PoseFeatureExtractor(),
        minFrameIntervalMs = interval,
        onFrameTick = { ticks += Unit },
        shouldEmitDebugFrame = { emitDebug },
        onDebugFrame = { bitmap, _ -> debugFrames += bitmap },
        onPoseFrame = { frames += it },
    )

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("condition not met in time")
            Thread.sleep(10L)
        }
    }

    @Test
    fun `analyzer turns camera frames into pose frames and debug previews`() {
        mockkStatic(InputImage::class)
        every { InputImage.fromMediaImage(any(), any()) } returns mockk(relaxed = true)
        val pose = mockk<Pose> { every { getPoseLandmark(any()) } returns null }
        val detector = mockk<PoseDetectorWrapper>()
        coEvery { detector.process(any()) } returns pose
        val frames = CopyOnWriteArrayList<PoseFrame>()
        val debug = CopyOnWriteArrayList<Bitmap>()
        val ticks = CopyOnWriteArrayList<Unit>()

        val analyzer = analyzer(detector, frames, debug, ticks = ticks)
        val image = yuvImage(16, 8, rotation = 90)
        analyzer.analyze(image)
        waitFor { frames.size == 1 && debug.size == 1 }
        waitFor { runCatching { verify { image.close() } }.isSuccess }
        assertEquals(1, ticks.size)
        assertEquals(false, frames.single().posePresent)

        // A frame arriving within the debug interval is analysed without a new preview.
        analyzer.analyze(yuvImage(16, 8, rotation = 270))
        waitFor { frames.size == 2 }
        assertEquals(1, debug.size)
        analyzer.stop()
    }

    @Test
    fun `analyzer drops frames that come too fast or while busy`() {
        mockkStatic(InputImage::class)
        every { InputImage.fromMediaImage(any(), any()) } returns mockk(relaxed = true)
        val detector = mockk<PoseDetectorWrapper>()
        coEvery { detector.process(any()) } coAnswers {
            delay(300L)
            mockk { every { getPoseLandmark(any()) } returns null }
        }
        val frames = CopyOnWriteArrayList<PoseFrame>()
        val analyzer = analyzer(detector, frames, CopyOnWriteArrayList(), emitDebug = false, interval = 0L)

        val first = yuvImage(16, 8)
        val busy = yuvImage(16, 8)
        analyzer.analyze(first)
        Thread.sleep(20L)
        analyzer.analyze(busy)
        verify { busy.close() }
        waitFor { frames.size == 1 }

        analyzer.updateMinFrameIntervalMs(60_000L)
        val tooSoon = yuvImage(16, 8)
        analyzer.analyze(tooSoon)
        verify { tooSoon.close() }
        analyzer.updateMinFrameIntervalMs(-5L)
        analyzer.stop()
    }

    @Test
    fun `analyzer skips frames without media and survives detector errors and timeouts`() {
        mockkStatic(InputImage::class)
        every { InputImage.fromMediaImage(any(), any()) } returns mockk(relaxed = true)
        val detector = mockk<PoseDetectorWrapper>()
        val frames = CopyOnWriteArrayList<PoseFrame>()
        val analyzer = analyzer(detector, frames, CopyOnWriteArrayList())

        val noMedia = yuvImage(16, 8, withMedia = false)
        analyzer.analyze(noMedia)
        verify { noMedia.close() }

        coEvery { detector.process(any()) } throws IllegalStateException("boom")
        val failing = yuvImage(16, 8)
        analyzer.analyze(failing)
        waitFor { runCatching { verify { failing.close() } }.isSuccess }

        coEvery { detector.process(any()) } coAnswers {
            delay(5_000L)
            mockk()
        }
        val slow = yuvImage(16, 8)
        analyzer.analyze(slow)
        waitFor { runCatching { verify { slow.close() } }.isSuccess }
        assertTrue(frames.isEmpty())
        analyzer.stop()
    }

    @Test
    fun `debug preview is skipped when conversion fails`() {
        mockkStatic(InputImage::class)
        every { InputImage.fromMediaImage(any(), any()) } returns mockk(relaxed = true)
        mockkObject(ImageProxyBitmapConverter)
        every { ImageProxyBitmapConverter.toDebugBitmap(any(), any(), any(), any()) } returns null
        val detector = mockk<PoseDetectorWrapper>()
        coEvery { detector.process(any()) } returns mockk { every { getPoseLandmark(any()) } returns null }
        val frames = CopyOnWriteArrayList<PoseFrame>()
        val debug = CopyOnWriteArrayList<Bitmap>()
        val analyzer = analyzer(detector, frames, debug)
        analyzer.analyze(yuvImage(16, 8))
        waitFor { frames.size == 1 }
        Thread.sleep(50L)
        assertTrue(debug.isEmpty())
        assertNull(debug.firstOrNull())
        analyzer.stop()
    }
}

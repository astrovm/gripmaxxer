package com.astrovm.gripmaxxer.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.media.Image
import android.os.Looper
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.tracking.Joint
import com.astrovm.gripmaxxer.tracking.Point
import com.google.android.gms.tasks.Tasks
import com.google.common.util.concurrent.Futures
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose as MlPose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase
import com.google.mlkit.vision.pose.PoseLandmark
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PoseCameraTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val detector = mockk<PoseDetector>(relaxed = true)
    private val frames = LinkedBlockingQueue<CameraFrame>()
    private var wantsImage = false

    @Before
    fun setUp() {
        mockkStatic(PoseDetection::class, InputImage::class)
        every { PoseDetection.getClient(any<PoseDetectorOptionsBase>()) } returns detector
        every { InputImage.fromMediaImage(any(), any()) } returns mockk()
    }

    @After
    fun tearDown() = unmockkAll()

    private fun camera() = PoseCamera(context, wantsImage = { wantsImage }) { frames.put(it) }

    private fun landmark(x: Float, y: Float, inFrame: Float) = mockk<PoseLandmark> {
        every { position } returns PointF(x, y)
        every { inFrameLikelihood } returns inFrame
    }

    /** An ML Kit result with a confident nose and wrist, and a shoulder it's unsure about. */
    private fun detected(): MlPose {
        val landmarks = mapOf(
            PoseLandmark.NOSE to landmark(10f, 20f, 0.9f),
            PoseLandmark.LEFT_WRIST to landmark(30f, 40f, 0.6f),
            PoseLandmark.LEFT_SHOULDER to landmark(50f, 60f, 0.2f),
        )
        return mockk { every { getPoseLandmark(any()) } answers { landmarks[firstArg()] } }
    }

    private fun frame(rotation: Int, hasImage: Boolean = true): ImageProxy {
        val proxy = mockk<ImageProxy>(relaxed = true)
        every { proxy.image } returns if (hasImage) mockk<Image>() else null
        every { proxy.imageInfo.rotationDegrees } returns rotation
        every { proxy.width } returns 640
        every { proxy.height } returns 480
        every { proxy.toBitmap() } returns Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        return proxy
    }

    private fun nextFrame() = frames.poll(5, TimeUnit.SECONDS)!!

    @Test
    fun keepsOnlyJointsTheDetectorIsSureAbout() {
        every { detector.process(any<InputImage>()) } returns Tasks.forResult(detected())
        val proxy = frame(rotation = 270)
        camera().analyze(proxy)

        val frame = nextFrame()
        assertEquals(mapOf(Joint.NOSE to Point(10f, 20f), Joint.LEFT_WRIST to Point(30f, 40f)), frame.pose!!.joints)
        // Rotated upright: the portrait size of a landscape sensor image.
        assertEquals(480, frame.width)
        assertEquals(640, frame.height)
        assertNull(frame.image)
        verify { proxy.close() }
    }

    @Test
    fun addsAnUprightImageForThePreview() {
        wantsImage = true
        every { detector.process(any<InputImage>()) } returns Tasks.forResult(detected())
        camera().analyze(frame(rotation = 90))
        val image = nextFrame().image!!
        assertEquals(480, image.width)
        assertEquals(640, image.height)

        camera().analyze(frame(rotation = 0))
        val flat = nextFrame()
        assertEquals(640, flat.width)
        assertEquals(640, flat.image!!.width)
    }

    @Test
    fun failedOrEmptyDetectionHasNoPose() {
        every { detector.process(any<InputImage>()) } returns Tasks.forException(IllegalStateException())
        camera().analyze(frame(rotation = 180))
        assertNull(nextFrame().pose)

        val nobody = mockk<MlPose> { every { getPoseLandmark(any()) } returns null }
        every { detector.process(any<InputImage>()) } returns Tasks.forResult(nobody)
        camera().analyze(frame(rotation = 0))
        assertNull(nextFrame().pose)
    }

    @Test
    fun frameWithoutImageIsSkipped() {
        val proxy = frame(rotation = 0, hasImage = false)
        camera().analyze(proxy)
        verify { proxy.close() }
        verify(exactly = 0) { detector.process(any<InputImage>()) }
    }

    @Test
    fun framesAfterStopAreClosedButDropped() {
        val pending = com.google.android.gms.tasks.TaskCompletionSource<MlPose>()
        every { detector.process(any<InputImage>()) } returns pending.task
        wantsImage = true
        val camera = camera()
        val proxy = frame(rotation = 0)
        camera.analyze(proxy)
        camera.stop()
        pending.setResult(detected())
        verify(timeout = 5_000) { proxy.close() }
        verify(exactly = 0) { proxy.toBitmap() }
        assertTrue(frames.isEmpty())
        verify { detector.close() }
    }

    @Test
    fun bindsTheFrontCamera() {
        val provider = mockk<ProcessCameraProvider>(relaxed = true)
        mockkObject(ProcessCameraProvider.Companion)
        every { ProcessCameraProvider.getInstance(any()) } returns Futures.immediateFuture(provider)
        val owner = mockk<LifecycleOwner>()
        val useCases = slot<UseCase>()
        every { provider.bindToLifecycle(owner, any(), capture(useCases)) } returns mockk()

        val camera = camera()
        var error: Throwable? = null
        camera.start(owner) { error = it }
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(error)
        assertNotNull(useCases.captured)
        camera.stop()
        verify(exactly = 2) { provider.unbindAll() }
    }

    @Test
    fun reportsCameraErrors() {
        mockkObject(ProcessCameraProvider.Companion)
        every { ProcessCameraProvider.getInstance(any()) } returns
            Futures.immediateFailedFuture(IllegalStateException("no camera"))
        var error: Throwable? = null
        camera().start(mockk()) { error = it }
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(error)
    }
}

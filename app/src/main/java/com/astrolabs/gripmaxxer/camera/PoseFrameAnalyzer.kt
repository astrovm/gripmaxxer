package com.astrolabs.gripmaxxer.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.astrolabs.gripmaxxer.pose.PoseDetectorWrapper
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PoseFrameAnalyzer(
    private val detectorWrapper: PoseDetectorWrapper,
    private val featureExtractor: PoseFeatureExtractor,
    minFrameIntervalMs: Long = 66L,
    private val onFrameTick: (() -> Unit)? = null,
    private val shouldEmitDebugFrame: (() -> Boolean)? = null,
    private val onDebugFrame: ((android.graphics.Bitmap, PoseFrame) -> Unit)? = null,
    private val onPoseFrame: (PoseFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isProcessing = AtomicBoolean(false)
    @Volatile
    private var minFrameIntervalMs: Long = minFrameIntervalMs.coerceAtLeast(0L)
    private var lastAnalyzedMs: Long = 0L
    private var lastDebugFrameMs: Long = 0L

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedMs < minFrameIntervalMs) {
            image.close()
            return
        }
        if (!isProcessing.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastAnalyzedMs = now
        onFrameTick?.invoke()

        val mediaImage = image.image
        if (mediaImage == null) {
            Log.w(TAG, "ImageProxy had null mediaImage")
            isProcessing.set(false)
            image.close()
            return
        }

        val rotation = image.imageInfo.rotationDegrees
        val frameWidth = if (rotation == 90 || rotation == 270) image.height else image.width
        val frameHeight = if (rotation == 90 || rotation == 270) image.width else image.height
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        scope.launch {
            try {
                val pose = withTimeoutOrNull(DETECTOR_TIMEOUT_MS) {
                    detectorWrapper.process(inputImage)
                }
                if (pose == null) {
                    Log.w(TAG, "Pose detection timed out after ${DETECTOR_TIMEOUT_MS}ms")
                    return@launch
                }
                val frame = featureExtractor.toPoseFrame(
                    pose = pose,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    timestampMs = now,
                )
                onPoseFrame(frame)
                if (shouldEmitDebugFrame?.invoke() == true && now - lastDebugFrameMs >= DEBUG_FRAME_INTERVAL_MS) {
                    val bitmap = ImageProxyBitmapConverter.toDebugBitmap(
                        image = image,
                        rotationDegrees = rotation,
                        mirrorHorizontally = true,
                    )
                    if (bitmap != null) {
                        lastDebugFrameMs = now
                        onDebugFrame?.invoke(bitmap, frame)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pose frame analysis failed", e)
            } finally {
                isProcessing.set(false)
                image.close()
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    fun updateMinFrameIntervalMs(value: Long) {
        minFrameIntervalMs = value.coerceAtLeast(0L)
    }

    companion object {
        private const val TAG = "PoseFrameAnalyzer"
        private const val DETECTOR_TIMEOUT_MS = 1200L
        private const val DEBUG_FRAME_INTERVAL_MS = 250L
    }
}

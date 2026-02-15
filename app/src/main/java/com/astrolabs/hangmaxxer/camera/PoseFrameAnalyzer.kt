package com.astrolabs.hangmaxxer.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.astrolabs.hangmaxxer.pose.PoseDetectorWrapper
import com.astrolabs.hangmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.hangmaxxer.pose.PoseFrame
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PoseFrameAnalyzer(
    private val detectorWrapper: PoseDetectorWrapper,
    private val featureExtractor: PoseFeatureExtractor,
    private val minFrameIntervalMs: Long = 66L,
    private val onPoseFrame: (PoseFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isProcessing = AtomicBoolean(false)
    private var lastAnalyzedMs: Long = 0L

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

        val mediaImage = image.image
        if (mediaImage == null) {
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
                val pose = detectorWrapper.process(inputImage)
                val frame = featureExtractor.toPoseFrame(
                    pose = pose,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    timestampMs = now,
                )
                onPoseFrame(frame)
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

    companion object {
        private const val TAG = "PoseFrameAnalyzer"
    }
}

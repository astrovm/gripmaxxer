package com.astrolabs.hangmaxxer.pose

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class PoseDetectorWrapper(initialAccurateMode: Boolean = false) {

    private val detectorMutex = Mutex()
    private var accurateMode: Boolean = initialAccurateMode
    private var detector: PoseDetector = buildDetector(initialAccurateMode)

    suspend fun setAccurateMode(enabled: Boolean) {
        detectorMutex.withLock {
            if (accurateMode == enabled) return
            detector.close()
            detector = buildDetector(enabled)
            accurateMode = enabled
        }
    }

    suspend fun process(image: InputImage): Pose {
        return detectorMutex.withLock {
            detector.process(image).await()
        }
    }

    suspend fun close() {
        detectorMutex.withLock {
            detector.close()
        }
    }

    private fun buildDetector(accurate: Boolean): PoseDetector {
        return if (accurate) {
            val options = AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptionsBase.CPU)
                .build()
            PoseDetection.getClient(options)
        } else {
            val options = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptionsBase.CPU)
                .build()
            PoseDetection.getClient(options)
        }
    }
}

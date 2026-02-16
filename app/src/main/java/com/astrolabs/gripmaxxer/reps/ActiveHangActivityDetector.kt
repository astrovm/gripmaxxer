package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.hang.HangDetectionConfig
import com.astrolabs.gripmaxxer.hang.HangDetector
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame

class ActiveHangActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private val hangDetector = HangDetector()

    fun updateConfig(config: HangDetectionConfig) {
        hangDetector.updateConfig(config)
    }

    override fun reset() {
        hangDetector.reset()
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val hanging = hangDetector.process(frame = frame, nowMs = nowMs).isHanging
        if (!hanging) return false

        val elbow = featureExtractor.elbowAngleDegrees(frame) ?: return false
        return elbow <= ACTIVE_HANG_MAX_ELBOW_ANGLE
    }

    companion object {
        private const val ACTIVE_HANG_MAX_ELBOW_ANGLE = 132f
    }
}

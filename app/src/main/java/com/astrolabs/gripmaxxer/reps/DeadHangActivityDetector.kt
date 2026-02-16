package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.hang.HangDetectionConfig
import com.astrolabs.gripmaxxer.hang.HangDetector
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame

class DeadHangActivityDetector(
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

        val elbow = featureExtractor.elbowAngleDegrees(frame) ?: return true
        return elbow >= DEAD_HANG_MIN_ELBOW_ANGLE
    }

    companion object {
        private const val DEAD_HANG_MIN_ELBOW_ANGLE = 148f
    }
}

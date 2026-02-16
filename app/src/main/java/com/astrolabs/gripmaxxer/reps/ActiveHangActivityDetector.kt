package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.hang.HangDetectionConfig
import com.astrolabs.gripmaxxer.hang.HangDetector
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame

class ActiveHangActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private val hangDetector = HangDetector()
    private var modeConfirmed = false
    private var aboveThresholdSinceMs: Long? = null
    private var missingAngleSinceMs: Long? = null

    fun updateConfig(config: HangDetectionConfig) {
        hangDetector.updateConfig(config)
    }

    override fun reset() {
        hangDetector.reset()
        modeConfirmed = false
        aboveThresholdSinceMs = null
        missingAngleSinceMs = null
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val hanging = hangDetector.process(frame = frame, nowMs = nowMs).isHanging
        if (!hanging) {
            modeConfirmed = false
            aboveThresholdSinceMs = null
            missingAngleSinceMs = null
            return false
        }

        val elbow = featureExtractor.elbowAngleDegrees(frame)

        if (!modeConfirmed) {
            if (elbow != null && elbow <= ACTIVE_HANG_ENTRY_MAX_ELBOW_ANGLE) {
                modeConfirmed = true
                aboveThresholdSinceMs = null
                missingAngleSinceMs = null
                return true
            }
            return false
        }

        if (elbow != null) {
            missingAngleSinceMs = null
            if (elbow <= ACTIVE_HANG_EXIT_MAX_ELBOW_ANGLE) {
                aboveThresholdSinceMs = null
                return true
            }
            if (aboveThresholdSinceMs == null) {
                aboveThresholdSinceMs = nowMs
            }
            return nowMs - (aboveThresholdSinceMs ?: nowMs) <= ACTIVE_HANG_ABOVE_THRESHOLD_GRACE_MS
        }

        aboveThresholdSinceMs = null
        if (missingAngleSinceMs == null) {
            missingAngleSinceMs = nowMs
        }
        return nowMs - (missingAngleSinceMs ?: nowMs) <= ACTIVE_HANG_MISSING_ANGLE_GRACE_MS
    }

    companion object {
        private const val ACTIVE_HANG_ENTRY_MAX_ELBOW_ANGLE = 132f
        private const val ACTIVE_HANG_EXIT_MAX_ELBOW_ANGLE = 146f
        private const val ACTIVE_HANG_ABOVE_THRESHOLD_GRACE_MS = 1200L
        private const val ACTIVE_HANG_MISSING_ANGLE_GRACE_MS = 1200L
    }
}

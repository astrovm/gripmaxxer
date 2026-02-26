package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.hang.HangDetectionConfig
import com.astrovm.gripmaxxer.hang.HangDetector
import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame

class DeadHangActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private val hangDetector = HangDetector()
    private var modeConfirmed = false
    private var belowThresholdSinceMs: Long? = null
    private var missingAngleSinceMs: Long? = null

    fun updateConfig(config: HangDetectionConfig) {
        hangDetector.updateConfig(config)
    }

    override fun reset() {
        hangDetector.reset()
        modeConfirmed = false
        belowThresholdSinceMs = null
        missingAngleSinceMs = null
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val hanging = hangDetector.process(frame = frame, nowMs = nowMs).isHanging
        if (!hanging) {
            modeConfirmed = false
            belowThresholdSinceMs = null
            missingAngleSinceMs = null
            return false
        }

        val elbow = featureExtractor.elbowAngleDegrees(frame)

        if (!modeConfirmed) {
            if (elbow != null && elbow >= DEAD_HANG_ENTRY_MIN_ELBOW_ANGLE) {
                modeConfirmed = true
                belowThresholdSinceMs = null
                missingAngleSinceMs = null
                return true
            }
            return false
        }

        if (elbow != null) {
            missingAngleSinceMs = null
            if (elbow >= DEAD_HANG_EXIT_MIN_ELBOW_ANGLE) {
                belowThresholdSinceMs = null
                return true
            }
            if (belowThresholdSinceMs == null) {
                belowThresholdSinceMs = nowMs
            }
            return nowMs - (belowThresholdSinceMs ?: nowMs) <= DEAD_HANG_BELOW_THRESHOLD_GRACE_MS
        }

        belowThresholdSinceMs = null
        if (missingAngleSinceMs == null) {
            missingAngleSinceMs = nowMs
        }
        return nowMs - (missingAngleSinceMs ?: nowMs) <= DEAD_HANG_MISSING_ANGLE_GRACE_MS
    }

    companion object {
        private const val DEAD_HANG_ENTRY_MIN_ELBOW_ANGLE = 150f
        private const val DEAD_HANG_EXIT_MIN_ELBOW_ANGLE = 140f
        private const val DEAD_HANG_BELOW_THRESHOLD_GRACE_MS = 1300L
        private const val DEAD_HANG_MISSING_ANGLE_GRACE_MS = 1400L
    }
}

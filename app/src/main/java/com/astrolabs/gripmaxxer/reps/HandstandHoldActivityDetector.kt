package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class HandstandHoldActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private var active = false
    private var entryCandidateSinceMs: Long? = null
    private var exitCandidateSinceMs: Long? = null
    private var missingPoseSinceMs: Long? = null

    override fun reset() {
        active = false
        entryCandidateSinceMs = null
        exitCandidateSinceMs = null
        missingPoseSinceMs = null
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val poseState = evaluatePose(frame)

        if (poseState == PoseState.HANDSTAND) {
            missingPoseSinceMs = null
            exitCandidateSinceMs = null
            if (active) return true
            if (entryCandidateSinceMs == null) {
                entryCandidateSinceMs = nowMs
            }
            if (nowMs - (entryCandidateSinceMs ?: nowMs) >= ENTRY_STABLE_MS) {
                active = true
                entryCandidateSinceMs = null
                return true
            }
            return false
        }

        entryCandidateSinceMs = null
        if (!active) return false

        return if (poseState == PoseState.UNKNOWN) {
            if (missingPoseSinceMs == null) {
                missingPoseSinceMs = nowMs
            }
            nowMs - (missingPoseSinceMs ?: nowMs) <= MISSING_POSE_GRACE_MS
        } else {
            missingPoseSinceMs = null
            if (exitCandidateSinceMs == null) {
                exitCandidateSinceMs = nowMs
            }
            val stillActive = nowMs - (exitCandidateSinceMs ?: nowMs) <= EXIT_STABLE_MS
            if (!stillActive) {
                active = false
                exitCandidateSinceMs = null
            }
            stillActive
        }
    }

    private fun evaluatePose(frame: PoseFrame): PoseState {
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            ?: return PoseState.UNKNOWN
        val wristY = frame.averageY(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
            ?: return PoseState.UNKNOWN
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            ?: return PoseState.UNKNOWN

        val shouldersOverHands = wristY - shoulderY >= SHOULDER_OVER_HANDS_MIN_DELTA
        if (!shouldersOverHands) return PoseState.NOT_HANDSTAND

        val hipsAboveShoulders = shoulderY - hipY >= HIPS_ABOVE_SHOULDERS_MIN_DELTA
        if (!hipsAboveShoulders) return PoseState.NOT_HANDSTAND

        val kneeY = frame.averageY(PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE)
        val ankleY = frame.averageY(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)
        val legsRaised = when {
            ankleY != null -> (hipY - ankleY) >= FEET_ABOVE_HIPS_MIN_DELTA
            kneeY != null -> (hipY - kneeY) >= KNEES_ABOVE_HIPS_MIN_DELTA
            else -> false
        }
        if (!legsRaised) return PoseState.NOT_HANDSTAND

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
        if (elbowAngle != null && elbowAngle < MIN_SUPPORT_ELBOW_ANGLE_DEG) {
            return PoseState.NOT_HANDSTAND
        }

        return PoseState.HANDSTAND
    }

    private enum class PoseState {
        HANDSTAND,
        NOT_HANDSTAND,
        UNKNOWN,
    }

    companion object {
        private const val ENTRY_STABLE_MS = 450L
        private const val EXIT_STABLE_MS = 850L
        private const val MISSING_POSE_GRACE_MS = 900L

        private const val SHOULDER_OVER_HANDS_MIN_DELTA = 0.03f
        private const val HIPS_ABOVE_SHOULDERS_MIN_DELTA = 0.04f
        private const val FEET_ABOVE_HIPS_MIN_DELTA = 0.03f
        private const val KNEES_ABOVE_HIPS_MIN_DELTA = 0.02f
        private const val MIN_SUPPORT_ELBOW_ANGLE_DEG = 145f
    }
}

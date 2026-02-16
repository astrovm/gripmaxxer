package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class PlankHoldActivityDetector(
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

        if (poseState == PoseState.PLANK) {
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
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            ?: return PoseState.UNKNOWN

        val bodyFlat = abs(shoulderY - hipY) <= BODY_FLAT_MAX_DELTA
        if (!bodyFlat) return PoseState.NOT_PLANK

        val hipAngle = featureExtractor.hipAngleDegrees(frame)
        if (hipAngle != null && hipAngle < MIN_HIP_ANGLE_DEG) {
            return PoseState.NOT_PLANK
        }

        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)
        if (kneeAngle != null && kneeAngle < MIN_KNEE_ANGLE_DEG) {
            return PoseState.NOT_PLANK
        }

        val legAnchorY = frame.averageY(PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE)
            ?: frame.averageY(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)
            ?: return PoseState.UNKNOWN
        val legsBehindTorso = legAnchorY > hipY + LEGS_BEHIND_HIP_MIN_DELTA
        if (!legsBehindTorso) return PoseState.NOT_PLANK

        return PoseState.PLANK
    }

    private enum class PoseState {
        PLANK,
        NOT_PLANK,
        UNKNOWN,
    }

    companion object {
        private const val ENTRY_STABLE_MS = 450L
        private const val EXIT_STABLE_MS = 900L
        private const val MISSING_POSE_GRACE_MS = 900L

        private const val BODY_FLAT_MAX_DELTA = 0.10f
        private const val LEGS_BEHIND_HIP_MIN_DELTA = 0.02f
        private const val MIN_HIP_ANGLE_DEG = 145f
        private const val MIN_KNEE_ANGLE_DEG = 140f
    }
}

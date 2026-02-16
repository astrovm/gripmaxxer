package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MiddleSplitHoldActivityDetector(
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

        if (poseState == PoseState.MIDDLE_SPLIT) {
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
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return PoseState.UNKNOWN
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return PoseState.UNKNOWN
        val leftHip = frame.landmark(PoseLandmark.LEFT_HIP) ?: return PoseState.UNKNOWN
        val rightHip = frame.landmark(PoseLandmark.RIGHT_HIP) ?: return PoseState.UNKNOWN
        val leftAnkle = frame.landmark(PoseLandmark.LEFT_ANKLE) ?: return PoseState.UNKNOWN
        val rightAnkle = frame.landmark(PoseLandmark.RIGHT_ANKLE) ?: return PoseState.UNKNOWN

        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) return PoseState.UNKNOWN

        val ankleSpan = abs(leftAnkle.x - rightAnkle.x)
        val wideEnough = ankleSpan >= shoulderWidth * MIN_ANKLE_TO_SHOULDER_WIDTH_RATIO
        if (!wideEnough) return PoseState.NOT_MIDDLE_SPLIT

        val hipCenterX = (leftHip.x + rightHip.x) / 2f
        val minAnkleX = min(leftAnkle.x, rightAnkle.x)
        val maxAnkleX = max(leftAnkle.x, rightAnkle.x)
        val hipsCentered = hipCenterX in (minAnkleX - HIP_CENTER_MARGIN_X)..(maxAnkleX + HIP_CENTER_MARGIN_X)
        if (!hipsCentered) return PoseState.NOT_MIDDLE_SPLIT

        val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipY = (leftHip.y + rightHip.y) / 2f
        val anklesY = (leftAnkle.y + rightAnkle.y) / 2f

        val hipsLowerThanTorso = hipY - shoulderY >= MIN_HIP_DROP_FROM_SHOULDERS
        if (!hipsLowerThanTorso) return PoseState.NOT_MIDDLE_SPLIT

        val hipsNearLegPlane = abs(hipY - anklesY) <= MAX_HIP_TO_ANKLE_Y_DELTA
        if (!hipsNearLegPlane) return PoseState.NOT_MIDDLE_SPLIT

        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)
        if (kneeAngle != null && kneeAngle < MIN_KNEE_ANGLE_DEG) {
            return PoseState.NOT_MIDDLE_SPLIT
        }

        return PoseState.MIDDLE_SPLIT
    }

    private enum class PoseState {
        MIDDLE_SPLIT,
        NOT_MIDDLE_SPLIT,
        UNKNOWN,
    }

    companion object {
        private const val ENTRY_STABLE_MS = 500L
        private const val EXIT_STABLE_MS = 1000L
        private const val MISSING_POSE_GRACE_MS = 1000L

        private const val MIN_SHOULDER_WIDTH = 0.08f
        private const val MIN_ANKLE_TO_SHOULDER_WIDTH_RATIO = 1.9f
        private const val HIP_CENTER_MARGIN_X = 0.10f
        private const val MIN_HIP_DROP_FROM_SHOULDERS = 0.10f
        private const val MAX_HIP_TO_ANKLE_Y_DELTA = 0.25f
        private const val MIN_KNEE_ANGLE_DEG = 150f
    }
}

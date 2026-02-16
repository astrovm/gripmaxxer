package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class BulgarianSplitSquatActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private var active = false
    private var lastMotionMs = 0L
    private var lastWorkingKneeAngle: Float? = null
    private val sideTracker = BodySideTracker(
        switchDelta = SIDE_SWITCH_DELTA_DEG,
        switchStableMs = SIDE_SWITCH_STABLE_MS,
    )

    override fun reset() {
        active = false
        lastMotionMs = 0L
        lastWorkingKneeAngle = null
        sideTracker.reset()
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        if (!hasSplitStance(frame)) return decayActive(nowMs)

        val side = selectTrackedSide(frame, nowMs) ?: return decayActive(nowMs)
        val workingKnee = featureExtractor.kneeAngleDegrees(frame, side) ?: return decayActive(nowMs)
        val rearKnee = featureExtractor.kneeAngleDegrees(frame, opposite(side))

        val angleMotion = lastWorkingKneeAngle?.let { abs(it - workingKnee) > WORKING_KNEE_MOTION_MIN_DELTA } ?: false
        val motionDetected = angleMotion ||
            workingKnee < ACTIVE_WORKING_KNEE_MAX ||
            (rearKnee != null && rearKnee < ACTIVE_REAR_KNEE_MAX)
        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }
        lastWorkingKneeAngle = workingKnee

        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    private fun hasSplitStance(frame: PoseFrame): Boolean {
        val leftAnkle = frame.landmark(PoseLandmark.LEFT_ANKLE) ?: return false
        val rightAnkle = frame.landmark(PoseLandmark.RIGHT_ANKLE) ?: return false
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) return false
        return abs(leftAnkle.x - rightAnkle.x) > shoulderWidth * SPLIT_STANCE_MIN_RATIO
    }

    private fun selectTrackedSide(frame: PoseFrame, nowMs: Long): BodySide? {
        val leftKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.LEFT)
        val rightKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.RIGHT)
        return sideTracker.selectLower(leftKnee, rightKnee, nowMs)
    }

    private fun opposite(side: BodySide): BodySide {
        return if (side == BodySide.LEFT) BodySide.RIGHT else BodySide.LEFT
    }

    private fun decayActive(nowMs: Long): Boolean {
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1700L
        private const val SIDE_SWITCH_DELTA_DEG = 7f
        private const val SIDE_SWITCH_STABLE_MS = 320L
        private const val MIN_SHOULDER_WIDTH = 0.08f
        private const val SPLIT_STANCE_MIN_RATIO = 1.0f
        private const val WORKING_KNEE_MOTION_MIN_DELTA = 1.6f
        private const val ACTIVE_WORKING_KNEE_MAX = 166f
        private const val ACTIVE_REAR_KNEE_MAX = 163f
    }
}

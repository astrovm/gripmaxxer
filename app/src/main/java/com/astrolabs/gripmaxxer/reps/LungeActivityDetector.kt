package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class LungeActivityDetector(
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
        if (!hasLungeStance(frame)) return decayActive(nowMs)

        val side = selectTrackedSide(frame, nowMs) ?: return decayActive(nowMs)
        val workingKnee = featureExtractor.kneeAngleDegrees(frame, side) ?: return decayActive(nowMs)
        val rearKnee = featureExtractor.kneeAngleDegrees(frame, opposite(side))

        val angleMotion = lastWorkingKneeAngle?.let { abs(it - workingKnee) > 1.5f } ?: false
        val motionDetected = angleMotion || workingKnee < 165f || (rearKnee != null && rearKnee < 165f)
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

    private fun selectTrackedSide(frame: PoseFrame, nowMs: Long): BodySide? {
        val leftKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.LEFT)
        val rightKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.RIGHT)
        return sideTracker.selectLower(leftKnee, rightKnee, nowMs)
    }

    private fun opposite(side: BodySide): BodySide {
        return if (side == BodySide.LEFT) BodySide.RIGHT else BodySide.LEFT
    }

    private fun hasLungeStance(frame: PoseFrame): Boolean {
        val leftAnkle = frame.landmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = frame.landmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER)
        if (leftAnkle == null || rightAnkle == null || leftShoulder == null || rightShoulder == null) {
            return true
        }
        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < 0.08f) return true
        val ankleSpan = abs(leftAnkle.x - rightAnkle.x)
        return ankleSpan >= shoulderWidth * MIN_ANKLE_TO_SHOULDER_WIDTH_RATIO
    }

    private fun decayActive(nowMs: Long): Boolean {
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1500L
        private const val SIDE_SWITCH_DELTA_DEG = 6f
        private const val SIDE_SWITCH_STABLE_MS = 280L
        private const val MIN_ANKLE_TO_SHOULDER_WIDTH_RATIO = 0.55f
    }
}

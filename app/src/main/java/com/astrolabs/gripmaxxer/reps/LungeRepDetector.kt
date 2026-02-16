package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class LungeRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 220L,
        minRepIntervalMs = 580L,
    )

    private val sideTracker = BodySideTracker(
        switchDelta = SIDE_SWITCH_DELTA_DEG,
        switchStableMs = SIDE_SWITCH_STABLE_MS,
    )

    override fun reset() {
        cycleCounter.reset()
        sideTracker.reset()
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        if (!hasLungeStance(frame)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val side = selectTrackedSide(frame, nowMs) ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val workingKnee = featureExtractor.kneeAngleDegrees(frame, side)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val rearKnee = featureExtractor.kneeAngleDegrees(frame, opposite(side))

        val isDown = workingKnee < DOWN_WORKING_KNEE_MAX && (rearKnee == null || rearKnee < DOWN_REAR_KNEE_MAX)
        val isUp = workingKnee > UP_WORKING_KNEE_MIN && (rearKnee == null || rearKnee > UP_REAR_KNEE_MIN)
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
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

    companion object {
        private const val SIDE_SWITCH_DELTA_DEG = 6f
        private const val SIDE_SWITCH_STABLE_MS = 280L
        private const val MIN_ANKLE_TO_SHOULDER_WIDTH_RATIO = 0.68f
        private const val DOWN_WORKING_KNEE_MAX = 114f
        private const val DOWN_REAR_KNEE_MAX = 152f
        private const val UP_WORKING_KNEE_MIN = 164f
        private const val UP_REAR_KNEE_MIN = 145f
    }
}

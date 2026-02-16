package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class BulgarianSplitSquatRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 220L,
        minRepIntervalMs = 600L,
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
        if (!active || !hasSplitStance(frame)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val side = selectTrackedSide(frame, nowMs) ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val workingKnee = featureExtractor.kneeAngleDegrees(frame, side)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val rearKnee = featureExtractor.kneeAngleDegrees(frame, opposite(side))
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)

        val isDown = workingKnee < 108f && rearKnee < 150f
        val isUp = workingKnee > 160f && rearKnee in REAR_KNEE_UP_MIN..REAR_KNEE_UP_MAX
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
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

    companion object {
        private const val SIDE_SWITCH_DELTA_DEG = 7f
        private const val SIDE_SWITCH_STABLE_MS = 320L
        private const val MIN_SHOULDER_WIDTH = 0.08f
        private const val SPLIT_STANCE_MIN_RATIO = 0.95f
        private const val REAR_KNEE_UP_MIN = 108f
        private const val REAR_KNEE_UP_MAX = 170f
    }
}

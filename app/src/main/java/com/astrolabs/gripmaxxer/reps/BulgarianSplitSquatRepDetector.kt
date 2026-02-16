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

    private var trackedSide: BodySide? = null

    override fun reset() {
        cycleCounter.reset()
        trackedSide = null
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active || !hasSplitStance(frame)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val side = selectTrackedSide(frame) ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
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
        return abs(leftAnkle.x - rightAnkle.x) > SPLIT_STANCE_MIN_X_DELTA
    }

    private fun selectTrackedSide(frame: PoseFrame): BodySide? {
        val leftKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.LEFT)
        val rightKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.RIGHT)
        val selected = when {
            leftKnee != null && rightKnee != null -> {
                if (leftKnee <= rightKnee - SIDE_SWITCH_DELTA_DEG) BodySide.LEFT
                else if (rightKnee <= leftKnee - SIDE_SWITCH_DELTA_DEG) BodySide.RIGHT
                else trackedSide ?: if (leftKnee <= rightKnee) BodySide.LEFT else BodySide.RIGHT
            }
            leftKnee != null -> BodySide.LEFT
            rightKnee != null -> BodySide.RIGHT
            else -> null
        }
        trackedSide = selected ?: trackedSide
        return trackedSide
    }

    private fun opposite(side: BodySide): BodySide {
        return if (side == BodySide.LEFT) BodySide.RIGHT else BodySide.LEFT
    }

    companion object {
        private const val SIDE_SWITCH_DELTA_DEG = 7f
        private const val SPLIT_STANCE_MIN_X_DELTA = 0.12f
        private const val REAR_KNEE_UP_MIN = 108f
        private const val REAR_KNEE_UP_MAX = 170f
    }
}

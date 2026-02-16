package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame

class LungeRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 210L,
        minRepIntervalMs = 550L,
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
        if (!active) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val side = selectTrackedSide(frame) ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val workingKnee = featureExtractor.kneeAngleDegrees(frame, side)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val rearKnee = featureExtractor.kneeAngleDegrees(frame, opposite(side))

        val isDown = workingKnee < 112f && (rearKnee == null || rearKnee < 155f)
        val isUp = workingKnee > 162f && (rearKnee == null || rearKnee > 142f)
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
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
        private const val SIDE_SWITCH_DELTA_DEG = 6f
    }
}

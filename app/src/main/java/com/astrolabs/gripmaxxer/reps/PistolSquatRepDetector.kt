package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame

class PistolSquatRepDetector(
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
        if (!active) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val side = selectTrackedSide(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame, side)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val hipAngle = featureExtractor.hipAngleDegrees(frame, side)

        val isDown = kneeAngle < 102f || (hipAngle != null && hipAngle < 112f)
        val isUp = kneeAngle > 166f && (hipAngle == null || hipAngle > 148f)
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    private fun selectTrackedSide(frame: PoseFrame): BodySide? {
        val leftKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.LEFT)
        val rightKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.RIGHT)
        val chosen = when {
            leftKnee != null && rightKnee != null -> {
                if (leftKnee <= rightKnee - SIDE_SWITCH_DELTA_DEG) BodySide.LEFT
                else if (rightKnee <= leftKnee - SIDE_SWITCH_DELTA_DEG) BodySide.RIGHT
                else trackedSide
            }
            leftKnee != null -> BodySide.LEFT
            rightKnee != null -> BodySide.RIGHT
            else -> null
        }
        trackedSide = chosen ?: trackedSide
        return trackedSide
    }

    companion object {
        private const val SIDE_SWITCH_DELTA_DEG = 12f
    }
}

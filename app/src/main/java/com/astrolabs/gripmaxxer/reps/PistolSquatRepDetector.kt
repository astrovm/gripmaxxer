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

        val side = selectTrackedSide(frame, nowMs)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame, side)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val hipAngle = featureExtractor.hipAngleDegrees(frame, side)

        val isDown = kneeAngle < 102f || (hipAngle != null && hipAngle < 112f)
        val isUp = kneeAngle > 166f && (hipAngle == null || hipAngle > 148f)
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    private fun selectTrackedSide(frame: PoseFrame, nowMs: Long): BodySide? {
        val leftKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.LEFT)
        val rightKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.RIGHT)
        return sideTracker.selectLower(leftKnee, rightKnee, nowMs)
    }

    companion object {
        private const val SIDE_SWITCH_DELTA_DEG = 12f
        private const val SIDE_SWITCH_STABLE_MS = 350L
    }
}

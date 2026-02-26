package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.BodySide
import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame

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
        val supportKnee = featureExtractor.kneeAngleDegrees(frame, opposite(side))
        val hipAngle = featureExtractor.hipAngleDegrees(frame, side)
        val supportLegReady = supportKnee == null || supportKnee > SUPPORT_KNEE_MIN

        val isDown = supportLegReady && (kneeAngle < DOWN_KNEE_MAX || (hipAngle != null && hipAngle < DOWN_HIP_MAX))
        val isUp = supportLegReady && kneeAngle > UP_KNEE_MIN && (hipAngle == null || hipAngle > UP_HIP_MIN)
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

    companion object {
        private const val SIDE_SWITCH_DELTA_DEG = 12f
        private const val SIDE_SWITCH_STABLE_MS = 350L
        private const val SUPPORT_KNEE_MIN = 146f
        private const val DOWN_KNEE_MAX = 102f
        private const val DOWN_HIP_MAX = 112f
        private const val UP_KNEE_MIN = 167f
        private const val UP_HIP_MIN = 150f
    }
}

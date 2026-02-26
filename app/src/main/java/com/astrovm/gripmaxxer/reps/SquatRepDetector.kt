package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame

class SquatRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 220L,
        minRepIntervalMs = 560L,
    )

    override fun reset() {
        cycleCounter.reset()
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val hipAngle = featureExtractor.hipAngleDegrees(frame)

        val isDown = kneeAngle < DOWN_KNEE_MAX || (hipAngle != null && hipAngle < DOWN_HIP_MAX)
        val isUp = kneeAngle > UP_KNEE_MIN && (hipAngle == null || hipAngle > UP_HIP_MIN)
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    companion object {
        private const val DOWN_KNEE_MAX = 104f
        private const val DOWN_HIP_MAX = 120f
        private const val UP_KNEE_MIN = 164f
        private const val UP_HIP_MIN = 150f
    }
}

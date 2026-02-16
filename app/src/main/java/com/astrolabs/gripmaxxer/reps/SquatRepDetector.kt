package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame

class SquatRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 200L,
        minRepIntervalMs = 500L,
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

        val isDown = kneeAngle < 100f || (hipAngle != null && hipAngle < 115f)
        val isUp = kneeAngle > 160f && (hipAngle == null || hipAngle > 145f)
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }
}

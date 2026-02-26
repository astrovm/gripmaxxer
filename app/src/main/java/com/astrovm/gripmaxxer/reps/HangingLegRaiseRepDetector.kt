package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class HangingLegRaiseRepDetector : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 120L,
        minRepIntervalMs = 430L,
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

        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val kneeY = frame.averageY(PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val ankleY = frame.averageY(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)

        val kneeLift = hipY - kneeY
        val ankleLift = ankleY?.let { hipY - it }

        val isUp = kneeLift >= KNEE_UP_LIFT_THRESHOLD ||
            (ankleLift != null && ankleLift >= ANKLE_UP_LIFT_THRESHOLD)
        // Use knee-driven reset to avoid ankle jitter causing false "down" transitions
        // (which can lead to ghost/double reps on fast movement).
        val isDown = kneeLift <= KNEE_DOWN_LIFT_THRESHOLD

        return cycleCounter.process(
            isDown = isDown,
            isUp = isUp,
            nowMs = nowMs,
        )
    }

    companion object {
        private const val KNEE_UP_LIFT_THRESHOLD = 0.035f
        private const val KNEE_DOWN_LIFT_THRESHOLD = 0.012f
        private const val ANKLE_UP_LIFT_THRESHOLD = 0.020f
        private const val ANKLE_DOWN_LIFT_THRESHOLD = 0.005f
    }
}

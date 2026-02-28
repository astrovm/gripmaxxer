package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class HangingLegRaiseRepDetector : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 110L,
        minRepIntervalMs = 320L,
    )
    private var lastActiveAtMs = Long.MIN_VALUE

    override fun reset() {
        cycleCounter.reset()
        lastActiveAtMs = Long.MIN_VALUE
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (active) {
            lastActiveAtMs = nowMs
        }
        val activeWithGrace = active || (nowMs - lastActiveAtMs) <= ACTIVE_GRACE_MS
        if (!activeWithGrace) {
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
        // Also accept clearly lowered ankles for users who keep a slight knee bend at the bottom.
        val isDown = kneeLift <= KNEE_DOWN_LIFT_THRESHOLD ||
            (ankleLift != null && ankleLift <= ANKLE_DOWN_LIFT_THRESHOLD)

        return cycleCounter.process(
            isDown = isDown,
            isUp = isUp,
            nowMs = nowMs,
        )
    }

    companion object {
        private const val ACTIVE_GRACE_MS = 450L
        private const val KNEE_UP_LIFT_THRESHOLD = 0.030f
        // Tolerate slight knee bend at the bottom so controlled partial descents still reset the cycle.
        private const val KNEE_DOWN_LIFT_THRESHOLD = 0.028f
        private const val ANKLE_UP_LIFT_THRESHOLD = 0.020f
        private const val ANKLE_DOWN_LIFT_THRESHOLD = 0.010f
    }
}

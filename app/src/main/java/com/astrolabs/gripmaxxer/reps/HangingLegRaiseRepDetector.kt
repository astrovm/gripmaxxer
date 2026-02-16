package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.max

class HangingLegRaiseRepDetector : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 180L,
        minRepIntervalMs = 550L,
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
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val kneeY = frame.averageY(PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val ankleY = frame.averageY(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE)

        val torsoLength = max(abs(hipY - shoulderY), MIN_TORSO_LENGTH)
        val kneeUpThreshold = max(KNEE_UP_LIFT_THRESHOLD, torsoLength * KNEE_UP_LIFT_TORSO_RATIO)
        val kneeDownThreshold = max(KNEE_DOWN_LIFT_THRESHOLD, torsoLength * KNEE_DOWN_LIFT_TORSO_RATIO)
        val ankleUpThreshold = max(ANKLE_UP_LIFT_THRESHOLD, torsoLength * ANKLE_UP_LIFT_TORSO_RATIO)
        val ankleDownThreshold = max(ANKLE_DOWN_LIFT_THRESHOLD, torsoLength * ANKLE_DOWN_LIFT_TORSO_RATIO)

        val kneeLift = hipY - kneeY
        val ankleLift = ankleY?.let { hipY - it }

        val isUp = kneeLift >= kneeUpThreshold ||
            (ankleLift != null && ankleLift >= ankleUpThreshold)
        val isDown = kneeLift <= kneeDownThreshold &&
            (ankleLift == null || ankleLift <= ankleDownThreshold)

        return cycleCounter.process(
            isDown = isDown,
            isUp = isUp,
            nowMs = nowMs,
        )
    }

    companion object {
        private const val MIN_TORSO_LENGTH = 0.10f
        private const val KNEE_UP_LIFT_THRESHOLD = 0.035f
        private const val KNEE_DOWN_LIFT_THRESHOLD = 0.012f
        private const val ANKLE_UP_LIFT_THRESHOLD = 0.020f
        private const val ANKLE_DOWN_LIFT_THRESHOLD = 0.005f
        private const val KNEE_UP_LIFT_TORSO_RATIO = 0.18f
        private const val KNEE_DOWN_LIFT_TORSO_RATIO = 0.07f
        private const val ANKLE_UP_LIFT_TORSO_RATIO = 0.10f
        private const val ANKLE_DOWN_LIFT_TORSO_RATIO = 0.03f
    }
}

package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class HipThrustRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 220L,
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

        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)
        if (kneeAngle != null && (kneeAngle < MIN_KNEE_ANGLE || kneeAngle > MAX_KNEE_ANGLE)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }
        if (kotlin.math.abs(hipY - shoulderY) > MAX_HIP_TO_SHOULDER_Y_DELTA) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val hipAngle = featureExtractor.hipAngleDegrees(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val isDown = hipAngle < 128f
        val isUp = hipAngle > 162f
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    companion object {
        private const val MIN_KNEE_ANGLE = 70f
        private const val MAX_KNEE_ANGLE = 178f
        private const val MAX_HIP_TO_SHOULDER_Y_DELTA = 0.34f
    }
}

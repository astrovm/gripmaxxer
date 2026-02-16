package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class PushUpRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 170L,
        minRepIntervalMs = 450L,
    )

    private var baselineShoulderY: Float? = null

    override fun reset() {
        cycleCounter.reset()
        baselineShoulderY = null
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active || !hasRequiredTorso(frame) || !isLikelyStandardPushUp(frame)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)

        if (baselineShoulderY == null) {
            baselineShoulderY = shoulderY
        }
        if (elbowAngle > 150f) {
            baselineShoulderY = blend(baselineShoulderY ?: shoulderY, shoulderY, 0.1f)
        }

        val baseline = baselineShoulderY ?: shoulderY
        val shoulderDrop = shoulderY - baseline
        val isDown = elbowAngle < 95f && shoulderDrop > 0.015f
        val isUp = elbowAngle > 155f && shoulderDrop < 0.012f
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    private fun hasRequiredTorso(frame: PoseFrame): Boolean {
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        return shoulderY != null && hipY != null
    }

    private fun isLikelyStandardPushUp(frame: PoseFrame): Boolean {
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)
        val hipAngle = featureExtractor.hipAngleDegrees(frame)
        if (kneeAngle != null && kneeAngle < 140f) return false
        if (hipAngle != null && hipAngle < 130f) return false
        return true
    }

    private fun blend(current: Float, target: Float, weight: Float): Float {
        return (current * (1f - weight)) + (target * weight)
    }
}

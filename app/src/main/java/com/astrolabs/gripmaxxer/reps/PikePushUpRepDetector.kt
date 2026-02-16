package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class PikePushUpRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 190L,
        minRepIntervalMs = 520L,
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
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        if (!active || !isPikePosture(frame, shoulderY)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        if (baselineShoulderY == null) baselineShoulderY = shoulderY
        if (elbowAngle > 155f) {
            baselineShoulderY = blend(baselineShoulderY ?: shoulderY, shoulderY, 0.1f)
        }
        val shoulderDrop = shoulderY - (baselineShoulderY ?: shoulderY)

        val isDown = elbowAngle < 95f && shoulderDrop > MIN_SHOULDER_DROP_FOR_DOWN
        val isUp = elbowAngle > 160f && shoulderDrop < MAX_SHOULDER_DROP_FOR_UP
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    private fun isPikePosture(frame: PoseFrame, shoulderY: Float): Boolean {
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return false
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)

        val hipsAboveShoulders = shoulderY - hipY >= HIPS_ABOVE_SHOULDERS_MIN_DELTA
        val legsMostlyStraight = kneeAngle == null || kneeAngle > MIN_KNEE_ANGLE
        return hipsAboveShoulders && legsMostlyStraight
    }

    private fun blend(current: Float, target: Float, weight: Float): Float {
        return (current * (1f - weight)) + (target * weight)
    }

    companion object {
        private const val HIPS_ABOVE_SHOULDERS_MIN_DELTA = 0.05f
        private const val MIN_KNEE_ANGLE = 140f
        private const val MIN_SHOULDER_DROP_FOR_DOWN = 0.012f
        private const val MAX_SHOULDER_DROP_FOR_UP = 0.010f
    }
}

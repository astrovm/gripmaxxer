package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class PikePushUpRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 210L,
        minRepIntervalMs = 560L,
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
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }
        val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
        if (!active || !isPikePosture(frame, shoulderY)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        if (baselineShoulderY == null) baselineShoulderY = shoulderY
        if (elbowAngle > BASELINE_RESET_ELBOW_MIN) {
            baselineShoulderY = blend(baselineShoulderY ?: shoulderY, shoulderY, 0.1f)
        }
        val shoulderDrop = shoulderY - (baselineShoulderY ?: shoulderY)

        val isDown = elbowAngle < DOWN_ELBOW_MAX && shoulderDrop > MIN_SHOULDER_DROP_FOR_DOWN
        val isUp = elbowAngle > UP_ELBOW_MIN && shoulderDrop < MAX_SHOULDER_DROP_FOR_UP
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    private fun isPikePosture(frame: PoseFrame, shoulderY: Float): Boolean {
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return false
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)
        val hipAngle = featureExtractor.hipAngleDegrees(frame)

        val hipsAboveShoulders = shoulderY - hipY >= HIPS_ABOVE_SHOULDERS_MIN_DELTA
        val legsMostlyStraight = kneeAngle == null || kneeAngle > MIN_KNEE_ANGLE
        val foldedAtHips = hipAngle == null || hipAngle <= MAX_HIP_ANGLE
        return hipsAboveShoulders && legsMostlyStraight && foldedAtHips
    }

    private fun blend(current: Float, target: Float, weight: Float): Float {
        return (current * (1f - weight)) + (target * weight)
    }

    companion object {
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val HIPS_ABOVE_SHOULDERS_MIN_DELTA = 0.05f
        private const val MIN_KNEE_ANGLE = 142f
        private const val MAX_HIP_ANGLE = 132f
        private const val BASELINE_RESET_ELBOW_MIN = 156f
        private const val DOWN_ELBOW_MAX = 98f
        private const val UP_ELBOW_MIN = 161f
        private const val MIN_SHOULDER_DROP_FOR_DOWN = 0.013f
        private const val MAX_SHOULDER_DROP_FOR_UP = 0.009f
    }
}

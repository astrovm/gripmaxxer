package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class HipThrustRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 230L,
        minRepIntervalMs = 600L,
    )
    private var baselineHipY: Float? = null

    override fun reset() {
        cycleCounter.reset()
        baselineHipY = null
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }
        val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)
        if (kneeAngle != null && (kneeAngle < MIN_KNEE_ANGLE || kneeAngle > MAX_KNEE_ANGLE)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }
        if (abs(hipY - shoulderY) > MAX_HIP_TO_SHOULDER_Y_DELTA) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val hipAngle = featureExtractor.hipAngleDegrees(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        if (baselineHipY == null) baselineHipY = hipY
        if (hipAngle > BASELINE_RESET_HIP_ANGLE_MIN) {
            baselineHipY = blend(current = baselineHipY ?: hipY, target = hipY, weight = BASELINE_BLEND_WEIGHT)
        }
        val hipDrop = hipY - (baselineHipY ?: hipY)

        val isDown = hipAngle < DOWN_HIP_ANGLE_MAX && hipDrop > MIN_HIP_DROP_FOR_DOWN
        val isUp = hipAngle > UP_HIP_ANGLE_MIN && hipDrop < MAX_HIP_DROP_FOR_UP
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    private fun blend(current: Float, target: Float, weight: Float): Float {
        return (current * (1f - weight)) + (target * weight)
    }

    companion object {
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val MIN_KNEE_ANGLE = 70f
        private const val MAX_KNEE_ANGLE = 178f
        private const val MAX_HIP_TO_SHOULDER_Y_DELTA = 0.32f
        private const val BASELINE_RESET_HIP_ANGLE_MIN = 160f
        private const val BASELINE_BLEND_WEIGHT = 0.08f
        private const val DOWN_HIP_ANGLE_MAX = 130f
        private const val UP_HIP_ANGLE_MIN = 161f
        private const val MIN_HIP_DROP_FOR_DOWN = 0.018f
        private const val MAX_HIP_DROP_FOR_UP = 0.012f
    }
}

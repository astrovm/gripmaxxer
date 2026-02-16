package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class PushUpRepDetector(
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
        if (elbowAngle > BASELINE_RESET_ELBOW_MIN) {
            baselineShoulderY = blend(
                current = baselineShoulderY ?: shoulderY,
                target = shoulderY,
                weight = BASELINE_BLEND_WEIGHT,
            )
        }

        val baseline = baselineShoulderY ?: shoulderY
        val shoulderDrop = shoulderY - baseline
        val isDown = elbowAngle < DOWN_ELBOW_MAX && shoulderDrop > MIN_SHOULDER_DROP_FOR_DOWN
        val isUp = elbowAngle > UP_ELBOW_MIN && shoulderDrop < MAX_SHOULDER_DROP_FOR_UP
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    private fun hasRequiredTorso(frame: PoseFrame): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        if (shoulderY == null || hipY == null) return false

        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) return false
        return abs(shoulderY - hipY) <= BODY_FLAT_MAX_DELTA
    }

    private fun isLikelyStandardPushUp(frame: PoseFrame): Boolean {
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)
        val hipAngle = featureExtractor.hipAngleDegrees(frame)
        if (kneeAngle != null && kneeAngle < MIN_KNEE_ANGLE) return false
        if (hipAngle != null && hipAngle < MIN_HIP_ANGLE) return false
        return true
    }

    private fun blend(current: Float, target: Float, weight: Float): Float {
        return (current * (1f - weight)) + (target * weight)
    }

    companion object {
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val BODY_FLAT_MAX_DELTA = 0.18f
        private const val MIN_KNEE_ANGLE = 138f
        private const val MIN_HIP_ANGLE = 132f
        private const val BASELINE_RESET_ELBOW_MIN = 156f
        private const val BASELINE_BLEND_WEIGHT = 0.09f
        private const val DOWN_ELBOW_MAX = 100f
        private const val UP_ELBOW_MIN = 160f
        private const val MIN_SHOULDER_DROP_FOR_DOWN = 0.013f
        private const val MAX_SHOULDER_DROP_FOR_UP = 0.010f
    }
}

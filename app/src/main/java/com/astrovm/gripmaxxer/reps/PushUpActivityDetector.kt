package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class PushUpActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private var active = false
    private var lastMotionMs = 0L
    private var baselineShoulderY: Float? = null

    override fun reset() {
        active = false
        lastMotionMs = 0L
        baselineShoulderY = null
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            ?: return decayActive(nowMs)
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            ?: return decayActive(nowMs)
        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
            ?: return decayActive(nowMs)

        if (!isLikelyPushUpPosture(frame, shoulderY, hipY)) {
            return decayActive(nowMs)
        }

        if (baselineShoulderY == null) baselineShoulderY = shoulderY
        if (elbowAngle > BASELINE_RESET_ELBOW_MIN) {
            baselineShoulderY = blend(
                current = baselineShoulderY ?: shoulderY,
                target = shoulderY,
                weight = BASELINE_BLEND_WEIGHT,
            )
        }
        val shoulderDrop = shoulderY - (baselineShoulderY ?: shoulderY)
        val motionDetected = elbowAngle < ACTIVE_ELBOW_MAX || shoulderDrop > MIN_SHOULDER_DROP_FOR_ACTIVE
        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }

        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    private fun isLikelyPushUpPosture(
        frame: PoseFrame,
        shoulderY: Float,
        hipY: Float,
    ): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) return false

        val bodyFlat = abs(shoulderY - hipY) <= BODY_FLAT_MAX_DELTA
        if (!bodyFlat) return false

        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)
        if (kneeAngle != null && kneeAngle < MIN_KNEE_ANGLE) return false

        val hipAngle = featureExtractor.hipAngleDegrees(frame)
        if (hipAngle != null && hipAngle < MIN_HIP_ANGLE) return false

        return true
    }

    private fun decayActive(nowMs: Long): Boolean {
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    private fun blend(current: Float, target: Float, weight: Float): Float {
        return (current * (1f - weight)) + (target * weight)
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1400L
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val BODY_FLAT_MAX_DELTA = 0.18f
        private const val MIN_KNEE_ANGLE = 138f
        private const val MIN_HIP_ANGLE = 132f
        private const val BASELINE_RESET_ELBOW_MIN = 157f
        private const val BASELINE_BLEND_WEIGHT = 0.09f
        private const val ACTIVE_ELBOW_MAX = 158f
        private const val MIN_SHOULDER_DROP_FOR_ACTIVE = 0.011f
    }
}

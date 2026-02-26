package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class HipThrustActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private var active = false
    private var lastMotionMs = 0L
    private var baselineHipY: Float? = null
    private var lastHipAngle: Float? = null

    override fun reset() {
        active = false
        lastMotionMs = 0L
        baselineHipY = null
        lastHipAngle = null
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return decayActive(nowMs)
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return decayActive(nowMs)
        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) return decayActive(nowMs)
        val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipAngle = featureExtractor.hipAngleDegrees(frame) ?: return decayActive(nowMs)
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return decayActive(nowMs)
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)

        if (kneeAngle != null && (kneeAngle < MIN_KNEE_ANGLE || kneeAngle > MAX_KNEE_ANGLE)) {
            return decayActive(nowMs)
        }
        if (abs(hipY - shoulderY) > MAX_HIP_TO_SHOULDER_Y_DELTA) {
            return decayActive(nowMs)
        }

        if (baselineHipY == null) baselineHipY = hipY
        if (hipAngle > BASELINE_RESET_HIP_ANGLE_MIN) {
            baselineHipY = blend(baselineHipY ?: hipY, hipY, 0.08f)
        }

        val hipDrop = hipY - (baselineHipY ?: hipY)
        val angleMotion = lastHipAngle?.let { abs(it - hipAngle) > HIP_ANGLE_MOTION_MIN_DELTA } ?: false
        val motionDetected = angleMotion || hipAngle < ACTIVE_HIP_ANGLE_MAX || hipDrop > ACTIVE_HIP_DROP_MIN
        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }
        lastHipAngle = hipAngle

        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
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
        private const val IDLE_TIMEOUT_MS = 1700L
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val MIN_KNEE_ANGLE = 70f
        private const val MAX_KNEE_ANGLE = 178f
        private const val MAX_HIP_TO_SHOULDER_Y_DELTA = 0.32f
        private const val BASELINE_RESET_HIP_ANGLE_MIN = 160f
        private const val HIP_ANGLE_MOTION_MIN_DELTA = 1.6f
        private const val ACTIVE_HIP_ANGLE_MAX = 164f
        private const val ACTIVE_HIP_DROP_MIN = 0.016f
    }
}

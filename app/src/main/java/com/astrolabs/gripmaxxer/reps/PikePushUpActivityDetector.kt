package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class PikePushUpActivityDetector(
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
        if (!isPikePosture(frame, shoulderY)) return decayActive(nowMs)

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame) ?: return decayActive(nowMs)
        if (baselineShoulderY == null) baselineShoulderY = shoulderY
        if (elbowAngle > 150f) {
            baselineShoulderY = blend(baselineShoulderY ?: shoulderY, shoulderY, 0.1f)
        }
        val shoulderDrop = shoulderY - (baselineShoulderY ?: shoulderY)

        val motionDetected = elbowAngle < 162f || shoulderDrop > MIN_SHOULDER_DROP_FOR_ACTIVE
        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    private fun isPikePosture(frame: PoseFrame, shoulderY: Float): Boolean {
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return false
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame)

        val hipsAboveShoulders = shoulderY - hipY >= HIPS_ABOVE_SHOULDERS_MIN_DELTA
        val legsMostlyStraight = kneeAngle == null || kneeAngle > MIN_KNEE_ANGLE
        return hipsAboveShoulders && legsMostlyStraight
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
        private const val HIPS_ABOVE_SHOULDERS_MIN_DELTA = 0.05f
        private const val MIN_KNEE_ANGLE = 140f
        private const val MIN_SHOULDER_DROP_FOR_ACTIVE = 0.009f
    }
}

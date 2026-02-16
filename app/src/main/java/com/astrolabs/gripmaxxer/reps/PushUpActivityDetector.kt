package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

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
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
        if (shoulderY == null || hipY == null || elbowAngle == null) {
            return decayActive(nowMs)
        }

        if (baselineShoulderY == null) baselineShoulderY = shoulderY
        if (elbowAngle > 155f) {
            baselineShoulderY = blend(baselineShoulderY ?: shoulderY, shoulderY, 0.1f)
        }
        val shoulderDrop = shoulderY - (baselineShoulderY ?: shoulderY)
        val motionDetected = elbowAngle < 155f || shoulderDrop > 0.012f
        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }

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
        private const val IDLE_TIMEOUT_MS = 1400L
    }
}

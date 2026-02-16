package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
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
        val hipAngle = featureExtractor.hipAngleDegrees(frame) ?: return decayActive(nowMs)
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return decayActive(nowMs)

        if (baselineHipY == null) baselineHipY = hipY
        if (hipAngle > 158f) {
            baselineHipY = blend(baselineHipY ?: hipY, hipY, 0.08f)
        }

        val hipDrop = hipY - (baselineHipY ?: hipY)
        val angleMotion = lastHipAngle?.let { abs(it - hipAngle) > 1.4f } ?: false
        val motionDetected = angleMotion || hipAngle < 165f || hipDrop > 0.015f
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
    }
}

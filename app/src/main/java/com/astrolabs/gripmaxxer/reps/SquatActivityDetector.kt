package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class SquatActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private var active = false
    private var lastMotionMs = 0L
    private var baselineHipY: Float? = null
    private var lastKneeAngle: Float? = null

    override fun reset() {
        active = false
        lastMotionMs = 0L
        baselineHipY = null
        lastKneeAngle = null
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame) ?: return decayActive(nowMs)
        val hipAngle = featureExtractor.hipAngleDegrees(frame)
        val hipY = frame.averageY(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return decayActive(nowMs)

        if (baselineHipY == null) baselineHipY = hipY
        if (kneeAngle > BASELINE_RESET_KNEE_MIN) {
            baselineHipY = blend(baselineHipY ?: hipY, hipY, 0.06f)
        }

        val hipDrop = hipY - (baselineHipY ?: hipY)
        val angleMotion = lastKneeAngle?.let { abs(it - kneeAngle) > KNEE_MOTION_MIN_DELTA } ?: false
        val motionDetected = angleMotion ||
            kneeAngle < ACTIVE_KNEE_MAX ||
            hipDrop > ACTIVE_HIP_DROP_MIN ||
            (hipAngle != null && hipAngle < ACTIVE_HIP_ANGLE_MAX)
        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }
        lastKneeAngle = kneeAngle

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
        private const val IDLE_TIMEOUT_MS = 1500L
        private const val BASELINE_RESET_KNEE_MIN = 162f
        private const val KNEE_MOTION_MIN_DELTA = 1.6f
        private const val ACTIVE_KNEE_MAX = 163f
        private const val ACTIVE_HIP_ANGLE_MAX = 152f
        private const val ACTIVE_HIP_DROP_MIN = 0.022f
    }
}

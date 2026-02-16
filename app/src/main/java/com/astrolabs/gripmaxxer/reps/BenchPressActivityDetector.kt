package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import kotlin.math.abs

class BenchPressActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private var active = false
    private var lastMotionMs = 0L
    private var lastElbowAngle: Float? = null

    override fun reset() {
        active = false
        lastMotionMs = 0L
        lastElbowAngle = null
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val elbowAngle = featureExtractor.elbowAngleDegrees(frame) ?: return decayActive(nowMs)
        val elbowMotion = lastElbowAngle?.let { abs(it - elbowAngle) > 1.3f } ?: false
        val motionDetected = elbowMotion || elbowAngle < 165f
        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }
        lastElbowAngle = elbowAngle

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

    companion object {
        private const val IDLE_TIMEOUT_MS = 1300L
    }
}

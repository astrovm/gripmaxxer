package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class DipActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private var active = false
    private var lastMotionMs = 0L

    override fun reset() {
        active = false
        lastMotionMs = 0L
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        if (!hasSupportPosture(frame)) {
            return decayActive(nowMs)
        }

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame) ?: return decayActive(nowMs)
        val motionDetected = elbowAngle < 165f
        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    private fun hasSupportPosture(frame: PoseFrame): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
        val leftWrist = frame.landmark(PoseLandmark.LEFT_WRIST) ?: return false
        val rightWrist = frame.landmark(PoseLandmark.RIGHT_WRIST) ?: return false

        val wristsBelowShoulders = leftWrist.y > leftShoulder.y + 0.02f &&
            rightWrist.y > rightShoulder.y + 0.02f
        val wristsNearShoulders = kotlin.math.abs(leftWrist.x - leftShoulder.x) < 0.22f &&
            kotlin.math.abs(rightWrist.x - rightShoulder.x) < 0.22f
        return wristsBelowShoulders && wristsNearShoulders
    }

    private fun decayActive(nowMs: Long): Boolean {
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1400L
    }
}

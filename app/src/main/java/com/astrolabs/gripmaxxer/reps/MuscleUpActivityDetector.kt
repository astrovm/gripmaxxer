package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.hang.HangDetectionConfig
import com.astrolabs.gripmaxxer.hang.HangDetector
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class MuscleUpActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private val hangDetector = HangDetector()
    private var active = false
    private var lastMotionMs = 0L

    fun updateConfig(config: HangDetectionConfig) {
        hangDetector.updateConfig(config)
    }

    override fun reset() {
        hangDetector.reset()
        active = false
        lastMotionMs = 0L
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val hanging = hangDetector.process(frame = frame, nowMs = nowMs).isHanging
        val support = hasSupportPosture(frame)
        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
        val transitionMotion = elbowAngle != null && elbowAngle < TRANSITION_MAX_ELBOW_ANGLE

        if (hanging || support || transitionMotion) {
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

        val wristsBelowShoulders = leftWrist.y > leftShoulder.y + 0.01f &&
            rightWrist.y > rightShoulder.y + 0.01f
        val wristsNearShoulders = kotlin.math.abs(leftWrist.x - leftShoulder.x) < 0.28f &&
            kotlin.math.abs(rightWrist.x - rightShoulder.x) < 0.28f
        return wristsBelowShoulders && wristsNearShoulders
    }

    companion object {
        private const val TRANSITION_MAX_ELBOW_ANGLE = 145f
        private const val IDLE_TIMEOUT_MS = 1800L
    }
}

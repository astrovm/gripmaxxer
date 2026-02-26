package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.hang.HangDetectionConfig
import com.astrovm.gripmaxxer.hang.HangDetector
import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class MuscleUpActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private val hangDetector = HangDetector()
    private var active = false
    private var lastMotionMs = 0L
    private var lastStructuredPoseMs = 0L

    fun updateConfig(config: HangDetectionConfig) {
        hangDetector.updateConfig(config)
    }

    override fun reset() {
        hangDetector.reset()
        active = false
        lastMotionMs = 0L
        lastStructuredPoseMs = 0L
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val hanging = hangDetector.process(frame = frame, nowMs = nowMs).isHanging
        val support = hasSupportPosture(frame)
        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        val wristY = frame.averageY(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
        val transitionMotion = elbowAngle != null &&
            elbowAngle < TRANSITION_MAX_ELBOW_ANGLE &&
            shoulderY != null &&
            wristY != null &&
            wristY < shoulderY + TRANSITION_WRIST_BELOW_SHOULDER_MAX_DELTA

        if (hanging || support) {
            lastStructuredPoseMs = nowMs
        }
        val transitionWithContext = transitionMotion &&
            (nowMs - lastStructuredPoseMs) <= TRANSITION_CONTEXT_MS

        if (hanging || support || transitionWithContext) {
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

        val shoulderWidth = kotlin.math.abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) return false

        val wristsBelowShoulders = leftWrist.y > leftShoulder.y + WRIST_BELOW_SHOULDER_MIN_DELTA &&
            rightWrist.y > rightShoulder.y + WRIST_BELOW_SHOULDER_MIN_DELTA
        val wristsNearShoulders = kotlin.math.abs(leftWrist.x - leftShoulder.x) < shoulderWidth * WRIST_TO_SHOULDER_X_MAX_RATIO &&
            kotlin.math.abs(rightWrist.x - rightShoulder.x) < shoulderWidth * WRIST_TO_SHOULDER_X_MAX_RATIO
        return wristsBelowShoulders && wristsNearShoulders
    }

    companion object {
        private const val TRANSITION_MAX_ELBOW_ANGLE = 145f
        private const val IDLE_TIMEOUT_MS = 1800L
        private const val TRANSITION_CONTEXT_MS = 1200L
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val WRIST_BELOW_SHOULDER_MIN_DELTA = 0.01f
        private const val WRIST_TO_SHOULDER_X_MAX_RATIO = 3.1f
        private const val TRANSITION_WRIST_BELOW_SHOULDER_MAX_DELTA = 0.05f
    }
}

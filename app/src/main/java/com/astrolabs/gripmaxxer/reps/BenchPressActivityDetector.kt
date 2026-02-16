package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
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
        if (!hasBenchPressPosture(frame)) {
            return decayActive(nowMs)
        }

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame) ?: return decayActive(nowMs)
        val elbowMotion = lastElbowAngle?.let { abs(it - elbowAngle) > 1.3f } ?: false
        val motionDetected = elbowMotion || elbowAngle < ACTIVE_ELBOW_MAX
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

    private fun hasBenchPressPosture(frame: PoseFrame): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
        val leftElbow = frame.landmark(PoseLandmark.LEFT_ELBOW) ?: return false
        val rightElbow = frame.landmark(PoseLandmark.RIGHT_ELBOW) ?: return false
        val leftWrist = frame.landmark(PoseLandmark.LEFT_WRIST) ?: return false
        val rightWrist = frame.landmark(PoseLandmark.RIGHT_WRIST) ?: return false

        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) return false

        val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
        val elbowY = (leftElbow.y + rightElbow.y) / 2f
        val wristY = (leftWrist.y + rightWrist.y) / 2f

        val wristNearShoulderHeight = abs(wristY - shoulderY) <= MAX_WRIST_TO_SHOULDER_Y_DELTA
        val elbowNearShoulderHeight = abs(elbowY - shoulderY) <= MAX_ELBOW_TO_SHOULDER_Y_DELTA
        val wristsNearShouldersX = abs(leftWrist.x - leftShoulder.x) < MAX_WRIST_TO_SHOULDER_X_DELTA &&
            abs(rightWrist.x - rightShoulder.x) < MAX_WRIST_TO_SHOULDER_X_DELTA
        val wristsNearElbowsX = abs(leftWrist.x - leftElbow.x) < MAX_WRIST_TO_ELBOW_X_DELTA &&
            abs(rightWrist.x - rightElbow.x) < MAX_WRIST_TO_ELBOW_X_DELTA
        return wristNearShoulderHeight && elbowNearShoulderHeight && wristsNearShouldersX && wristsNearElbowsX
    }

    private fun decayActive(nowMs: Long): Boolean {
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1300L
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val ACTIVE_ELBOW_MAX = 164f
        private const val MAX_WRIST_TO_SHOULDER_Y_DELTA = 0.22f
        private const val MAX_ELBOW_TO_SHOULDER_Y_DELTA = 0.20f
        private const val MAX_WRIST_TO_SHOULDER_X_DELTA = 0.32f
        private const val MAX_WRIST_TO_ELBOW_X_DELTA = 0.22f
    }
}

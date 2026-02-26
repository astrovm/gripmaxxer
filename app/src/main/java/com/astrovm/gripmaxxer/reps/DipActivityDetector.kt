package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

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
        val motionDetected = elbowAngle < ACTIVE_ELBOW_MAX
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
        val leftElbow = frame.landmark(PoseLandmark.LEFT_ELBOW) ?: return false
        val rightElbow = frame.landmark(PoseLandmark.RIGHT_ELBOW) ?: return false
        val leftWrist = frame.landmark(PoseLandmark.LEFT_WRIST) ?: return false
        val rightWrist = frame.landmark(PoseLandmark.RIGHT_WRIST) ?: return false

        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) return false

        val wristsBelowShoulders = leftWrist.y > leftShoulder.y + WRIST_BELOW_SHOULDER_MIN_DELTA &&
            rightWrist.y > rightShoulder.y + WRIST_BELOW_SHOULDER_MIN_DELTA
        val elbowsBelowShoulders = leftElbow.y > leftShoulder.y + ELBOW_BELOW_SHOULDER_MIN_DELTA &&
            rightElbow.y > rightShoulder.y + ELBOW_BELOW_SHOULDER_MIN_DELTA
        val wristsNearShoulders = abs(leftWrist.x - leftShoulder.x) < shoulderWidth * WRIST_TO_SHOULDER_X_MAX_RATIO &&
            abs(rightWrist.x - rightShoulder.x) < shoulderWidth * WRIST_TO_SHOULDER_X_MAX_RATIO
        return wristsBelowShoulders && elbowsBelowShoulders && wristsNearShoulders
    }

    private fun decayActive(nowMs: Long): Boolean {
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1400L
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val ACTIVE_ELBOW_MAX = 167f
        private const val WRIST_BELOW_SHOULDER_MIN_DELTA = 0.015f
        private const val ELBOW_BELOW_SHOULDER_MIN_DELTA = 0.006f
        private const val WRIST_TO_SHOULDER_X_MAX_RATIO = 2.5f
    }
}

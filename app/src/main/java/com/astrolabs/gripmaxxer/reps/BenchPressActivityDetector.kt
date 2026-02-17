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
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER)

        if (leftShoulder != null && rightShoulder != null) {
            val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
            if (shoulderWidth < MIN_SHOULDER_WIDTH) return false
        }

        val leftValid = isBenchSideValid(
            shoulder = leftShoulder,
            elbow = frame.landmark(PoseLandmark.LEFT_ELBOW),
            wrist = frame.landmark(PoseLandmark.LEFT_WRIST),
        )
        val rightValid = isBenchSideValid(
            shoulder = rightShoulder,
            elbow = frame.landmark(PoseLandmark.RIGHT_ELBOW),
            wrist = frame.landmark(PoseLandmark.RIGHT_WRIST),
        )
        return leftValid || rightValid
    }

    private fun isBenchSideValid(
        shoulder: com.astrolabs.gripmaxxer.pose.NormalizedLandmark?,
        elbow: com.astrolabs.gripmaxxer.pose.NormalizedLandmark?,
        wrist: com.astrolabs.gripmaxxer.pose.NormalizedLandmark?,
    ): Boolean {
        if (shoulder == null || elbow == null || wrist == null) return false

        val wristNearShoulderHeight = abs(wrist.y - shoulder.y) <= MAX_WRIST_TO_SHOULDER_Y_DELTA
        val elbowNearShoulderHeight = abs(elbow.y - shoulder.y) <= MAX_ELBOW_TO_SHOULDER_Y_DELTA
        val wristNearShoulderX = abs(wrist.x - shoulder.x) <= MAX_WRIST_TO_SHOULDER_X_DELTA
        val wristNearElbowX = abs(wrist.x - elbow.x) <= MAX_WRIST_TO_ELBOW_X_DELTA
        return wristNearShoulderHeight && elbowNearShoulderHeight && wristNearShoulderX && wristNearElbowX
    }

    private fun decayActive(nowMs: Long): Boolean {
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1500L
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val ACTIVE_ELBOW_MAX = 168f
        private const val MAX_WRIST_TO_SHOULDER_Y_DELTA = 0.30f
        private const val MAX_ELBOW_TO_SHOULDER_Y_DELTA = 0.26f
        private const val MAX_WRIST_TO_SHOULDER_X_DELTA = 0.42f
        private const val MAX_WRIST_TO_ELBOW_X_DELTA = 0.30f
    }
}

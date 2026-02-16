package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class BenchPressRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 190L,
        minRepIntervalMs = 500L,
    )

    override fun reset() {
        cycleCounter.reset()
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active || !hasBenchPressPosture(frame)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val isDown = elbowAngle < DOWN_ELBOW_MAX
        val isUp = elbowAngle > UP_ELBOW_MIN
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
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

    companion object {
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val DOWN_ELBOW_MAX = 98f
        private const val UP_ELBOW_MIN = 161f
        private const val MAX_WRIST_TO_SHOULDER_Y_DELTA = 0.22f
        private const val MAX_ELBOW_TO_SHOULDER_Y_DELTA = 0.20f
        private const val MAX_WRIST_TO_SHOULDER_X_DELTA = 0.32f
        private const val MAX_WRIST_TO_ELBOW_X_DELTA = 0.22f
    }
}

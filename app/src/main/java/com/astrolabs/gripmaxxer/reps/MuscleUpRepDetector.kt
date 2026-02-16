package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class MuscleUpRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 220L,
        minRepIntervalMs = 750L,
    )

    override fun reset() {
        cycleCounter.reset()
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val wristY = frame.averageY(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)

        val isDown = elbowAngle > 155f && wristY < shoulderY - WRIST_OVER_SHOULDER_MIN_DELTA
        val isUp = elbowAngle < 110f &&
            wristY > shoulderY + WRIST_UNDER_SHOULDER_MIN_DELTA &&
            wristsNearShoulders(frame)

        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    private fun wristsNearShoulders(frame: PoseFrame): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
        val leftWrist = frame.landmark(PoseLandmark.LEFT_WRIST) ?: return false
        val rightWrist = frame.landmark(PoseLandmark.RIGHT_WRIST) ?: return false
        return kotlin.math.abs(leftWrist.x - leftShoulder.x) < 0.28f &&
            kotlin.math.abs(rightWrist.x - rightShoulder.x) < 0.28f
    }

    companion object {
        private const val WRIST_OVER_SHOULDER_MIN_DELTA = 0.02f
        private const val WRIST_UNDER_SHOULDER_MIN_DELTA = 0.01f
    }
}

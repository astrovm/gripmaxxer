package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class DipRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 190L,
        minRepIntervalMs = 520L,
    )

    override fun reset() {
        cycleCounter.reset()
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active || !hasSupportPosture(frame)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val isDown = elbowAngle < DOWN_ELBOW_MAX
        val isUp = elbowAngle > UP_ELBOW_MIN
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
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

    companion object {
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val DOWN_ELBOW_MAX = 100f
        private const val UP_ELBOW_MIN = 158f
        private const val WRIST_BELOW_SHOULDER_MIN_DELTA = 0.015f
        private const val ELBOW_BELOW_SHOULDER_MIN_DELTA = 0.006f
        private const val WRIST_TO_SHOULDER_X_MAX_RATIO = 2.5f
    }
}

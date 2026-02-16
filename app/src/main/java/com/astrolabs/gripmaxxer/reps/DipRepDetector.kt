package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class DipRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 170L,
        minRepIntervalMs = 450L,
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
        val isDown = elbowAngle < 95f
        val isUp = elbowAngle > 155f
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
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
}

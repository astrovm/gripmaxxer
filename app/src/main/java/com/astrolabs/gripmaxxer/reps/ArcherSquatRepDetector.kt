package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class ArcherSquatRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 230L,
        minRepIntervalMs = 620L,
    )

    override fun reset() {
        cycleCounter.reset()
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active || !hasWideStance(frame)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val leftKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.LEFT)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val rightKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.RIGHT)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)

        val downLeft = isSideDown(leftKnee, rightKnee)
        val downRight = isSideDown(rightKnee, leftKnee)
        val isDown = downLeft || downRight
        val isUp = leftKnee > UP_KNEE_MIN &&
            rightKnee > UP_KNEE_MIN &&
            abs(leftKnee - rightKnee) < UP_KNEE_BALANCE_MAX_DELTA

        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    private fun hasWideStance(frame: PoseFrame): Boolean {
        val leftAnkle = frame.landmark(PoseLandmark.LEFT_ANKLE) ?: return false
        val rightAnkle = frame.landmark(PoseLandmark.RIGHT_ANKLE) ?: return false
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER) ?: return false

        val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
        if (shoulderWidth < MIN_SHOULDER_WIDTH) return false
        val ankleSpan = abs(leftAnkle.x - rightAnkle.x)
        return ankleSpan >= shoulderWidth * MIN_ANKLE_TO_SHOULDER_WIDTH_RATIO
    }

    private fun isSideDown(workingKnee: Float, supportKnee: Float): Boolean {
        return workingKnee < DOWN_WORKING_KNEE_MAX &&
            supportKnee > DOWN_SUPPORT_KNEE_MIN &&
            (supportKnee - workingKnee) > MIN_KNEE_SEPARATION_DEG
    }

    companion object {
        private const val MIN_SHOULDER_WIDTH = 0.08f
        private const val MIN_ANKLE_TO_SHOULDER_WIDTH_RATIO = 1.35f
        private const val DOWN_WORKING_KNEE_MAX = 112f
        private const val DOWN_SUPPORT_KNEE_MIN = 150f
        private const val MIN_KNEE_SEPARATION_DEG = 28f
        private const val UP_KNEE_MIN = 160f
        private const val UP_KNEE_BALANCE_MAX_DELTA = 16f
    }
}

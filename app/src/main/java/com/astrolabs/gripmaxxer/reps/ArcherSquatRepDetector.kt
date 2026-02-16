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
        stableMs = 240L,
        minRepIntervalMs = 650L,
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
        val hipShift = lateralHipShiftRatio(frame)
        val isDown = (downLeft || downRight) && hipShift >= DOWN_HIP_SHIFT_MIN_RATIO
        val isUp = leftKnee > UP_KNEE_MIN &&
            rightKnee > UP_KNEE_MIN &&
            abs(leftKnee - rightKnee) < UP_KNEE_BALANCE_MAX_DELTA &&
            hipShift <= UP_HIP_SHIFT_MAX_RATIO

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

    private fun lateralHipShiftRatio(frame: PoseFrame): Float {
        val leftHip = frame.landmark(PoseLandmark.LEFT_HIP) ?: return 0f
        val rightHip = frame.landmark(PoseLandmark.RIGHT_HIP) ?: return 0f
        val leftAnkle = frame.landmark(PoseLandmark.LEFT_ANKLE) ?: return 0f
        val rightAnkle = frame.landmark(PoseLandmark.RIGHT_ANKLE) ?: return 0f

        val hipCenter = (leftHip.x + rightHip.x) / 2f
        val ankleCenter = (leftAnkle.x + rightAnkle.x) / 2f
        val ankleSpan = abs(leftAnkle.x - rightAnkle.x)
        if (ankleSpan < 0.12f) return 0f
        return abs(hipCenter - ankleCenter) / (ankleSpan / 2f)
    }

    companion object {
        private const val MIN_SHOULDER_WIDTH = 0.08f
        private const val MIN_ANKLE_TO_SHOULDER_WIDTH_RATIO = 1.4f
        private const val DOWN_WORKING_KNEE_MAX = 110f
        private const val DOWN_SUPPORT_KNEE_MIN = 152f
        private const val MIN_KNEE_SEPARATION_DEG = 30f
        private const val UP_KNEE_MIN = 162f
        private const val UP_KNEE_BALANCE_MAX_DELTA = 14f
        private const val DOWN_HIP_SHIFT_MIN_RATIO = 0.15f
        private const val UP_HIP_SHIFT_MAX_RATIO = 0.10f
    }
}

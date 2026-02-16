package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class ArcherSquatActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private var active = false
    private var lastMotionMs = 0L
    private var lastLeftKneeAngle: Float? = null
    private var lastRightKneeAngle: Float? = null

    override fun reset() {
        active = false
        lastMotionMs = 0L
        lastLeftKneeAngle = null
        lastRightKneeAngle = null
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        if (!hasWideStance(frame)) return decayActive(nowMs)

        val leftKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.LEFT)
        val rightKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.RIGHT)
        if (leftKnee == null || rightKnee == null) return decayActive(nowMs)

        val leftMotion = lastLeftKneeAngle?.let { abs(it - leftKnee) > 1.4f } ?: false
        val rightMotion = lastRightKneeAngle?.let { abs(it - rightKnee) > 1.4f } ?: false
        val sideDepth = (isSideDown(leftKnee, rightKnee) || isSideDown(rightKnee, leftKnee)) &&
            hasLateralHipShift(frame, MIN_ACTIVE_HIP_SHIFT_RATIO)
        val motionDetected = leftMotion || rightMotion || sideDepth

        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }

        lastLeftKneeAngle = leftKnee
        lastRightKneeAngle = rightKnee

        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
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

    private fun hasLateralHipShift(frame: PoseFrame, minShiftRatio: Float): Boolean {
        val leftHip = frame.landmark(PoseLandmark.LEFT_HIP) ?: return false
        val rightHip = frame.landmark(PoseLandmark.RIGHT_HIP) ?: return false
        val leftAnkle = frame.landmark(PoseLandmark.LEFT_ANKLE) ?: return false
        val rightAnkle = frame.landmark(PoseLandmark.RIGHT_ANKLE) ?: return false

        val hipCenter = (leftHip.x + rightHip.x) / 2f
        val ankleCenter = (leftAnkle.x + rightAnkle.x) / 2f
        val ankleSpan = abs(leftAnkle.x - rightAnkle.x)
        if (ankleSpan < 0.12f) return false

        val normalizedShift = abs(hipCenter - ankleCenter) / (ankleSpan / 2f)
        return normalizedShift >= minShiftRatio
    }

    private fun decayActive(nowMs: Long): Boolean {
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1600L
        private const val MIN_SHOULDER_WIDTH = 0.08f
        private const val MIN_ANKLE_TO_SHOULDER_WIDTH_RATIO = 1.35f
        private const val DOWN_WORKING_KNEE_MAX = 118f
        private const val DOWN_SUPPORT_KNEE_MIN = 148f
        private const val MIN_KNEE_SEPARATION_DEG = 24f
        private const val MIN_ACTIVE_HIP_SHIFT_RATIO = 0.12f
    }
}

package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import kotlin.math.abs

class LungeActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private var active = false
    private var lastMotionMs = 0L
    private var trackedSide: BodySide? = null
    private var lastWorkingKneeAngle: Float? = null

    override fun reset() {
        active = false
        lastMotionMs = 0L
        trackedSide = null
        lastWorkingKneeAngle = null
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val side = selectTrackedSide(frame) ?: return decayActive(nowMs)
        val workingKnee = featureExtractor.kneeAngleDegrees(frame, side) ?: return decayActive(nowMs)
        val rearKnee = featureExtractor.kneeAngleDegrees(frame, opposite(side))

        val angleMotion = lastWorkingKneeAngle?.let { abs(it - workingKnee) > 1.5f } ?: false
        val motionDetected = angleMotion || workingKnee < 165f || (rearKnee != null && rearKnee < 165f)
        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }
        lastWorkingKneeAngle = workingKnee

        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    private fun selectTrackedSide(frame: PoseFrame): BodySide? {
        val leftKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.LEFT)
        val rightKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.RIGHT)
        val selected = when {
            leftKnee != null && rightKnee != null -> {
                if (leftKnee <= rightKnee - SIDE_SWITCH_DELTA_DEG) BodySide.LEFT
                else if (rightKnee <= leftKnee - SIDE_SWITCH_DELTA_DEG) BodySide.RIGHT
                else trackedSide ?: if (leftKnee <= rightKnee) BodySide.LEFT else BodySide.RIGHT
            }
            leftKnee != null -> BodySide.LEFT
            rightKnee != null -> BodySide.RIGHT
            else -> null
        }
        trackedSide = selected ?: trackedSide
        return trackedSide
    }

    private fun opposite(side: BodySide): BodySide {
        return if (side == BodySide.LEFT) BodySide.RIGHT else BodySide.LEFT
    }

    private fun decayActive(nowMs: Long): Boolean {
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1500L
        private const val SIDE_SWITCH_DELTA_DEG = 6f
    }
}

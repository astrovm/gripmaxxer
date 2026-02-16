package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.BodySide
import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class PistolSquatActivityDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeActivityDetector {

    private var active = false
    private var lastMotionMs = 0L
    private var baselineHipY: Float? = null
    private var lastKneeAngle: Float? = null
    private val sideTracker = BodySideTracker(
        switchDelta = SIDE_SWITCH_DELTA_DEG,
        switchStableMs = SIDE_SWITCH_STABLE_MS,
    )

    override fun reset() {
        active = false
        lastMotionMs = 0L
        baselineHipY = null
        lastKneeAngle = null
        sideTracker.reset()
    }

    override fun process(
        frame: PoseFrame,
        nowMs: Long,
    ): Boolean {
        val side = selectTrackedSide(frame, nowMs) ?: return decayActive(nowMs)
        val kneeAngle = featureExtractor.kneeAngleDegrees(frame, side) ?: return decayActive(nowMs)
        val supportKnee = featureExtractor.kneeAngleDegrees(frame, opposite(side))
        val hipY = hipYForSide(frame, side) ?: return decayActive(nowMs)

        if (baselineHipY == null) baselineHipY = hipY
        if (kneeAngle > BASELINE_RESET_KNEE_MIN) {
            baselineHipY = blend(baselineHipY ?: hipY, hipY, 0.08f)
        }

        val hipDrop = hipY - (baselineHipY ?: hipY)
        val supportLegReady = supportKnee == null || supportKnee > SUPPORT_KNEE_MIN
        val angleMotion = lastKneeAngle?.let { abs(it - kneeAngle) > KNEE_MOTION_MIN_DELTA } ?: false
        val motionDetected = supportLegReady && (
            angleMotion ||
                kneeAngle < ACTIVE_KNEE_MAX ||
                hipDrop > ACTIVE_HIP_DROP_MIN
            )
        if (motionDetected) {
            active = true
            lastMotionMs = nowMs
        }
        lastKneeAngle = kneeAngle

        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    private fun decayActive(nowMs: Long): Boolean {
        if (active && nowMs - lastMotionMs > IDLE_TIMEOUT_MS) {
            active = false
        }
        return active
    }

    private fun selectTrackedSide(frame: PoseFrame, nowMs: Long): BodySide? {
        val leftKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.LEFT)
        val rightKnee = featureExtractor.kneeAngleDegrees(frame, BodySide.RIGHT)
        return sideTracker.selectLower(leftKnee, rightKnee, nowMs)
    }

    private fun hipYForSide(frame: PoseFrame, side: BodySide): Float? {
        val landmark = when (side) {
            BodySide.LEFT -> PoseLandmark.LEFT_HIP
            BodySide.RIGHT -> PoseLandmark.RIGHT_HIP
        }
        return frame.landmark(landmark)?.y
    }

    private fun opposite(side: BodySide): BodySide {
        return if (side == BodySide.LEFT) BodySide.RIGHT else BodySide.LEFT
    }

    private fun blend(current: Float, target: Float, weight: Float): Float {
        return (current * (1f - weight)) + (target * weight)
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1500L
        private const val SIDE_SWITCH_DELTA_DEG = 12f
        private const val SIDE_SWITCH_STABLE_MS = 350L
        private const val SUPPORT_KNEE_MIN = 146f
        private const val BASELINE_RESET_KNEE_MIN = 167f
        private const val KNEE_MOTION_MIN_DELTA = 1.8f
        private const val ACTIVE_KNEE_MAX = 169f
        private const val ACTIVE_HIP_DROP_MIN = 0.02f
    }
}

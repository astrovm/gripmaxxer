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
        val hipY = hipYForSide(frame, side) ?: return decayActive(nowMs)

        if (baselineHipY == null) baselineHipY = hipY
        if (kneeAngle > 165f) {
            baselineHipY = blend(baselineHipY ?: hipY, hipY, 0.08f)
        }

        val hipDrop = hipY - (baselineHipY ?: hipY)
        val angleMotion = lastKneeAngle?.let { abs(it - kneeAngle) > 1.8f } ?: false
        val motionDetected = angleMotion || kneeAngle < 170f || hipDrop > 0.02f
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

    private fun blend(current: Float, target: Float, weight: Float): Float {
        return (current * (1f - weight)) + (target * weight)
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 1500L
        private const val SIDE_SWITCH_DELTA_DEG = 12f
        private const val SIDE_SWITCH_STABLE_MS = 350L
    }
}

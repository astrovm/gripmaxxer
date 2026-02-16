package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark

class MuscleUpRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private enum class State {
        DOWN_HANG,
        TRANSITION,
        TOP_SUPPORT,
    }

    private var state = State.DOWN_HANG
    private var reps = 0
    private var lastRepTimeMs = 0L
    private var hangSinceMs: Long? = null
    private var transitionSinceMs: Long? = null
    private var topSinceMs: Long? = null

    override fun reset() {
        state = State.DOWN_HANG
        reps = 0
        lastRepTimeMs = 0L
        hangSinceMs = null
        transitionSinceMs = null
        topSinceMs = null
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active) {
            state = State.DOWN_HANG
            hangSinceMs = null
            transitionSinceMs = null
            topSinceMs = null
            return RepCounterResult(reps = reps, repEvent = false)
        }

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
            ?: return RepCounterResult(reps = reps, repEvent = false)
        val shoulderY = frame.averageY(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            ?: return RepCounterResult(reps = reps, repEvent = false)
        val wristY = frame.averageY(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)
            ?: return RepCounterResult(reps = reps, repEvent = false)

        val isHang = elbowAngle > HANG_MIN_ELBOW_ANGLE &&
            wristY < shoulderY - WRIST_OVER_SHOULDER_MIN_DELTA
        val isRising = elbowAngle < TRANSITION_MAX_ELBOW_ANGLE
        val isTopSupport = elbowAngle < TOP_MAX_ELBOW_ANGLE &&
            wristY > shoulderY + WRIST_UNDER_SHOULDER_MIN_DELTA &&
            wristsNearShoulders(frame)

        when (state) {
            State.DOWN_HANG -> {
                if (isHang) {
                    if (hangSinceMs == null) hangSinceMs = nowMs
                } else {
                    hangSinceMs = null
                }

                val hangConfirmed = hangSinceMs != null &&
                    (nowMs - (hangSinceMs ?: nowMs) >= HANG_CONFIRM_STABLE_MS)
                if (hangConfirmed && isRising) {
                    if (transitionSinceMs == null) transitionSinceMs = nowMs
                    if (nowMs - (transitionSinceMs ?: nowMs) >= TRANSITION_STABLE_MS) {
                        state = State.TRANSITION
                        topSinceMs = null
                    }
                } else {
                    transitionSinceMs = null
                }
            }

            State.TRANSITION -> {
                if (isTopSupport) {
                    if (topSinceMs == null) topSinceMs = nowMs
                    val topStable = nowMs - (topSinceMs ?: nowMs) >= TOP_STABLE_MS
                    val cooldownPassed = nowMs - lastRepTimeMs > MIN_REP_INTERVAL_MS
                    if (topStable && cooldownPassed) {
                        reps += 1
                        lastRepTimeMs = nowMs
                        state = State.TOP_SUPPORT
                        hangSinceMs = null
                        transitionSinceMs = null
                        topSinceMs = null
                        return RepCounterResult(reps = reps, repEvent = true)
                    }
                } else {
                    topSinceMs = null
                }

                if (isHang && !isRising) {
                    state = State.DOWN_HANG
                    transitionSinceMs = null
                    topSinceMs = null
                }
            }

            State.TOP_SUPPORT -> {
                if (isHang) {
                    if (hangSinceMs == null) hangSinceMs = nowMs
                    if (nowMs - (hangSinceMs ?: nowMs) >= HANG_RESET_STABLE_MS) {
                        state = State.DOWN_HANG
                        transitionSinceMs = null
                        topSinceMs = null
                    }
                } else {
                    hangSinceMs = null
                }
            }
        }

        return RepCounterResult(reps = reps, repEvent = false)
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
        private const val HANG_MIN_ELBOW_ANGLE = 152f
        private const val TRANSITION_MAX_ELBOW_ANGLE = 146f
        private const val TOP_MAX_ELBOW_ANGLE = 115f

        private const val HANG_CONFIRM_STABLE_MS = 180L
        private const val TRANSITION_STABLE_MS = 150L
        private const val TOP_STABLE_MS = 180L
        private const val HANG_RESET_STABLE_MS = 240L
        private const val MIN_REP_INTERVAL_MS = 750L
    }
}

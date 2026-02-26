package com.astrovm.gripmaxxer.reps

import com.astrovm.gripmaxxer.pose.PoseFeatureExtractor
import com.astrovm.gripmaxxer.pose.PoseFrame
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

class BenchPressRepDetector(
    private val featureExtractor: PoseFeatureExtractor,
) : ModeRepDetector {

    private val cycleCounter = CycleRepCounter(
        stableMs = 190L,
        minRepIntervalMs = 500L,
    )

    override fun reset() {
        cycleCounter.reset()
    }

    override fun process(
        frame: PoseFrame,
        active: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        if (!active || !hasBenchPressPosture(frame)) {
            return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        }

        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)
            ?: return RepCounterResult(reps = cycleCounter.currentReps(), repEvent = false)
        val isDown = elbowAngle < DOWN_ELBOW_MAX
        val isUp = elbowAngle > UP_ELBOW_MIN
        return cycleCounter.process(isDown = isDown, isUp = isUp, nowMs = nowMs)
    }

    private fun hasBenchPressPosture(frame: PoseFrame): Boolean {
        val leftShoulder = frame.landmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = frame.landmark(PoseLandmark.RIGHT_SHOULDER)

        if (leftShoulder != null && rightShoulder != null) {
            val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
            if (shoulderWidth < MIN_SHOULDER_WIDTH) return false
        }

        val leftValid = isBenchSideValid(
            shoulder = leftShoulder,
            elbow = frame.landmark(PoseLandmark.LEFT_ELBOW),
            wrist = frame.landmark(PoseLandmark.LEFT_WRIST),
        )
        val rightValid = isBenchSideValid(
            shoulder = rightShoulder,
            elbow = frame.landmark(PoseLandmark.RIGHT_ELBOW),
            wrist = frame.landmark(PoseLandmark.RIGHT_WRIST),
        )
        return leftValid || rightValid
    }

    private fun isBenchSideValid(
        shoulder: com.astrovm.gripmaxxer.pose.NormalizedLandmark?,
        elbow: com.astrovm.gripmaxxer.pose.NormalizedLandmark?,
        wrist: com.astrovm.gripmaxxer.pose.NormalizedLandmark?,
    ): Boolean {
        if (shoulder == null || elbow == null || wrist == null) return false

        val wristNearShoulderHeight = abs(wrist.y - shoulder.y) <= MAX_WRIST_TO_SHOULDER_Y_DELTA
        val elbowNearShoulderHeight = abs(elbow.y - shoulder.y) <= MAX_ELBOW_TO_SHOULDER_Y_DELTA
        val wristNearShoulderX = abs(wrist.x - shoulder.x) <= MAX_WRIST_TO_SHOULDER_X_DELTA
        val wristNearElbowX = abs(wrist.x - elbow.x) <= MAX_WRIST_TO_ELBOW_X_DELTA
        return wristNearShoulderHeight && elbowNearShoulderHeight && wristNearShoulderX && wristNearElbowX
    }

    companion object {
        private const val MIN_SHOULDER_WIDTH = 0.075f
        private const val DOWN_ELBOW_MAX = 112f
        private const val UP_ELBOW_MIN = 154f
        private const val MAX_WRIST_TO_SHOULDER_Y_DELTA = 0.30f
        private const val MAX_ELBOW_TO_SHOULDER_Y_DELTA = 0.26f
        private const val MAX_WRIST_TO_SHOULDER_X_DELTA = 0.42f
        private const val MAX_WRIST_TO_ELBOW_X_DELTA = 0.30f
    }
}

package com.astrolabs.hangmaxxer.reps

import com.astrolabs.hangmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.hangmaxxer.pose.PoseFrame

enum class ExerciseMode(val label: String) {
    UNKNOWN("Detecting"),
    PULL_UP("Pull-up"),
    CHIN_UP("Chin-up"),
}

data class RepCounterConfig(
    val elbowUpAngle: Float = 110f,
    val elbowDownAngle: Float = 155f,
    val marginUp: Float = 0.05f,
    val marginDown: Float = 0.03f,
    val stableMs: Long = 250L,
    val minRepIntervalMs: Long = 600L,
)

data class RepCounterResult(
    val reps: Int,
    val repEvent: Boolean,
)

class RepCounter(
    private val featureExtractor: PoseFeatureExtractor,
    private var config: RepCounterConfig = RepCounterConfig(),
) {

    private enum class State {
        DOWN,
        IN_UP,
    }

    private val wristWindow = ArrayDeque<Float>()
    private val noseWindow = ArrayDeque<Float>()
    private val shoulderWindow = ArrayDeque<Float>()
    private val elbowWindow = ArrayDeque<Float>()

    private val movingAverageWindow = 5

    private var state = State.DOWN
    private var reps = 0
    private var lastRepTimeMs = 0L
    private var upCandidateSince: Long? = null
    private var downCandidateSince: Long? = null

    fun updateConfig(config: RepCounterConfig) {
        this.config = config
    }

    fun reset() {
        wristWindow.clear()
        noseWindow.clear()
        shoulderWindow.clear()
        elbowWindow.clear()
        state = State.DOWN
        reps = 0
        lastRepTimeMs = 0L
        upCandidateSince = null
        downCandidateSince = null
    }

    fun process(
        frame: PoseFrame,
        hanging: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): RepCounterResult {
        if (!hanging || !frame.posePresent) {
            return RepCounterResult(reps = reps, repEvent = false)
        }

        val wristY = frame.averageY(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST,
            com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST,
        ) ?: return RepCounterResult(reps = reps, repEvent = false)

        val shoulderY = frame.averageY(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER,
            com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER,
        ) ?: return RepCounterResult(reps = reps, repEvent = false)
        val noseY = frame.noseOrMouthY()
        val elbowAngle = featureExtractor.elbowAngleDegrees(frame) ?: return RepCounterResult(reps = reps, repEvent = false)

        val smoothWrist = pushAndAverage(wristWindow, wristY)
        val smoothShoulder = pushAndAverage(shoulderWindow, shoulderY)
        val smoothElbow = pushAndAverage(elbowWindow, elbowAngle)
        val smoothNose = if (noseY != null) {
            pushAndAverage(noseWindow, noseY)
        } else {
            noseWindow.clear()
            null
        }

        val (isDown, isUp) = if (smoothNose != null) {
            val down = smoothElbow > config.elbowDownAngle && smoothNose > smoothWrist + config.marginDown
            val up = smoothElbow < config.elbowUpAngle && smoothNose < smoothWrist - config.marginUp
            down to up
        } else {
            // Face can leave frame near the top on door-mounted bars; fall back to shoulder-vs-wrist travel.
            val shoulderToWristDelta = smoothShoulder - smoothWrist
            val down = smoothElbow > config.elbowDownAngle &&
                shoulderToWristDelta > (config.marginDown + SHOULDER_DOWN_EXTRA_DELTA)
            val up = smoothElbow < config.elbowUpAngle &&
                shoulderToWristDelta < (config.marginUp + SHOULDER_UP_EXTRA_DELTA)
            down to up
        }

        return when (state) {
            State.DOWN -> handleDownState(isUp, nowMs)
            State.IN_UP -> handleInUpState(isDown, nowMs)
        }
    }

    private fun handleDownState(isUp: Boolean, nowMs: Long): RepCounterResult {
        if (isUp) {
            if (upCandidateSince == null) {
                upCandidateSince = nowMs
            }
            val stableDuration = nowMs - (upCandidateSince ?: nowMs)
            val cooldownPassed = (nowMs - lastRepTimeMs) > config.minRepIntervalMs
            if (stableDuration >= config.stableMs && cooldownPassed) {
                reps += 1
                lastRepTimeMs = nowMs
                state = State.IN_UP
                upCandidateSince = null
                downCandidateSince = null
                return RepCounterResult(reps = reps, repEvent = true)
            }
        } else {
            upCandidateSince = null
        }

        return RepCounterResult(reps = reps, repEvent = false)
    }

    private fun handleInUpState(isDown: Boolean, nowMs: Long): RepCounterResult {
        if (isDown) {
            if (downCandidateSince == null) {
                downCandidateSince = nowMs
            }
            val stableDuration = nowMs - (downCandidateSince ?: nowMs)
            if (stableDuration >= config.stableMs) {
                state = State.DOWN
                downCandidateSince = null
                upCandidateSince = null
            }
        } else {
            downCandidateSince = null
        }

        return RepCounterResult(reps = reps, repEvent = false)
    }

    private fun pushAndAverage(window: ArrayDeque<Float>, value: Float): Float {
        window.addLast(value)
        if (window.size > movingAverageWindow) {
            window.removeFirst()
        }
        return window.average().toFloat()
    }

    companion object {
        private const val SHOULDER_DOWN_EXTRA_DELTA = 0.15f
        private const val SHOULDER_UP_EXTRA_DELTA = 0.07f
    }
}

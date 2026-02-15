package com.astrolabs.hangmaxxer.reps

import com.astrolabs.hangmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.hangmaxxer.pose.PoseFrame

enum class ExerciseMode(val label: String) {
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

        val noseY = frame.noseOrMouthY() ?: return RepCounterResult(reps = reps, repEvent = false)
        val elbowAngle = featureExtractor.elbowAngleDegrees(frame) ?: return RepCounterResult(reps = reps, repEvent = false)

        val smoothWrist = pushAndAverage(wristWindow, wristY)
        val smoothNose = pushAndAverage(noseWindow, noseY)
        val smoothElbow = pushAndAverage(elbowWindow, elbowAngle)

        val isDown = smoothElbow > config.elbowDownAngle && smoothNose > smoothWrist + config.marginDown
        val isUp = smoothElbow < config.elbowUpAngle && smoothNose < smoothWrist - config.marginUp

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
}

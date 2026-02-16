package com.astrolabs.gripmaxxer.reps

import com.astrolabs.gripmaxxer.pose.PoseFeatureExtractor
import com.astrolabs.gripmaxxer.pose.PoseFrame

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
    private var downAnchorElbow: Float? = null
    private var upAnchorElbow: Float? = null
    private var releaseLockUntilMs = 0L

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
        downAnchorElbow = null
        upAnchorElbow = null
        releaseLockUntilMs = 0L
    }

    fun process(
        frame: PoseFrame,
        hanging: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): RepCounterResult {
        if (!hanging) {
            return RepCounterResult(reps = reps, repEvent = false)
        }

        val wristY = frame.averageY(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST,
            com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST,
        )
        val shoulderY = frame.averageY(
            com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER,
            com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER,
        )
        val noseY = frame.noseOrMouthY()
        val elbowAngle = featureExtractor.elbowAngleDegrees(frame)

        if (nowMs < releaseLockUntilMs) {
            forceDownState()
            return RepCounterResult(reps = reps, repEvent = false)
        }

        if (isLikelyReleased(frame, shoulderY, wristY)) {
            releaseLockUntilMs = nowMs + RELEASE_LOCK_MS
            forceDownState()
            return RepCounterResult(reps = reps, repEvent = false)
        }

        val smoothElbow = if (elbowAngle != null) {
            pushAndAverage(elbowWindow, elbowAngle)
        } else {
            elbowWindow.lastOrNull() ?: return RepCounterResult(reps = reps, repEvent = false)
        }

        val smoothWrist = wristY?.let { pushAndAverage(wristWindow, it) }
        val smoothShoulder = shoulderY?.let { pushAndAverage(shoulderWindow, it) }
        val smoothNose = if (noseY != null) {
            pushAndAverage(noseWindow, noseY)
        } else {
            noseWindow.clear()
            null
        }

        val (isDown, isUp, requiredStableMs) = if (smoothNose != null && smoothWrist != null) {
            val down = smoothElbow > config.elbowDownAngle && smoothNose > smoothWrist + config.marginDown
            val up = smoothElbow < config.elbowUpAngle && smoothNose < smoothWrist - config.marginUp
            Triple(down, up, config.stableMs)
        } else if (smoothShoulder != null && smoothWrist != null) {
            // Face can leave frame near the top on door-mounted bars; fall back to shoulder-vs-wrist travel.
            val shoulderToWristDelta = smoothShoulder - smoothWrist
            val down = smoothElbow > (config.elbowDownAngle - SHOULDER_FALLBACK_DOWN_ELBOW_RELAX_DEG) &&
                shoulderToWristDelta > (config.marginDown + SHOULDER_DOWN_EXTRA_DELTA)
            val up = smoothElbow < (config.elbowUpAngle + SHOULDER_FALLBACK_UP_ELBOW_RELAX_DEG) &&
                shoulderToWristDelta < (config.marginUp + SHOULDER_UP_EXTRA_DELTA)
            Triple(down, up, config.stableMs)
        } else {
            // If wrists/head are occluded, rely on elbow angle only so reps still count.
            val down = smoothElbow > (config.elbowDownAngle - ELBOW_ONLY_DOWN_RELAX_DEG)
            val up = smoothElbow < (config.elbowUpAngle + ELBOW_ONLY_UP_RELAX_DEG)
            Triple(down, up, (config.stableMs / 2L).coerceAtLeast(MIN_OCCLUDED_STABLE_MS))
        }

        if (state == State.DOWN) {
            val anchor = downAnchorElbow
            downAnchorElbow = if (anchor == null) smoothElbow else maxOf(anchor, smoothElbow)
        } else {
            val anchor = upAnchorElbow
            upAnchorElbow = if (anchor == null) smoothElbow else minOf(anchor, smoothElbow)
        }

        val isUpByElbowTravel = downAnchorElbow?.let { anchor ->
            (anchor - smoothElbow) >= ELBOW_UP_TRAVEL_DEG
        } ?: false
        val isDownByElbowTravel = upAnchorElbow?.let { anchor ->
            (smoothElbow - anchor) >= ELBOW_DOWN_TRAVEL_DEG
        } ?: false

        val upStableMs = if (isUpByElbowTravel) {
            requiredStableMs.coerceAtMost(ELBOW_TRAVEL_STABLE_MS)
        } else {
            requiredStableMs
        }
        val downStableMs = if (isDownByElbowTravel) {
            requiredStableMs.coerceAtMost(ELBOW_TRAVEL_STABLE_MS)
        } else {
            requiredStableMs
        }

        return when (state) {
            State.DOWN -> handleDownState(
                isUp = isUp || isUpByElbowTravel,
                nowMs = nowMs,
                requiredStableMs = upStableMs,
                smoothElbow = smoothElbow,
            )

            State.IN_UP -> handleInUpState(
                isDown = isDown || isDownByElbowTravel,
                nowMs = nowMs,
                requiredStableMs = downStableMs,
                smoothElbow = smoothElbow,
            )
        }
    }

    private fun handleDownState(
        isUp: Boolean,
        nowMs: Long,
        requiredStableMs: Long,
        smoothElbow: Float,
    ): RepCounterResult {
        if (isUp) {
            if (upCandidateSince == null) {
                upCandidateSince = nowMs
            }
            val stableDuration = nowMs - (upCandidateSince ?: nowMs)
            val cooldownPassed = (nowMs - lastRepTimeMs) > config.minRepIntervalMs
            if (stableDuration >= requiredStableMs && cooldownPassed) {
                reps += 1
                lastRepTimeMs = nowMs
                state = State.IN_UP
                upCandidateSince = null
                downCandidateSince = null
                upAnchorElbow = smoothElbow
                downAnchorElbow = null
                return RepCounterResult(reps = reps, repEvent = true)
            }
        } else {
            upCandidateSince = null
        }

        return RepCounterResult(reps = reps, repEvent = false)
    }

    private fun handleInUpState(
        isDown: Boolean,
        nowMs: Long,
        requiredStableMs: Long,
        smoothElbow: Float,
    ): RepCounterResult {
        if (isDown) {
            if (downCandidateSince == null) {
                downCandidateSince = nowMs
            }
            val stableDuration = nowMs - (downCandidateSince ?: nowMs)
            if (stableDuration >= requiredStableMs) {
                state = State.DOWN
                downCandidateSince = null
                upCandidateSince = null
                downAnchorElbow = smoothElbow
                upAnchorElbow = null
            }
        } else {
            downCandidateSince = null
        }

        return RepCounterResult(reps = reps, repEvent = false)
    }

    private fun forceDownState() {
        state = State.DOWN
        upCandidateSince = null
        downCandidateSince = null
        downAnchorElbow = null
        upAnchorElbow = null
    }

    private fun isLikelyReleased(
        frame: PoseFrame,
        shoulderY: Float?,
        wristY: Float?,
    ): Boolean {
        if (shoulderY == null || wristY == null) return false
        val bothShouldersPresent =
            frame.landmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER) != null &&
                frame.landmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER) != null
        val bothWristsPresent =
            frame.landmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_WRIST) != null &&
                frame.landmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_WRIST) != null
        if (!bothShouldersPresent || !bothWristsPresent) return false

        return wristY > shoulderY + WRIST_BELOW_SHOULDER_RELEASE_DELTA
    }

    private fun pushAndAverage(window: ArrayDeque<Float>, value: Float): Float {
        window.addLast(value)
        if (window.size > movingAverageWindow) {
            window.removeFirst()
        }
        return window.average().toFloat()
    }

    companion object {
        private const val SHOULDER_DOWN_EXTRA_DELTA = 0.12f
        private const val SHOULDER_UP_EXTRA_DELTA = 0.14f
        private const val SHOULDER_FALLBACK_DOWN_ELBOW_RELAX_DEG = 6f
        private const val SHOULDER_FALLBACK_UP_ELBOW_RELAX_DEG = 10f
        private const val ELBOW_ONLY_DOWN_RELAX_DEG = 8f
        private const val ELBOW_ONLY_UP_RELAX_DEG = 12f
        private const val MIN_OCCLUDED_STABLE_MS = 120L
        private const val ELBOW_UP_TRAVEL_DEG = 24f
        private const val ELBOW_DOWN_TRAVEL_DEG = 22f
        private const val ELBOW_TRAVEL_STABLE_MS = 120L
        private const val WRIST_BELOW_SHOULDER_RELEASE_DELTA = 0.03f
        private const val RELEASE_LOCK_MS = 1200L
    }
}

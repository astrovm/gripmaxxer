package com.astrolabs.gripmaxxer.reps

class CycleRepCounter(
    private val stableMs: Long,
    private val minRepIntervalMs: Long,
) {

    private enum class State {
        UP,
        DOWN,
    }

    private var state = State.UP
    private var reps = 0
    private var lastRepTimeMs = 0L
    private var downCandidateSince: Long? = null
    private var upCandidateSince: Long? = null

    fun reset() {
        state = State.UP
        reps = 0
        lastRepTimeMs = 0L
        downCandidateSince = null
        upCandidateSince = null
    }

    fun currentReps(): Int = reps

    fun process(
        isDown: Boolean,
        isUp: Boolean,
        nowMs: Long,
    ): RepCounterResult {
        return when (state) {
            State.UP -> processFromUp(isDown = isDown, nowMs = nowMs)
            State.DOWN -> processFromDown(isUp = isUp, nowMs = nowMs)
        }
    }

    private fun processFromUp(isDown: Boolean, nowMs: Long): RepCounterResult {
        if (isDown) {
            if (downCandidateSince == null) downCandidateSince = nowMs
            val stableDuration = nowMs - (downCandidateSince ?: nowMs)
            if (stableDuration >= stableMs) {
                state = State.DOWN
                downCandidateSince = null
                upCandidateSince = null
            }
        } else {
            downCandidateSince = null
        }
        return RepCounterResult(reps = reps, repEvent = false)
    }

    private fun processFromDown(isUp: Boolean, nowMs: Long): RepCounterResult {
        if (isUp) {
            if (upCandidateSince == null) upCandidateSince = nowMs
            val stableDuration = nowMs - (upCandidateSince ?: nowMs)
            val cooldownPassed = nowMs - lastRepTimeMs > minRepIntervalMs
            if (stableDuration >= stableMs && cooldownPassed) {
                reps += 1
                lastRepTimeMs = nowMs
                state = State.UP
                downCandidateSince = null
                upCandidateSince = null
                return RepCounterResult(reps = reps, repEvent = true)
            }
        } else {
            upCandidateSince = null
        }
        return RepCounterResult(reps = reps, repEvent = false)
    }
}

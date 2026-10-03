package com.astrovm.gripmaxxer.tracking

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Plays accelerometer readings for a phone upside down in a front trouser pocket, at 50 Hz.
 * Each move is smooth, like a real body, and the sensor reads a bit high and a bit noisy,
 * like real ones do.
 */
class PocketMoves(var now: Long = 0L, private val feed: (Motion, Long) -> Unit) {
    /** Thigh lean from vertical, in degrees. */
    var tilt = 5f
        private set

    private fun emit(total: Float) {
        val t = Math.toRadians(tilt.toDouble())
        val noise = 0.12f * sin(now * 0.37).toFloat()
        feed(Motion(noise, (-cos(t) * total).toFloat() + noise, (sin(t) * total).toFloat()), now)
        now += STEP_MS
    }

    fun still(ms: Long) = repeat((ms / STEP_MS).toInt()) { emit(GRAVITY) }

    /** Moves [meters] up (or down if negative) in [ms], starting and ending at rest. */
    fun move(meters: Float, ms: Long) {
        val steps = (ms / STEP_MS).toInt()
        val seconds = ms / 1000.0
        repeat(steps) { i ->
            val t = i * STEP_MS / 1000.0
            val accel = 2 * PI * meters / (seconds * seconds) * sin(2 * PI * t / seconds)
            emit(GRAVITY + accel.toFloat())
        }
    }

    /** Turns the thigh to [degrees] in [ms]. */
    fun lean(degrees: Float, ms: Long) {
        val steps = (ms / STEP_MS).toInt()
        val from = tilt
        repeat(steps) { i ->
            tilt = from + (degrees - from) * (1 - cos(PI * (i + 1) / steps).toFloat()) / 2
            emit(GRAVITY)
        }
    }

    fun fall(ms: Long) = repeat((ms / STEP_MS).toInt()) { emit(0.3f) }

    fun impact() = emit(25f)

    /** Pushing up with [accel] m/s² on top of gravity. */
    private fun push(accel: Float, ms: Long) = repeat((ms / STEP_MS).toInt()) { emit(GRAVITY + accel) }

    /** Jumps and catches the bar at the top. */
    fun jumpToBar() {
        push(8f, 200)
        fall(160)
        still(200)
    }

    /** Jumps and lands back on the floor. */
    fun jump() {
        push(8f, 200)
        fall(320)
        push(15f, 100)
        still(200)
    }

    fun pullUp() {
        move(0.4f, 1_000)
        move(-0.4f, 1_200)
        still(300)
    }

    /** Letting go of the bar and landing. */
    fun drop() {
        fall(160)
        push(12f, 120)
        still(500)
    }

    /** Footsteps: a hit, then the leg giving a little to soak it up. */
    fun walk(steps: Int) = repeat(steps) {
        impact()
        repeat(3) { emit(GRAVITY - 5f) }
        still(440)
    }

    companion object {
        const val STEP_MS = 20L
        const val GRAVITY = 9.9f
    }
}

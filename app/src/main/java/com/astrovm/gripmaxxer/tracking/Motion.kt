package com.astrovm.gripmaxxer.tracking

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt

/** One accelerometer reading in m/s², in the phone's own axes. Lying still it points up, about 9.8 long. */
data class Motion(val x: Float, val y: Float, val z: Float) {
    val length: Float get() = sqrt(x * x + y * y + z * z)
}

/** What the phone's movement says right now. */
data class MotionState(
    /** How far the thigh leans from vertical, in degrees. 0 standing up, 90 with the thigh level. */
    val thighTilt: Float,
    /** Distance moved in the current direction, in meters. Positive is up. */
    val travel: Float,
    /** Barely moving for a moment. */
    val still: Boolean,
    /** Falling freely, like right after letting go of the bar or at the top of a jump. */
    val falling: Boolean,
    /** A hit, like a footstep, pushing off for a jump or a soft landing. */
    val impact: Boolean,
    /** A hard hit, like landing from a jump. */
    val landing: Boolean,
)

/**
 * Reads a phone sitting in a front trouser pocket.
 *
 * The phone lies flat on the thigh, so how much gravity points out of the screen tells
 * how far the thigh leans, whichever way up the phone is. Acceleration, added up over
 * time, tells how the body moves up and down. That sum drifts, so the speed
 * slowly leaks back to zero and the distance starts over whenever the direction flips.
 */
class MotionReader {
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var baseline: Float? = null
    private var velocity = 0f
    private var travel = 0f
    private var direction = 0
    private var lastMs: Long? = null
    private var stillSinceMs: Long? = null

    fun update(motion: Motion, nowMs: Long): MotionState {
        val last = lastMs
        lastMs = nowMs
        val dt = if (last == null) 0f else ((nowMs - last) / 1000f).coerceIn(0f, MAX_STEP_S)
        if (last == null) {
            gx = motion.x
            gy = motion.y
            gz = motion.z
        } else {
            val k = dt / (GRAVITY_TAU_S + dt)
            gx += k * (motion.x - gx)
            gy += k * (motion.y - gy)
            gz += k * (motion.z - gz)
        }
        val g = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(MIN_GRAVITY)
        val tilt = Math.toDegrees(asin((abs(gz) / g).coerceAtMost(1f)).toDouble()).toFloat()

        // Up and down shows as the reading getting longer or shorter, whichever way the phone
        // points, so turning the thigh doesn't look like moving. The baseline soaks up gravity
        // and the sensor's own bias. Jumps and landings would drag it off, so it only follows
        // gentle readings.
        val length = motion.length
        val base = baseline ?: length
        val accel = length - base
        baseline = if (abs(accel) < GENTLE) base + dt / (BASELINE_TAU_S + dt) * accel else base

        val falling = length < FALLING
        val impact = length > IMPACT
        velocity = (velocity + accel * dt) * (1f - dt / VELOCITY_TAU_S)
        val now = when {
            velocity > MOVING -> 1
            velocity < -MOVING -> -1
            else -> direction
        }
        if (now != direction) {
            direction = now
            travel = 0f
        }
        travel += velocity * dt
        val reported = travel
        // A hit shakes the phone around, which throws the distance off. Steps are hits too, so walking never adds up.
        if (impact) {
            travel = 0f
            direction = 0
        }

        // Steady readings for a moment mean the body has stopped, whatever the sum says.
        val calm = abs(accel) < CALM_ACCEL
        stillSinceMs = if (calm) stillSinceMs ?: nowMs else null
        val still = stillSinceMs.let { it != null && nowMs - it >= STILL_MS }
        if (still) {
            velocity = 0f
            travel = 0f
        }

        return MotionState(tilt, reported, still, falling, impact, landing = length > LANDING)
    }

    private companion object {
        const val MAX_STEP_S = 0.1f
        const val GRAVITY_TAU_S = 0.25f
        const val BASELINE_TAU_S = 4f
        const val VELOCITY_TAU_S = 4f
        const val MIN_GRAVITY = 0.1f
        /** Speed in m/s below which the body counts as not going anywhere. */
        const val MOVING = 0.06f
        const val CALM_ACCEL = 0.5f
        const val GENTLE = 2f
        const val STILL_MS = 400L
        /** Under about a third of gravity: nothing is holding you up. */
        const val FALLING = 3.5f
        /** About one and a half times gravity. */
        const val IMPACT = 14.5f
        /** About twice gravity. */
        const val LANDING = 19.6f
    }
}

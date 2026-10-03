package com.astrovm.gripmaxxer.tracking

/** Where the body is within one rep. */
enum class Position {
    /** Where every rep starts and ends: hanging straight, standing tall, arms locked out. */
    REST,

    /** The hard end of the rep: chin over the bar, chest at the floor, bottom of the squat. */
    PEAK,
}

/**
 * Counts reps from a stream of positions.
 *
 * A rep is REST, then PEAK, then back to REST. Pulling moves count at the peak so the
 * number goes up the moment you get your chin over the bar. Pushing moves count when
 * you get back to the start, so a half rep never counts.
 *
 * A position only sticks after it holds for [settleMs], which filters out one-frame glitches.
 */
class RepCounter(
    private val countAtPeak: Boolean,
    private val settleMs: Long = 100L,
) {
    var reps: Int = 0
        private set

    /** When the latest rep started: the last moment at rest before going for the peak. */
    var repStartedAtMs: Long? = null
        private set

    private var lastRestAtMs: Long? = null

    private var settled: Position? = null
    private var candidate: Position? = null
    private var candidateSinceMs = 0L
    private var armed = false
    private var peakPending = false

    /** Feeds one frame. [position] is null between the two ends. Returns true when a rep is counted. */
    fun update(position: Position?, nowMs: Long): Boolean {
        if (position == Position.REST) lastRestAtMs = nowMs
        if (position != candidate) {
            candidate = position
            candidateSinceMs = nowMs
        }
        if (position == null || position == settled || nowMs - candidateSinceMs < settleMs) return false
        settled = position

        return when (position) {
            Position.REST -> {
                val counted = peakPending
                peakPending = false
                armed = true
                if (counted) reps++
                counted
            }

            Position.PEAK -> {
                if (!armed) return false
                armed = false
                repStartedAtMs = lastRestAtMs
                if (countAtPeak) {
                    reps++
                    true
                } else {
                    peakPending = true
                    false
                }
            }
        }
    }
}

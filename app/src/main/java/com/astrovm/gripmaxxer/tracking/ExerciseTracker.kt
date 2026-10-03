package com.astrovm.gripmaxxer.tracking

/** A set the tracker saw from start to finish. */
data class TrackedSet(
    val exercise: Exercise,
    val reps: Int,
    val durationMs: Long,
    val endedAtMs: Long,
)

/** What the tracker sees right now. */
data class TrackerState(
    /** A set is in progress. */
    val inSet: Boolean = false,
    val reps: Int = 0,
    val setDurationMs: Long = 0L,
    /** A rep was counted on this frame. */
    val repCounted: Boolean = false,
    /** A set ended on this frame. */
    val finishedSet: TrackedSet? = null,
)

/** Turns a stream of readings into sets for one exercise. */
sealed interface SetTracker {
    val exercise: Exercise

    /** Ends the set in progress, if any. Call when switching exercise or stopping. */
    fun finish(nowMs: Long): TrackedSet?
}

/** The set from [startMs] to [endMs], or null if it was too short to keep. */
internal fun completedSet(exercise: Exercise, reps: Int, startMs: Long, endMs: Long, nowMs: Long, minReps: Int = 1): TrackedSet? {
    val duration = (endMs - startMs).coerceAtLeast(0L)
    val counts = if (exercise.isHold) duration >= MIN_HOLD_MS else reps >= minReps
    return if (counts) TrackedSet(exercise, reps, duration, nowMs) else null
}

private const val MIN_HOLD_MS = 2_000L

/**
 * Turns camera frames into sets for one exercise.
 *
 * Bar exercises: a set is one hang, from grabbing the bar to letting go.
 * Floor exercises: a set starts with the first rep and ends after a few seconds of rest.
 */
class ExerciseTracker(override val exercise: Exercise) : SetTracker {

    private val bar = if (exercise.onBar) BarDetector() else null
    private val reader = PositionReader.forExercise(exercise)
    private var counter = newCounter()
    private var setStartMs: Long? = null
    private var lastMoveMs = 0L
    private var lastPoseMs = 0L

    fun update(pose: Pose?, nowMs: Long): TrackerState {
        if (pose != null) lastPoseMs = nowMs
        return if (bar != null) updateOnBar(bar, pose, nowMs) else updateOnFloor(pose, nowMs)
    }

    override fun finish(nowMs: Long): TrackedSet? {
        val start = setStartMs ?: return null
        val end = if (bar != null) bar.lastGripAtMs else lastMoveMs
        return closeSet(start, end.coerceAtMost(nowMs), nowMs)
    }

    private fun updateOnBar(bar: BarDetector, pose: Pose?, nowMs: Long): TrackerState {
        val wasOnBar = bar.onBar
        val onBar = bar.update(pose, nowMs)
        if (!onBar) {
            val finished = if (wasOnBar) closeSet(setStartMs!!, bar.lastGripAtMs, nowMs) else null
            return TrackerState(finishedSet = finished)
        }
        val start = setStartMs ?: bar.hangStartedAtMs!!.also { setStartMs = it }
        val counted = pose != null && reader != null && counter.update(reader.read(pose), nowMs)
        return TrackerState(
            inSet = true,
            reps = counter.reps,
            setDurationMs = nowMs - start,
            repCounted = counted,
        )
    }

    private fun updateOnFloor(pose: Pose?, nowMs: Long): TrackerState {
        val position = pose?.let { reader!!.read(it) }
        val counted = counter.update(position, nowMs)
        if (counted) {
            lastMoveMs = nowMs
            if (setStartMs == null) setStartMs = counter.repStartedAtMs
        }
        val start = setStartMs ?: return TrackerState()
        val resting = nowMs - lastMoveMs >= FLOOR_REST_MS || nowMs - lastPoseMs >= FLOOR_LOST_MS
        if (resting) return TrackerState(finishedSet = closeSet(start, lastMoveMs, nowMs))
        return TrackerState(
            inSet = true,
            reps = counter.reps,
            setDurationMs = nowMs - start,
            repCounted = counted,
        )
    }

    private fun closeSet(startMs: Long, endMs: Long, nowMs: Long): TrackedSet? {
        val reps = counter.reps
        setStartMs = null
        counter = newCounter()
        return completedSet(exercise, reps, startMs, endMs, nowMs)
    }

    private fun newCounter() = RepCounter(countAtPeak = exercise.onBar)

    private companion object {
        const val FLOOR_REST_MS = 5_000L
        const val FLOOR_LOST_MS = 3_000L
    }
}

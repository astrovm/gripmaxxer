package com.astrovm.gripmaxxer.tracking

/**
 * Reads where the body is within a rep from the phone in your pocket.
 * Like [PositionReader], REST and PEAK have a gap between them so noise can't flip it.
 */
fun interface PocketReader {
    fun read(state: MotionState): Position?

    companion object {
        fun forExercise(exercise: Exercise): PocketReader? = when (exercise) {
            Exercise.DEAD_HANG, Exercise.ACTIVE_HANG -> null
            Exercise.PULL_UP, Exercise.CHIN_UP -> TravelReader(upFirst = true, distance = 0.15f)
            Exercise.HANGING_LEG_RAISE -> TiltReader(restBelow = 30f, peakAbove = 65f)
            // The pocket sits between the hips and the toes, so it moves less than the chest.
            Exercise.PUSH_UP -> TravelReader(upFirst = false, distance = 0.06f, minTilt = 50f)
            Exercise.DIP -> TravelReader(upFirst = false, distance = 0.12f)
            Exercise.SQUAT -> TiltReader(restBelow = 35f, peakAbove = 65f)
        }
    }
}

/** Squats and leg raises: thigh hanging down at rest, close to level at the peak. */
class TiltReader(private val restBelow: Float, private val peakAbove: Float) : PocketReader {
    override fun read(state: MotionState): Position? = when {
        state.thighTilt > peakAbove -> Position.PEAK
        state.thighTilt < restBelow -> Position.REST
        else -> null
    }
}

/**
 * Pull-ups, chin-ups, dips and push-ups: the body goes up and down while the thigh barely turns.
 * Pulls start by going up [distance] meters, the others by going down.
 *
 * Holding still also counts as rest, so the first rep counts from a still hang. Not right
 * after the peak though, or a pause at the bottom of a push-up would finish the rep.
 * Push-ups only count with the thighs close to level, [minTilt], so walking around can't.
 */
class TravelReader(
    private val upFirst: Boolean,
    private val distance: Float,
    private val minTilt: Float = 0f,
) : PocketReader {
    private var last: Position? = null

    override fun read(state: MotionState): Position? {
        if (state.thighTilt < minTilt) return null
        val toward = if (upFirst) state.travel else -state.travel
        val position = when {
            toward >= distance -> Position.PEAK
            toward <= -distance * RETURN -> Position.REST
            state.still && last != Position.PEAK -> Position.REST
            else -> null
        }
        if (position != null) last = position
        return position
    }

    private companion object {
        /** Coming back counts at a bit over half the way, since the sum loses some distance. */
        const val RETURN = 0.6f
    }
}

/**
 * Tells from the pocket whether you're hanging from a bar.
 *
 * Getting on: a jump, or moving up with the thigh upright like the first pull-up,
 * without landing right after. Getting off: a fall followed by landing, or a few footsteps.
 */
class PocketBarDetector {

    var onBar: Boolean = false
        private set

    /** When the current hang started. Null while off the bar. */
    var hangStartedAtMs: Long? = null
        private set

    /** Last moment still on the bar. */
    var lastGripAtMs: Long = 0L
        private set

    /** When a move up that might be the start of a hang began. */
    var candidateAtMs: Long? = null
        private set

    /** The hang started with a jump rather than a pull-up. */
    var jumped: Boolean = false
        private set

    private var uprightSinceMs: Long? = null
    private var wasFalling = false
    private var fallStartMs = 0L
    private var fallEndMs: Long? = null
    private val steps = ArrayDeque<Long>()

    fun update(state: MotionState, nowMs: Long): Boolean {
        if (state.falling) {
            if (!wasFalling) fallStartMs = nowMs
            fallEndMs = nowMs
        }
        wasFalling = state.falling
        val upright = state.thighTilt < UPRIGHT
        uprightSinceMs = if (upright) uprightSinceMs ?: nowMs else null

        if (onBar) updateOnBar(state, upright, nowMs) else updateOffBar(state, upright, nowMs)
        return onBar
    }

    private fun updateOnBar(state: MotionState, upright: Boolean, nowMs: Long) {
        if (state.impact && upright) steps.addLast(nowMs)
        while (steps.isNotEmpty() && nowMs - steps.first() > STEPS_MS) steps.removeFirst()
        val fallEnd = fallEndMs
        val landed = state.impact && fallEnd != null && nowMs - fallEnd <= LANDING_MS &&
            fallEnd - fallStartMs >= MIN_FALL_MS
        when {
            landed -> getOff(fallStartMs)
            steps.size >= STEPS -> getOff(steps.first())
            !state.falling -> lastGripAtMs = nowMs
        }
    }

    private fun updateOffBar(state: MotionState, upright: Boolean, nowMs: Long) {
        val candidate = candidateAtMs
        if (candidate == null) {
            val settled = uprightSinceMs.let { it != null && nowMs - it >= UPRIGHT_MS }
            val fell = state.falling && nowMs - fallStartMs >= MIN_FALL_MS
            if (upright && (fell || (settled && state.travel >= UP_MOVE))) {
                candidateAtMs = nowMs
                jumped = fell
            }
            return
        }
        val fallEnd = fallEndMs ?: candidate
        when {
            // Hopped and landed, or walked off. Catching the bar at the top of a jump is gentler.
            state.landing || (state.impact && !jumped) -> candidateAtMs = null
            nowMs - maxOf(candidate, fallEnd) >= CONFIRM_MS -> {
                onBar = true
                hangStartedAtMs = candidate
                lastGripAtMs = nowMs
                candidateAtMs = null
            }
        }
    }

    private fun getOff(atMs: Long) {
        onBar = false
        hangStartedAtMs = null
        lastGripAtMs = atMs
        steps.clear()
    }

    private companion object {
        const val UPRIGHT = 45f
        /** Upright for this long first, so standing up from a squat isn't a hop. */
        const val UPRIGHT_MS = 1_000L
        const val UP_MOVE = 0.08f
        /** A hop that doesn't land within this long caught the bar. */
        const val CONFIRM_MS = 600L
        const val LANDING_MS = 400L
        const val MIN_FALL_MS = 50L
        const val STEPS = 3
        const val STEPS_MS = 3_000L
    }
}

/**
 * Turns pocket motion into sets for one exercise, like [ExerciseTracker] does with the camera.
 *
 * The rep counter runs the whole time, so a pull-up that gets you on the bar still counts.
 * Reps from before the set started are left out.
 */
class PocketTracker(override val exercise: Exercise) : SetTracker {

    private val motion = MotionReader()
    private val bar = if (exercise.onBar) PocketBarDetector() else null
    private val reader = PocketReader.forExercise(exercise)
    private val counter = RepCounter(countAtPeak = exercise.onBar)
    private var repsBefore = 0
    private var repsAtCandidate = 0
    private var setStartMs: Long? = null
    private var lastMoveMs = 0L

    fun update(sample: Motion, nowMs: Long): TrackerState {
        val state = motion.update(sample, nowMs)
        val counted = reader != null && counter.update(reader.read(state), nowMs)
        return if (bar != null) updateOnBar(bar, state, counted, nowMs) else updateOnFloor(counted, nowMs)
    }

    override fun finish(nowMs: Long): TrackedSet? {
        val start = setStartMs ?: return null
        val end = if (bar != null) bar.lastGripAtMs else lastMoveMs
        return closeSet(start, end.coerceAtMost(nowMs), nowMs)
    }

    private fun updateOnBar(bar: PocketBarDetector, state: MotionState, counted: Boolean, nowMs: Long): TrackerState {
        val wasOnBar = bar.onBar
        val hadCandidate = bar.candidateAtMs != null
        val onBar = bar.update(state, nowMs)
        if (!hadCandidate && bar.candidateAtMs != null) repsAtCandidate = counter.reps
        if (!onBar) {
            val finished = if (wasOnBar) closeSet(setStartMs!!, bar.lastGripAtMs, nowMs) else null
            return TrackerState(finishedSet = finished)
        }
        if (!wasOnBar) {
            setStartMs = bar.hangStartedAtMs
            // A jump up to the bar looks like a pull-up. Only count what came after it.
            repsBefore = if (bar.jumped) counter.reps else repsAtCandidate
        }
        val reps = counter.reps - repsBefore
        return TrackerState(
            inSet = true,
            reps = reps,
            setDurationMs = nowMs - setStartMs!!,
            // The pull-up that got you on the bar beeps once the hang is sure.
            repCounted = counted || (!wasOnBar && reps > 0),
        )
    }

    private fun updateOnFloor(counted: Boolean, nowMs: Long): TrackerState {
        if (counted) {
            lastMoveMs = nowMs
            if (setStartMs == null) setStartMs = counter.repStartedAtMs ?: nowMs
        }
        val start = setStartMs ?: return TrackerState().also { repsBefore = counter.reps }
        if (nowMs - lastMoveMs >= FLOOR_REST_MS) return TrackerState(finishedSet = closeSet(start, lastMoveMs, nowMs))
        return TrackerState(
            inSet = true,
            reps = counter.reps - repsBefore,
            setDurationMs = nowMs - start,
            repCounted = counted,
        )
    }

    private fun closeSet(startMs: Long, endMs: Long, nowMs: Long): TrackedSet? {
        val reps = counter.reps - repsBefore
        repsBefore = counter.reps
        setStartMs = null
        // Sitting down and getting up looks like one squat, so a floor set needs two reps.
        return completedSet(exercise, reps, startMs, endMs, nowMs, minReps = if (bar == null) 2 else 1)
    }

    private companion object {
        const val FLOOR_REST_MS = 5_000L
    }
}

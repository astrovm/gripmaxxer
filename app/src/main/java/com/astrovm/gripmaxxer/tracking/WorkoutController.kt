package com.astrovm.gripmaxxer.tracking

import com.astrovm.gripmaxxer.data.Settings
import com.astrovm.gripmaxxer.data.SettingsRepository
import com.astrovm.gripmaxxer.data.WorkoutRepository
import com.astrovm.gripmaxxer.feedback.Cues
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the live workout screen and the floating timer show. */
data class LiveState(
    /** The camera is on and counting. */
    val tracking: Boolean = false,
    val exercise: Exercise? = null,
    /** When the open workout started, for the notification clock. */
    val workoutStartedAtMs: Long? = null,
    /** A person is in frame. Always true in the pocket. */
    val personVisible: Boolean = false,
    /** The phone is in a pocket: the motion sensors count instead of the camera. */
    val inPocket: Boolean = false,
    val inSet: Boolean = false,
    val reps: Int = 0,
    val setDurationMs: Long = 0L,
    /** The last set saved on its own, shown until the next one starts. */
    val lastSet: TrackedSet? = null,
    /** Why the camera stopped on its own. */
    val error: String? = null,
)

/** Things that need Android. The tracking service plugs in the real ones. */
interface TrackingEffects {
    fun beep()
    fun say(text: String)
    fun playMedia()
    fun pauseMedia()
}

/**
 * Runs the active workout: turns camera frames, or motion from the pocket, into saved
 * sets, and starts and stops tracking with the workout. Lives as long as the app process.
 *
 * Frames arrive on the camera thread, everything else on the main thread, so the
 * tracker is only touched under [lock]. Database writes run one at a time in order,
 * so a set is always saved before the workout it belongs to is finished.
 */
class WorkoutController(
    private val workouts: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    private val startCamera: () -> Unit,
    private val stopCamera: () -> Unit,
) {
    private val lock = Any()
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()

    /** Null until the tracking service is running. */
    @Volatile
    var effects: TrackingEffects? = null

    @Volatile
    private var settings = Settings()
    private var workoutId: Long? = null
    private var tracker: SetTracker? = null
    private var lastPoseMs: Long? = null
    private var lastHoldCue = 0L

    init {
        // A failed write must not stop the ones queued behind it.
        scope.launch { for (write in writes) runCatching { write() } }
        scope.launch { settingsRepository.settings.collect { settings = it } }
    }

    /** Starts a new workout, or picks the running one back up. */
    suspend fun start(exercise: Exercise) {
        settingsRepository.setLastExercise(exercise)
        val id = workouts.start(exercise)
        val current = workouts.active.first()
        track(id, current?.exercise ?: exercise, current?.startedAtMs)
    }

    /** Turns the camera back on for a workout that's still open, e.g. after the app was closed. */
    suspend fun resume() {
        val active = workouts.active.first() ?: return
        track(active.id, active.exercise, active.startedAtMs)
    }

    suspend fun switchExercise(exercise: Exercise) {
        val id = synchronized(lock) {
            val id = workoutId ?: return
            if (tracker?.exercise == exercise) return
            flushSet(id)
            tracker = newTracker(exercise, _live.value.inPocket)
            _live.update {
                LiveState(
                    tracking = it.tracking,
                    exercise = exercise,
                    workoutStartedAtMs = it.workoutStartedAtMs,
                    personVisible = it.personVisible,
                    inPocket = it.inPocket,
                )
            }
            id
        }
        settingsRepository.setLastExercise(exercise)
        write { workouts.switchExercise(id, exercise) }
    }

    /** Saves the set in progress and closes the workout. Returns false if it had no sets and was dropped. */
    suspend fun finish(): Boolean {
        val id = synchronized(lock) { workoutId } ?: workouts.active.first()?.id ?: return false
        stopTracking()
        return write { workouts.finish(id) }
    }

    /** Saves the set in progress and turns the camera off. The workout stays open. */
    fun stopTracking(error: String? = null) {
        val wasTracking = synchronized(lock) {
            val id = workoutId ?: return@synchronized false
            flushSet(id)
            workoutId = null
            tracker = null
            _live.value = LiveState(error = error)
            true
        }
        if (wasTracking) stopCamera()
    }

    /**
     * Switches between counting with the camera and with the motion sensors.
     * Saves the set in progress first, since the other one can't pick it up.
     */
    fun setInPocket(inPocket: Boolean) {
        synchronized(lock) {
            val id = workoutId ?: return
            val exercise = tracker?.exercise ?: return
            if (_live.value.inPocket == inPocket) return
            flushSet(id)
            tracker = newTracker(exercise, inPocket)
            _live.update {
                LiveState(
                    tracking = it.tracking,
                    exercise = exercise,
                    workoutStartedAtMs = it.workoutStartedAtMs,
                    personVisible = inPocket,
                    inPocket = inPocket,
                    lastSet = it.lastSet,
                )
            }
        }
    }

    fun onFrame(pose: Pose?, nowMs: Long) {
        synchronized(lock) {
            val id = workoutId ?: return
            val tracker = tracker as? ExerciseTracker ?: return
            if (pose != null) lastPoseMs = nowMs
            val visible = lastPoseMs.let { it != null && nowMs - it < PERSON_GONE_MS }
            apply(id, tracker, tracker.update(pose, nowMs), visible)
        }
    }

    fun onMotion(motion: Motion, nowMs: Long) {
        synchronized(lock) {
            val id = workoutId ?: return
            val tracker = tracker as? PocketTracker ?: return
            apply(id, tracker, tracker.update(motion, nowMs), visible = true)
        }
    }

    /** Must hold [lock]. */
    private fun apply(id: Long, tracker: SetTracker, state: TrackerState, visible: Boolean) {
        val wasInSet = _live.value.inSet
        state.finishedSet?.let { save(id, it) }
        if (state.repCounted && settings.repSound) effects?.beep()
        if (state.inSet != wasInSet && settings.mediaControl) {
            if (state.inSet) effects?.playMedia() else effects?.pauseMedia()
        }
        if (!state.inSet) lastHoldCue = 0L
        if (state.inSet && tracker.exercise.isHold) cueHold(state.setDurationMs)
        _live.update {
            it.copy(
                personVisible = visible,
                inSet = state.inSet,
                reps = state.reps,
                setDurationMs = state.setDurationMs,
                lastSet = state.finishedSet ?: it.lastSet.takeUnless { state.inSet },
            )
        }
    }

    private fun track(id: Long, exercise: Exercise, startedAtMs: Long?) {
        synchronized(lock) {
            if (workoutId == id && tracker != null) return
            workoutId = id
            tracker = newTracker(exercise, inPocket = false)
            lastPoseMs = null
            _live.value = LiveState(tracking = true, exercise = exercise, workoutStartedAtMs = startedAtMs)
        }
        startCamera()
    }

    private fun newTracker(exercise: Exercise, inPocket: Boolean): SetTracker =
        if (inPocket) PocketTracker(exercise) else ExerciseTracker(exercise)

    private fun cueHold(durationMs: Long) {
        val seconds = durationMs / 1000
        val mark = seconds / Cues.HOLD_CUE_EVERY_S * Cues.HOLD_CUE_EVERY_S
        if (mark <= lastHoldCue) return
        lastHoldCue = mark
        if (settings.voiceCues) effects?.say(Cues.holdText(mark))
    }

    /** Must hold [lock]. */
    private fun flushSet(id: Long) {
        tracker?.finish(System.currentTimeMillis())?.let { save(id, it) }
        if (_live.value.inSet && settings.mediaControl) effects?.pauseMedia()
    }

    private fun save(id: Long, set: TrackedSet) {
        writes.trySend { workouts.addTrackedSet(id, set) }
    }

    private suspend fun <T> write(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        writes.send { result.completeWith(runCatching { block() }) }
        return result.await()
    }

    private companion object {
        const val PERSON_GONE_MS = 1_000L
    }
}

package com.astrovm.gripmaxxer.workout

import com.astrovm.gripmaxxer.datastore.GripmaxxerDatabase
import com.astrovm.gripmaxxer.datastore.ProfileModeRow
import com.astrovm.gripmaxxer.datastore.WorkoutEntity
import com.astrovm.gripmaxxer.datastore.WorkoutFeedRow
import com.astrovm.gripmaxxer.datastore.WorkoutSetEntity
import com.astrovm.gripmaxxer.datastore.WorkoutWithSetsEntity
import com.astrovm.gripmaxxer.reps.ExerciseMode
import com.astrovm.gripmaxxer.service.AutoSetEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface WorkoutRepository {
    val activeWorkoutFlow: Flow<ActiveWorkoutState?>
    val completedWorkoutFeedFlow: Flow<List<WorkoutFeedItem>>
    val calendarSummaryFlow: Flow<List<CalendarDaySummary>>
    val profileStatsFlow: Flow<List<ExerciseProfileStats>>

    suspend fun startWorkout(mode: ExerciseMode): Long
    suspend fun pauseWorkout(workoutId: Long): Boolean
    suspend fun resumeWorkout(workoutId: Long): Boolean
    suspend fun endWorkout(workoutId: Long): Boolean
    suspend fun appendAutoSet(workoutId: Long, event: AutoSetEvent): WorkoutSetState?
    suspend fun editSet(setId: Long, reps: Int, durationMs: Long): Boolean
    suspend fun deleteSet(setId: Long): Boolean
    suspend fun deleteWorkout(workoutId: Long): Boolean
    suspend fun getCompletedWorkoutDetail(workoutId: Long): CompletedWorkoutDetail?
    suspend fun getSetById(setId: Long): WorkoutSetState?
}

class RoomWorkoutRepository(
    private val database: GripmaxxerDatabase,
) : WorkoutRepository {
    private val workoutDao = database.workoutDao()

    override val activeWorkoutFlow: Flow<ActiveWorkoutState?> = workoutDao.observeActiveWorkoutWithSets()
        .map { workout -> workout?.toActiveDomain() }

    override val completedWorkoutFeedFlow: Flow<List<WorkoutFeedItem>> = workoutDao.observeCompletedWorkoutFeed()
        .map { rows -> rows.map { it.toDomain() } }

    override val calendarSummaryFlow: Flow<List<CalendarDaySummary>> = workoutDao.observeCalendarSummary()
        .map { rows -> rows.map { CalendarDaySummary(dayEpochMs = it.dayEpochMs, workoutCount = it.workoutCount) } }

    override val profileStatsFlow: Flow<List<ExerciseProfileStats>> = workoutDao.observeProfileByMode()
        .map { rows ->
            rows.mapNotNull { it.toDomain() }
                .sortedBy { CameraTrackableModes.indexOf(it.mode).takeIf { idx -> idx >= 0 } ?: Int.MAX_VALUE }
        }

    override suspend fun startWorkout(mode: ExerciseMode): Long {
        val activeId = workoutDao.getActiveWorkoutId()
        if (activeId != null) return activeId
        val now = System.currentTimeMillis()
        return workoutDao.insertWorkout(
            WorkoutEntity(
                title = mode.label,
                exerciseModeName = mode.name,
                startedAtMs = now,
                completedAtMs = null,
                isPaused = false,
                pauseStartedAtMs = null,
                pausedAccumulatedMs = 0L,
            )
        )
    }

    override suspend fun pauseWorkout(workoutId: Long): Boolean {
        val full = workoutDao.getWorkoutWithSets(workoutId) ?: return false
        val workout = full.workout
        if (workout.completedAtMs != null || workout.isPaused) return false
        workoutDao.updateWorkout(
            workout.copy(
                isPaused = true,
                pauseStartedAtMs = System.currentTimeMillis(),
            )
        )
        return true
    }

    override suspend fun resumeWorkout(workoutId: Long): Boolean {
        val full = workoutDao.getWorkoutWithSets(workoutId) ?: return false
        val workout = full.workout
        if (workout.completedAtMs != null || !workout.isPaused) return false

        val pausedStart = workout.pauseStartedAtMs ?: System.currentTimeMillis()
        val addedPaused = (System.currentTimeMillis() - pausedStart).coerceAtLeast(0L)

        workoutDao.updateWorkout(
            workout.copy(
                isPaused = false,
                pauseStartedAtMs = null,
                pausedAccumulatedMs = workout.pausedAccumulatedMs + addedPaused,
            )
        )
        return true
    }

    override suspend fun endWorkout(workoutId: Long): Boolean {
        val full = workoutDao.getWorkoutWithSets(workoutId) ?: return false
        val workout = full.workout
        if (workout.completedAtMs != null) return true
        if (full.sets.isEmpty()) {
            workoutDao.deleteWorkoutById(workoutId)
            return true
        }

        val now = System.currentTimeMillis()
        val finalPausedAccumulated = if (workout.isPaused) {
            val pausedStart = workout.pauseStartedAtMs ?: now
            workout.pausedAccumulatedMs + (now - pausedStart).coerceAtLeast(0L)
        } else {
            workout.pausedAccumulatedMs
        }

        workoutDao.updateWorkout(
            workout.copy(
                completedAtMs = now,
                isPaused = false,
                pauseStartedAtMs = null,
                pausedAccumulatedMs = finalPausedAccumulated,
            )
        )
        return true
    }

    override suspend fun appendAutoSet(workoutId: Long, event: AutoSetEvent): WorkoutSetState? {
        val full = workoutDao.getWorkoutWithSets(workoutId) ?: return null
        val workout = full.workout
        if (workout.completedAtMs != null || workout.isPaused) return null

        val mode = parseMode(workout.exerciseModeName) ?: return null
        if (mode != event.mode) return null

        val nextSet = workoutDao.countSetsForWorkout(workoutId) + 1
        val setId = workoutDao.insertWorkoutSet(
            WorkoutSetEntity(
                workoutId = workoutId,
                setNumber = nextSet,
                reps = event.reps.coerceAtLeast(0),
                durationMs = event.activeMs.coerceAtLeast(0L),
                completedAtMs = event.timestampMs,
                autoTracked = true,
            )
        )
        return workoutDao.getSetById(setId)?.toDomain()
    }

    override suspend fun editSet(setId: Long, reps: Int, durationMs: Long): Boolean {
        val current = workoutDao.getSetById(setId) ?: return false
        workoutDao.updateWorkoutSet(
            current.copy(
                reps = reps.coerceAtLeast(0),
                durationMs = durationMs.coerceAtLeast(0L),
            )
        )
        return true
    }

    override suspend fun deleteSet(setId: Long): Boolean {
        val workoutId = workoutDao.getWorkoutIdBySetId(setId) ?: return false
        workoutDao.deleteWorkoutSetById(setId)

        val remaining = workoutDao.getSetsForWorkout(workoutId)
        if (remaining.isEmpty()) {
            val workout = workoutDao.getWorkoutWithSets(workoutId)?.workout
            if (workout?.completedAtMs != null) {
                workoutDao.deleteWorkoutById(workoutId)
            }
            return true
        }

        val resequenced = remaining
            .sortedBy { it.setNumber }
            .mapIndexed { index, set ->
                if (set.setNumber == index + 1) set else set.copy(setNumber = index + 1)
            }

        if (resequenced.isNotEmpty()) {
            workoutDao.updateWorkoutSets(resequenced)
        }
        return true
    }

    override suspend fun deleteWorkout(workoutId: Long): Boolean {
        val existing = workoutDao.getWorkoutWithSets(workoutId) ?: return false
        if (existing.workout.completedAtMs == null) return false
        workoutDao.deleteWorkoutById(workoutId)
        return true
    }

    override suspend fun getCompletedWorkoutDetail(workoutId: Long): CompletedWorkoutDetail? {
        val full = workoutDao.getWorkoutWithSets(workoutId) ?: return null
        val completedAt = full.workout.completedAtMs ?: return null
        val mode = parseMode(full.workout.exerciseModeName) ?: return null

        return CompletedWorkoutDetail(
            workoutId = full.workout.id,
            title = full.workout.title,
            mode = mode,
            startedAtMs = full.workout.startedAtMs,
            completedAtMs = completedAt,
            durationMs = (completedAt - full.workout.startedAtMs - full.workout.pausedAccumulatedMs).coerceAtLeast(0L),
            sets = full.sets.sortedBy { it.setNumber }.map { it.toDomain() },
        )
    }

    override suspend fun getSetById(setId: Long): WorkoutSetState? {
        return workoutDao.getSetById(setId)?.toDomain()
    }

    private fun WorkoutWithSetsEntity.toActiveDomain(): ActiveWorkoutState {
        val mode = parseMode(workout.exerciseModeName) ?: ExerciseMode.PULL_UP
        val now = System.currentTimeMillis()
        val runningBase = if (workout.isPaused) {
            (workout.pauseStartedAtMs ?: now)
        } else {
            now
        }

        val elapsed = (runningBase - workout.startedAtMs - workout.pausedAccumulatedMs).coerceAtLeast(0L)

        return ActiveWorkoutState(
            id = workout.id,
            title = workout.title,
            mode = mode,
            startedAtMs = workout.startedAtMs,
            elapsedMs = elapsed,
            elapsedComputedAtMs = now,
            paused = workout.isPaused,
            sets = sets.sortedBy { it.setNumber }.map { it.toDomain() },
        )
    }

    private fun WorkoutFeedRow.toDomain(): WorkoutFeedItem {
        return WorkoutFeedItem(
            workoutId = workoutId,
            title = title,
            mode = parseMode(modeName) ?: ExerciseMode.PULL_UP,
            completedAtMs = completedAtMs,
            durationMs = durationMs.coerceAtLeast(0L),
            setCount = setCount,
        )
    }

    private fun ProfileModeRow.toDomain(): ExerciseProfileStats? {
        val mode = parseMode(modeName) ?: return null
        return ExerciseProfileStats(
            mode = mode,
            totalWorkouts = totalWorkouts,
            maxReps = maxReps,
            maxHoldMs = maxHoldMs,
        )
    }

    private fun WorkoutSetEntity.toDomain(): WorkoutSetState {
        return WorkoutSetState(
            id = id,
            setNumber = setNumber,
            reps = reps,
            durationMs = durationMs,
            completedAtMs = completedAtMs,
            autoTracked = autoTracked,
        )
    }

    private fun parseMode(raw: String?): ExerciseMode? {
        if (raw.isNullOrBlank()) return null
        return runCatching { ExerciseMode.valueOf(raw) }.getOrNull()
    }
}

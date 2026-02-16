package com.astrolabs.gripmaxxer.workout

import androidx.room.withTransaction
import com.astrolabs.gripmaxxer.datastore.CalendarDayRow
import com.astrolabs.gripmaxxer.datastore.GripmaxxerDatabase
import com.astrolabs.gripmaxxer.datastore.ProfileBaseRow
import com.astrolabs.gripmaxxer.datastore.RecordRow
import com.astrolabs.gripmaxxer.datastore.RoutineEntity
import com.astrolabs.gripmaxxer.datastore.RoutineExerciseEntity
import com.astrolabs.gripmaxxer.datastore.RoutineWithExercisesEntity
import com.astrolabs.gripmaxxer.datastore.WorkoutEntity
import com.astrolabs.gripmaxxer.datastore.WorkoutExerciseEntity
import com.astrolabs.gripmaxxer.datastore.WorkoutExerciseWithSetsEntity
import com.astrolabs.gripmaxxer.datastore.WorkoutFeedRow
import com.astrolabs.gripmaxxer.datastore.WorkoutSetEntity
import com.astrolabs.gripmaxxer.datastore.WorkoutSession
import com.astrolabs.gripmaxxer.datastore.WorkoutWithExercisesEntity
import com.astrolabs.gripmaxxer.reps.ExerciseMode
import com.astrolabs.gripmaxxer.service.AutoSetEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

interface RoutineRepository {
    val routinesFlow: Flow<List<Routine>>
    suspend fun createRoutine(name: String, exercises: List<RoutineExerciseTemplateInput>): Long
    suspend fun renameRoutine(routineId: Long, newName: String)
    suspend fun duplicateRoutine(routineId: Long): Long?
    suspend fun deleteRoutine(routineId: Long)
}

interface WorkoutRepository {
    val activeWorkoutFlow: Flow<ActiveWorkoutState?>
    val completedWorkoutFeedFlow: Flow<List<WorkoutFeedItem>>
    val calendarSummaryFlow: Flow<List<CalendarDaySummary>>
    val profileStatsFlow: Flow<ProfileStats>

    suspend fun startEmptyWorkout(title: String): Long
    suspend fun startWorkoutFromRoutine(routineId: Long): Long?
    suspend fun updateWorkoutTitle(workoutId: Long, title: String)
    suspend fun addExerciseToWorkout(
        workoutId: Long,
        exerciseName: String,
        mode: ExerciseMode?,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Float,
        restSeconds: Int,
    ): Long

    suspend fun addSet(exerciseId: Long): Long
    suspend fun updateSetWeight(setId: Long, weightKg: Float)
    suspend fun updateSetReps(setId: Long, reps: Int)
    suspend fun toggleSetDone(setId: Long, done: Boolean)
    suspend fun removeSet(setId: Long)
    suspend fun finishWorkout(workoutId: Long): Boolean
    suspend fun getCompletedWorkoutDetail(workoutId: Long): CompletedWorkoutDetail?
    suspend fun applyAutoSetEvent(event: AutoSetEvent, preferredExerciseId: Long?): AutoSetSuggestion?
    suspend fun importLegacySessionsIfNeeded(sessions: List<WorkoutSession>)
}

interface ActiveWorkoutRepository {
    val activeWorkoutFlow: Flow<ActiveWorkoutState?>
    suspend fun startEmptyWorkout(title: String): Long
    suspend fun startWorkoutFromRoutine(routineId: Long): Long?
    suspend fun finishWorkout(workoutId: Long): Boolean
    suspend fun applyAutoSetEvent(event: AutoSetEvent, preferredExerciseId: Long?): AutoSetSuggestion?
}

data class RoutineExerciseTemplateInput(
    val exerciseName: String,
    val mode: ExerciseMode?,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Float,
    val restSeconds: Int,
)

class RoomRoutineRepository(
    private val database: GripmaxxerDatabase,
) : RoutineRepository {
    private val routineDao = database.routineDao()

    override val routinesFlow: Flow<List<Routine>> = routineDao.observeRoutinesWithExercises()
        .map { routines ->
            routines.map { it.toDomain() }
        }

    override suspend fun createRoutine(name: String, exercises: List<RoutineExerciseTemplateInput>): Long {
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val routineId = routineDao.insertRoutine(
                RoutineEntity(
                    name = name,
                    createdAtMs = now,
                    updatedAtMs = now,
                )
            )
            routineDao.insertRoutineExercises(
                exercises.mapIndexed { index, exercise ->
                    RoutineExerciseEntity(
                        routineId = routineId,
                        position = index,
                        exerciseName = exercise.exerciseName,
                        modeName = exercise.mode?.name,
                        targetSets = exercise.targetSets.coerceAtLeast(1),
                        targetReps = exercise.targetReps.coerceAtLeast(0),
                        targetWeightKg = exercise.targetWeightKg.coerceAtLeast(0f),
                        restSeconds = exercise.restSeconds.coerceAtLeast(15),
                    )
                }
            )
            routineId
        }
    }

    override suspend fun renameRoutine(routineId: Long, newName: String) {
        val current = routineDao.getRoutineWithExercises(routineId)?.routine ?: return
        routineDao.updateRoutine(
            current.copy(
                name = newName,
                updatedAtMs = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun duplicateRoutine(routineId: Long): Long? {
        val source = routineDao.getRoutineWithExercises(routineId) ?: return null
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val duplicatedId = routineDao.insertRoutine(
                RoutineEntity(
                    name = "${source.routine.name} Copy",
                    createdAtMs = now,
                    updatedAtMs = now,
                )
            )
            routineDao.insertRoutineExercises(
                source.exercises.sortedBy { it.position }.map { exercise ->
                    exercise.copy(id = 0L, routineId = duplicatedId)
                }
            )
            duplicatedId
        }
    }

    override suspend fun deleteRoutine(routineId: Long) {
        routineDao.deleteRoutine(routineId)
    }

    private fun RoutineWithExercisesEntity.toDomain(): Routine {
        return Routine(
            id = routine.id,
            name = routine.name,
            createdAtMs = routine.createdAtMs,
            exercises = exercises
                .sortedBy { it.position }
                .map { exercise ->
                    RoutineExerciseTemplate(
                        id = exercise.id,
                        routineId = exercise.routineId,
                        position = exercise.position,
                        exerciseName = exercise.exerciseName,
                        mode = parseMode(exercise.modeName),
                        targetSets = exercise.targetSets,
                        targetReps = exercise.targetReps,
                        targetWeightKg = exercise.targetWeightKg,
                        restSeconds = exercise.restSeconds,
                    )
                },
        )
    }

    private fun parseMode(raw: String?): ExerciseMode? {
        if (raw.isNullOrBlank()) return null
        return runCatching { ExerciseMode.valueOf(raw) }.getOrNull()
    }
}

class RoomWorkoutRepository(
    private val database: GripmaxxerDatabase,
) : WorkoutRepository, ActiveWorkoutRepository {
    private val routineDao = database.routineDao()
    private val workoutDao = database.workoutDao()

    override val activeWorkoutFlow: Flow<ActiveWorkoutState?> = workoutDao.observeActiveWorkoutWithExercises()
        .map { workout -> workout?.toActiveDomain() }

    override val completedWorkoutFeedFlow: Flow<List<WorkoutFeedItem>> = workoutDao.observeCompletedWorkoutFeed()
        .map { rows -> rows.map { it.toDomain() } }

    override val calendarSummaryFlow: Flow<List<CalendarDaySummary>> = workoutDao.observeCalendarSummary()
        .map { rows -> rows.map { CalendarDaySummary(dayEpochMs = it.dayEpochMs, workoutCount = it.workoutCount) } }

    override val profileStatsFlow: Flow<ProfileStats> = combine(
        workoutDao.observeProfileBase(),
        workoutDao.observeCalendarSummary(),
        workoutDao.observeTopRecords(),
    ) { base, calendar, records ->
        base.toProfileStats(calendarRows = calendar, recordRows = records)
    }

    override suspend fun startEmptyWorkout(title: String): Long {
        val activeId = workoutDao.getActiveWorkoutId()
        if (activeId != null) return activeId
        return workoutDao.insertWorkout(
            WorkoutEntity(
                title = title,
                startedAtMs = System.currentTimeMillis(),
                completedAtMs = null,
                sourceRoutineId = null,
            )
        )
    }

    override suspend fun startWorkoutFromRoutine(routineId: Long): Long? {
        val activeId = workoutDao.getActiveWorkoutId()
        if (activeId != null) return activeId
        val routine = routineDao.getRoutineWithExercises(routineId) ?: return null
        return database.withTransaction {
            val workoutId = workoutDao.insertWorkout(
                WorkoutEntity(
                    title = routine.routine.name,
                    startedAtMs = System.currentTimeMillis(),
                    completedAtMs = null,
                    sourceRoutineId = routineId,
                )
            )
            routine.exercises.sortedBy { it.position }.forEachIndexed { index, routineExercise ->
                val exerciseId = workoutDao.insertWorkoutExercise(
                    WorkoutExerciseEntity(
                        workoutId = workoutId,
                        position = index,
                        exerciseName = routineExercise.exerciseName,
                        modeName = routineExercise.modeName,
                        restSeconds = routineExercise.restSeconds,
                    )
                )
                workoutDao.insertWorkoutSets(
                    (1..routineExercise.targetSets.coerceAtLeast(1)).map { setNum ->
                        WorkoutSetEntity(
                            workoutExerciseId = exerciseId,
                            setNumber = setNum,
                            weightKg = routineExercise.targetWeightKg,
                            reps = routineExercise.targetReps,
                            done = false,
                            durationMs = 0L,
                            completedAtMs = null,
                            autoTracked = false,
                        )
                    }
                )
            }
            workoutId
        }
    }

    override suspend fun updateWorkoutTitle(workoutId: Long, title: String) {
        val full = workoutDao.getWorkoutWithExercises(workoutId) ?: return
        workoutDao.updateWorkout(full.workout.copy(title = title))
    }

    override suspend fun addExerciseToWorkout(
        workoutId: Long,
        exerciseName: String,
        mode: ExerciseMode?,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Float,
        restSeconds: Int,
    ): Long {
        return database.withTransaction {
            val workout = workoutDao.getWorkoutWithExercises(workoutId) ?: return@withTransaction -1L
            val nextPosition = workout.exercises.maxOfOrNull { it.exercise.position }?.plus(1) ?: 0
            val exerciseId = workoutDao.insertWorkoutExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId,
                    position = nextPosition,
                    exerciseName = exerciseName,
                    modeName = mode?.name,
                    restSeconds = restSeconds.coerceAtLeast(15),
                )
            )
            workoutDao.insertWorkoutSets(
                (1..targetSets.coerceAtLeast(1)).map { setNum ->
                    WorkoutSetEntity(
                        workoutExerciseId = exerciseId,
                        setNumber = setNum,
                        weightKg = targetWeightKg.coerceAtLeast(0f),
                        reps = targetReps.coerceAtLeast(0),
                        done = false,
                        durationMs = 0L,
                        completedAtMs = null,
                        autoTracked = false,
                    )
                }
            )
            exerciseId
        }
    }

    override suspend fun addSet(exerciseId: Long): Long {
        val nextSetNumber = workoutDao.getMaxSetNumber(exerciseId) + 1
        return workoutDao.insertWorkoutSets(
            listOf(
                WorkoutSetEntity(
                    workoutExerciseId = exerciseId,
                    setNumber = nextSetNumber,
                    weightKg = 0f,
                    reps = 0,
                    done = false,
                    durationMs = 0L,
                    completedAtMs = null,
                    autoTracked = false,
                )
            )
        ).first()
    }

    override suspend fun updateSetWeight(setId: Long, weightKg: Float) {
        updateSet(setId) { it.copy(weightKg = weightKg.coerceAtLeast(0f)) }
    }

    override suspend fun updateSetReps(setId: Long, reps: Int) {
        updateSet(setId) { it.copy(reps = reps.coerceAtLeast(0)) }
    }

    override suspend fun toggleSetDone(setId: Long, done: Boolean) {
        updateSet(setId) {
            if (done) {
                it.copy(done = true, completedAtMs = System.currentTimeMillis())
            } else {
                it.copy(done = false, completedAtMs = null)
            }
        }
    }

    override suspend fun removeSet(setId: Long) {
        workoutDao.deleteWorkoutSetById(setId)
    }

    override suspend fun finishWorkout(workoutId: Long): Boolean {
        val full = workoutDao.getWorkoutWithExercises(workoutId) ?: return false
        val workout = full.workout
        if (workout.completedAtMs != null) return true
        workoutDao.updateWorkout(workout.copy(completedAtMs = System.currentTimeMillis()))
        return true
    }

    override suspend fun getCompletedWorkoutDetail(workoutId: Long): CompletedWorkoutDetail? {
        val full = workoutDao.getWorkoutWithExercises(workoutId) ?: return null
        val completedAt = full.workout.completedAtMs ?: return null
        return CompletedWorkoutDetail(
            workoutId = full.workout.id,
            title = full.workout.title,
            startedAtMs = full.workout.startedAtMs,
            completedAtMs = completedAt,
            durationMs = (completedAt - full.workout.startedAtMs).coerceAtLeast(0L),
            exercises = full.exercises
                .sortedBy { it.exercise.position }
                .map { exercise ->
                    WorkoutExerciseDetail(
                        exerciseName = exercise.exercise.exerciseName,
                        sets = mapSetStates(exercise),
                    )
                },
        )
    }

    override suspend fun applyAutoSetEvent(event: AutoSetEvent, preferredExerciseId: Long?): AutoSetSuggestion? {
        val active = workoutDao.getWorkoutWithExercises(workoutDao.getActiveWorkoutId() ?: return null) ?: return null
        val candidates = active.exercises
            .sortedBy { it.exercise.position }
            .filter { it.exercise.modeName == event.mode.name }
        if (candidates.isEmpty()) return null

        val targetExercise = candidates.firstOrNull { it.exercise.id == preferredExerciseId } ?: candidates.first()
        val firstPending = targetExercise.sets
            .sortedBy { it.setNumber }
            .firstOrNull { !it.done }

        if (firstPending != null) {
            workoutDao.updateWorkoutSet(
                firstPending.copy(
                    reps = event.reps,
                    durationMs = event.activeMs,
                    autoTracked = true,
                )
            )
            return AutoSetSuggestion(
                eventId = event.eventId,
                exerciseId = targetExercise.exercise.id,
                exerciseName = targetExercise.exercise.exerciseName,
                reps = event.reps,
                durationMs = event.activeMs,
                mode = event.mode,
            )
        }

        workoutDao.insertWorkoutSets(
            listOf(
                WorkoutSetEntity(
                    workoutExerciseId = targetExercise.exercise.id,
                    setNumber = (targetExercise.sets.maxOfOrNull { it.setNumber } ?: 0) + 1,
                    weightKg = 0f,
                    reps = event.reps,
                    done = false,
                    durationMs = event.activeMs,
                    completedAtMs = null,
                    autoTracked = true,
                )
            )
        )

        return AutoSetSuggestion(
            eventId = event.eventId,
            exerciseId = targetExercise.exercise.id,
            exerciseName = targetExercise.exercise.exerciseName,
            reps = event.reps,
            durationMs = event.activeMs,
            mode = event.mode,
        )
    }

    override suspend fun importLegacySessionsIfNeeded(sessions: List<WorkoutSession>) {
        if (sessions.isEmpty()) return
        val shouldImport = workoutDao.getCompletedWorkoutCount() == 0
        if (!shouldImport) return

        database.withTransaction {
            sessions.sortedBy { it.completedAtMs }.forEach { session ->
                val startedAt = (session.completedAtMs - session.activeMs).coerceAtLeast(0L)
                val workoutId = workoutDao.insertWorkout(
                    WorkoutEntity(
                        title = "Legacy ${session.mode.label}",
                        startedAtMs = startedAt,
                        completedAtMs = session.completedAtMs,
                        sourceRoutineId = null,
                    )
                )
                val exerciseId = workoutDao.insertWorkoutExercise(
                    WorkoutExerciseEntity(
                        workoutId = workoutId,
                        position = 0,
                        exerciseName = session.mode.label,
                        modeName = session.mode.name,
                        restSeconds = 90,
                    )
                )
                workoutDao.insertWorkoutSets(
                    listOf(
                        WorkoutSetEntity(
                            workoutExerciseId = exerciseId,
                            setNumber = 1,
                            weightKg = 0f,
                            reps = session.reps,
                            done = true,
                            durationMs = session.activeMs,
                            completedAtMs = session.completedAtMs,
                            autoTracked = true,
                        )
                    )
                )
            }
        }
    }

    private suspend fun updateSet(setId: Long, update: (WorkoutSetEntity) -> WorkoutSetEntity) {
        val active = workoutDao.getWorkoutWithExercises(workoutDao.getActiveWorkoutId() ?: return) ?: return
        val set = active.exercises
            .flatMap { it.sets }
            .firstOrNull { it.id == setId } ?: return
        workoutDao.updateWorkoutSet(update(set))
    }

    private fun RoutineWithExercisesEntity.toDomain(): Routine {
        return Routine(
            id = routine.id,
            name = routine.name,
            createdAtMs = routine.createdAtMs,
            exercises = exercises
                .sortedBy { it.position }
                .map { exercise ->
                    RoutineExerciseTemplate(
                        id = exercise.id,
                        routineId = exercise.routineId,
                        position = exercise.position,
                        exerciseName = exercise.exerciseName,
                        mode = parseMode(exercise.modeName),
                        targetSets = exercise.targetSets,
                        targetReps = exercise.targetReps,
                        targetWeightKg = exercise.targetWeightKg,
                        restSeconds = exercise.restSeconds,
                    )
                },
        )
    }

    private fun WorkoutWithExercisesEntity.toActiveDomain(): ActiveWorkoutState {
        val now = System.currentTimeMillis()
        return ActiveWorkoutState(
            id = workout.id,
            title = workout.title,
            startedAtMs = workout.startedAtMs,
            elapsedMs = (now - workout.startedAtMs).coerceAtLeast(0L),
            exercises = exercises
                .sortedBy { it.exercise.position }
                .map { exercise ->
                    WorkoutExerciseState(
                        id = exercise.exercise.id,
                        position = exercise.exercise.position,
                        exerciseName = exercise.exercise.exerciseName,
                        mode = parseMode(exercise.exercise.modeName),
                        restSeconds = exercise.exercise.restSeconds,
                        sets = mapSetStates(exercise),
                    )
                },
        )
    }

    private fun mapSetStates(exercise: WorkoutExerciseWithSetsEntity): List<WorkoutSetState> {
        var previous = "-"
        val exerciseMode = parseMode(exercise.exercise.modeName)
        return exercise.sets.sortedBy { it.setNumber }.map { set ->
            val state = WorkoutSetState(
                id = set.id,
                setNumber = set.setNumber,
                previous = previous,
                weightKg = set.weightKg,
                reps = set.reps,
                done = set.done,
                completedAtMs = set.completedAtMs,
                durationMs = set.durationMs,
                autoTracked = set.autoTracked,
            )
            if (set.done) {
                previous = if (exerciseMode.isHangMode()) {
                    formatHoldDuration(set.durationMs)
                } else {
                    "${set.weightKg.trimZero()}kg x ${set.reps}"
                }
            }
            state
        }
    }

    private fun WorkoutFeedRow.toDomain(): WorkoutFeedItem {
        return WorkoutFeedItem(
            workoutId = workoutId,
            title = title,
            completedAtMs = completedAtMs,
            durationMs = durationMs.coerceAtLeast(0L),
            exerciseCount = exerciseCount,
            totalVolumeKg = totalVolumeKg,
        )
    }

    private fun ProfileBaseRow.toProfileStats(
        calendarRows: List<CalendarDayRow>,
        recordRows: List<RecordRow>,
    ): ProfileStats {
        return ProfileStats(
            totalWorkouts = totalWorkouts,
            totalSets = totalSets,
            totalVolumeKg = totalVolumeKg,
            currentWeekCount = computeCurrentWeekCount(calendarRows),
            streakDays = computeStreakDays(calendarRows),
            maxReps = maxReps,
            maxActiveMs = maxActiveMs,
            records = recordRows.map { PersonalRecord(it.exerciseName, it.maxReps) },
        )
    }

    private fun computeCurrentWeekCount(rows: List<CalendarDayRow>): Int {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.minusDays(today.dayOfWeek.value.toLong() - 1L)
        return rows.count { row ->
            val date = row.dayEpochMs.toLocalDate(zone)
            !date.isBefore(start) && !date.isAfter(today)
        }
    }

    private fun computeStreakDays(rows: List<CalendarDayRow>): Int {
        if (rows.isEmpty()) return 0
        val zone = ZoneId.systemDefault()
        val completedDays = rows
            .map { it.dayEpochMs.toLocalDate(zone) }
            .toSet()

        var current = LocalDate.now(zone)
        if (!completedDays.contains(current)) {
            current = current.minusDays(1)
        }
        var streak = 0
        while (completedDays.contains(current)) {
            streak += 1
            current = current.minusDays(1)
        }
        return streak
    }

    private fun parseMode(raw: String?): ExerciseMode? {
        if (raw.isNullOrBlank()) return null
        return runCatching { ExerciseMode.valueOf(raw) }.getOrNull()
    }

    private fun Float.trimZero(): String {
        val rounded = (this * 10f).roundToInt() / 10f
        val asInt = rounded.toInt()
        return if (asInt.toFloat() == rounded) {
            asInt.toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", rounded)
        }
    }

    private fun Long.toLocalDate(zoneId: ZoneId): LocalDate {
        return java.time.Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    }

    private fun ExerciseMode?.isHangMode(): Boolean {
        return this == ExerciseMode.DEAD_HANG || this == ExerciseMode.ACTIVE_HANG
    }

    private fun formatHoldDuration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(java.util.Locale.US, "%d:%02d hold", minutes, seconds)
    }
}

class DefaultActiveWorkoutRepository(
    private val workoutRepository: WorkoutRepository,
) : ActiveWorkoutRepository {
    override val activeWorkoutFlow: Flow<ActiveWorkoutState?> = workoutRepository.activeWorkoutFlow

    override suspend fun startEmptyWorkout(title: String): Long {
        return workoutRepository.startEmptyWorkout(title)
    }

    override suspend fun startWorkoutFromRoutine(routineId: Long): Long? {
        return workoutRepository.startWorkoutFromRoutine(routineId)
    }

    override suspend fun finishWorkout(workoutId: Long): Boolean {
        return workoutRepository.finishWorkout(workoutId)
    }

    override suspend fun applyAutoSetEvent(event: AutoSetEvent, preferredExerciseId: Long?): AutoSetSuggestion? {
        return workoutRepository.applyAutoSetEvent(event, preferredExerciseId)
    }
}

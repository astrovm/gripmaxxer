package com.astrovm.gripmaxxer.data

import com.astrovm.gripmaxxer.tracking.Exercise
import com.astrovm.gripmaxxer.tracking.TrackedSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WorkoutSet(
    val id: Long,
    val exercise: Exercise,
    val reps: Int,
    val durationMs: Long,
    val completedAtMs: Long,
    val tracked: Boolean,
)

data class Workout(
    val id: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val exercise: Exercise,
    /** Oldest first. */
    val sets: List<WorkoutSet>,
) {
    val durationMs: Long? get() = endedAtMs?.let { it - startedAtMs }

    /** Exercises in the order they were first done. */
    val exercises: List<Exercise> get() = sets.map { it.exercise }.distinct()
}

/** Best results for one exercise across all finished workouts. */
data class ExerciseStats(
    val exercise: Exercise,
    val sets: Int,
    val bestReps: Int,
    val totalReps: Int,
    val bestHoldMs: Long,
    val totalHoldMs: Long,
)

class WorkoutRepository(private val dao: WorkoutDao, private val clock: () -> Long = System::currentTimeMillis) {

    val active: Flow<Workout?> = dao.observeActive().map { it?.toWorkout() }

    val history: Flow<List<Workout>> = dao.observeFinished().map { rows -> rows.mapNotNull { it.toWorkout() } }

    val stats: Flow<List<ExerciseStats>> = history.map(::statsFor)

    fun workout(id: Long): Flow<Workout?> = dao.observe(id).map { it?.toWorkout() }

    /** Starts a workout, or returns the one already running. */
    suspend fun start(exercise: Exercise): Long {
        dao.active()?.let { return it.id }
        return dao.insert(WorkoutEntity(startedAtMs = clock(), endedAtMs = null, exercise = exercise.name))
    }

    suspend fun switchExercise(workoutId: Long, exercise: Exercise) = dao.setExercise(workoutId, exercise.name)

    /** Finishes a workout. A workout with no sets is thrown away. Returns true if it was kept. */
    suspend fun finish(workoutId: Long): Boolean {
        if (dao.setCount(workoutId) == 0) {
            dao.delete(workoutId)
            return false
        }
        dao.end(workoutId, clock())
        return true
    }

    suspend fun delete(workoutId: Long) = dao.delete(workoutId)

    suspend fun addTrackedSet(workoutId: Long, set: TrackedSet) {
        dao.insert(
            SetEntity(
                workoutId = workoutId,
                exercise = set.exercise.name,
                reps = set.reps,
                durationMs = set.durationMs,
                completedAtMs = set.endedAtMs,
                tracked = true,
            ),
        )
    }

    suspend fun addSet(workoutId: Long, exercise: Exercise, reps: Int, durationMs: Long) {
        dao.insert(
            SetEntity(
                workoutId = workoutId,
                exercise = exercise.name,
                reps = reps,
                durationMs = durationMs,
                completedAtMs = clock(),
                tracked = false,
            ),
        )
    }

    suspend fun updateSet(setId: Long, reps: Int, durationMs: Long) = dao.updateSet(setId, reps, durationMs)

    suspend fun deleteSet(setId: Long) = dao.deleteSet(setId)
}

private fun WorkoutWithSets.toWorkout(): Workout? {
    val exercise = Exercise.fromName(workout.exercise) ?: return null
    return Workout(
        id = workout.id,
        startedAtMs = workout.startedAtMs,
        endedAtMs = workout.endedAtMs,
        exercise = exercise,
        sets = sets.mapNotNull { it.toSet() }.sortedBy { it.completedAtMs },
    )
}

private fun SetEntity.toSet(): WorkoutSet? {
    val exercise = Exercise.fromName(exercise) ?: return null
    return WorkoutSet(id, exercise, reps, durationMs, completedAtMs, tracked)
}

internal fun statsFor(workouts: List<Workout>): List<ExerciseStats> =
    workouts.flatMap { it.sets }
        .groupBy { it.exercise }
        .map { (exercise, sets) ->
            ExerciseStats(
                exercise = exercise,
                sets = sets.size,
                bestReps = sets.maxOf { it.reps },
                totalReps = sets.sumOf { it.reps },
                bestHoldMs = sets.maxOf { it.durationMs },
                totalHoldMs = sets.sumOf { it.durationMs },
            )
        }
        .sortedBy { it.exercise.ordinal }

package com.astrolabs.gripmaxxer.workout

import com.astrolabs.gripmaxxer.reps.ExerciseMode

data class Routine(
    val id: Long,
    val name: String,
    val createdAtMs: Long,
    val exercises: List<RoutineExerciseTemplate>,
)

data class RoutineExerciseTemplate(
    val id: Long,
    val routineId: Long,
    val position: Int,
    val exerciseName: String,
    val mode: ExerciseMode?,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Float,
    val restSeconds: Int,
)

data class WorkoutSetState(
    val id: Long,
    val setNumber: Int,
    val previous: String,
    val weightKg: Float,
    val reps: Int,
    val done: Boolean,
    val completedAtMs: Long?,
    val durationMs: Long,
    val autoTracked: Boolean,
)

data class WorkoutExerciseState(
    val id: Long,
    val position: Int,
    val exerciseName: String,
    val mode: ExerciseMode?,
    val restSeconds: Int,
    val sets: List<WorkoutSetState>,
)

data class AutoSetSuggestion(
    val eventId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val reps: Int,
    val durationMs: Long,
    val mode: ExerciseMode,
)

data class ActiveWorkoutState(
    val id: Long,
    val title: String,
    val startedAtMs: Long,
    val elapsedMs: Long,
    val exercises: List<WorkoutExerciseState>,
    val pendingSuggestion: AutoSetSuggestion? = null,
)

data class WorkoutFeedItem(
    val workoutId: Long,
    val title: String,
    val completedAtMs: Long,
    val durationMs: Long,
    val exerciseCount: Int,
    val totalVolumeKg: Float,
)

data class CalendarDaySummary(
    val dayEpochMs: Long,
    val workoutCount: Int,
)

data class PersonalRecord(
    val exerciseName: String,
    val maxReps: Int,
)

data class ProfileStats(
    val totalWorkouts: Int,
    val totalSets: Int,
    val totalVolumeKg: Float,
    val currentWeekCount: Int,
    val streakDays: Int,
    val maxReps: Int,
    val maxActiveMs: Long,
    val records: List<PersonalRecord>,
)

data class WorkoutExerciseDetail(
    val exerciseName: String,
    val sets: List<WorkoutSetState>,
)

data class CompletedWorkoutDetail(
    val workoutId: Long,
    val title: String,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val durationMs: Long,
    val exercises: List<WorkoutExerciseDetail>,
)

data class ExerciseTemplate(
    val name: String,
    val mode: ExerciseMode?,
)

val DefaultExerciseLibrary = listOf(
    ExerciseTemplate("Pull-up", ExerciseMode.PULL_UP),
    ExerciseTemplate("Chin-up", ExerciseMode.PULL_UP),
    ExerciseTemplate("Push-up", ExerciseMode.PUSH_UP),
    ExerciseTemplate("Squat", ExerciseMode.SQUAT),
    ExerciseTemplate("Bench Press", ExerciseMode.BENCH_PRESS),
    ExerciseTemplate("Dips", ExerciseMode.DIP),
    ExerciseTemplate("Overhead Press", null),
    ExerciseTemplate("Bent Over Row", null),
    ExerciseTemplate("Romanian Deadlift", null),
    ExerciseTemplate("Hip Thrust", null),
    ExerciseTemplate("Lateral Raise", null),
    ExerciseTemplate("Bicep Curl", null),
    ExerciseTemplate("Face Pull", null),
    ExerciseTemplate("Ab Wheel", null),
    ExerciseTemplate("Stretching", null),
)

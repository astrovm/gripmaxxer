package com.astrolabs.gripmaxxer.workout

import com.astrolabs.gripmaxxer.reps.ExerciseMode

data class WorkoutSetState(
    val id: Long,
    val setNumber: Int,
    val reps: Int,
    val durationMs: Long,
    val completedAtMs: Long,
    val autoTracked: Boolean,
)

data class ActiveWorkoutState(
    val id: Long,
    val title: String,
    val mode: ExerciseMode,
    val startedAtMs: Long,
    val elapsedMs: Long,
    val elapsedComputedAtMs: Long,
    val paused: Boolean,
    val sets: List<WorkoutSetState>,
)

data class WorkoutFeedItem(
    val workoutId: Long,
    val title: String,
    val mode: ExerciseMode,
    val completedAtMs: Long,
    val durationMs: Long,
    val setCount: Int,
)

data class CalendarDaySummary(
    val dayEpochMs: Long,
    val workoutCount: Int,
)

data class ProfileStats(
    val totalWorkouts: Int,
    val maxReps: Int,
    val maxHoldMs: Long,
)

data class CompletedWorkoutDetail(
    val workoutId: Long,
    val title: String,
    val mode: ExerciseMode,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val durationMs: Long,
    val sets: List<WorkoutSetState>,
)

val CameraTrackableModes = listOf(
    ExerciseMode.DEAD_HANG,
    ExerciseMode.ACTIVE_HANG,
    ExerciseMode.PULL_UP,
    ExerciseMode.PUSH_UP,
    ExerciseMode.SQUAT,
    ExerciseMode.BENCH_PRESS,
    ExerciseMode.DIP,
)

package com.astrolabs.gripmaxxer.datastore

import com.astrolabs.gripmaxxer.reps.ExerciseMode

data class WorkoutSession(
    val completedAtMs: Long,
    val mode: ExerciseMode,
    val reps: Int,
    val activeMs: Long,
)

data class WorkoutHistory(
    val maxReps: Int = 0,
    val maxActiveMs: Long = 0L,
    val sessions: List<WorkoutSession> = emptyList(),
)

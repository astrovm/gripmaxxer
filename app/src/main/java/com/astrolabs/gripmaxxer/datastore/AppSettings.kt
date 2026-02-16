package com.astrolabs.gripmaxxer.datastore

import com.astrolabs.gripmaxxer.reps.ExerciseMode

data class AppSettings(
    val overlayEnabled: Boolean = true,
    val mediaControlEnabled: Boolean = true,
    val selectedExerciseMode: ExerciseMode = ExerciseMode.PULL_UP,
    val poseModeAccurate: Boolean = false,
    val wristShoulderMargin: Float = 0.08f,
    val missingPoseTimeoutMs: Long = 300L,
    val marginUp: Float = 0.05f,
    val marginDown: Float = 0.03f,
    val elbowUpAngle: Float = 110f,
    val elbowDownAngle: Float = 155f,
    val stableMs: Long = 250L,
    val minRepIntervalMs: Long = 600L,
)

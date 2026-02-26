package com.astrovm.gripmaxxer.datastore

import com.astrovm.gripmaxxer.reps.ExerciseMode

data class AppSettings(
    val overlayEnabled: Boolean = true,
    val mediaControlEnabled: Boolean = true,
    val repSoundEnabled: Boolean = true,
    val voiceCueEnabled: Boolean = true,
    val colorPalette: ColorPalette = ColorPalette.BLACK_WHITE,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val selectedExerciseMode: ExerciseMode = ExerciseMode.DEAD_HANG,
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

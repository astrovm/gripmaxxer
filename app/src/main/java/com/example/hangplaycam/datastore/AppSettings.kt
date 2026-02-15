package com.example.hangplaycam.datastore

data class AppSettings(
    val overlayEnabled: Boolean = true,
    val poseModeAccurate: Boolean = false,
    val wristShoulderMargin: Float = 0.06f,
    val missingPoseTimeoutMs: Long = 300L,
    val marginUp: Float = 0.05f,
    val marginDown: Float = 0.03f,
    val elbowUpAngle: Float = 110f,
    val elbowDownAngle: Float = 155f,
    val stableMs: Long = 250L,
    val minRepIntervalMs: Long = 600L,
)

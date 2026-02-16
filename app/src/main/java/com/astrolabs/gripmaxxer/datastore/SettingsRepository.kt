package com.astrolabs.gripmaxxer.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astrolabs.gripmaxxer.reps.ExerciseMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gripDataStore by preferencesDataStore(name = "gripmaxxer_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val overlayEnabled = booleanPreferencesKey("overlayEnabled")
        val mediaControlEnabled = booleanPreferencesKey("mediaControlEnabled")
        val selectedExerciseMode = stringPreferencesKey("selectedExerciseMode")
        val poseModeAccurate = booleanPreferencesKey("poseModeAccurate")
        val wristShoulderMargin = floatPreferencesKey("wristShoulderMargin")
        val missingPoseTimeoutMs = longPreferencesKey("missingPoseTimeoutMs")
        val marginUp = floatPreferencesKey("marginUp")
        val marginDown = floatPreferencesKey("marginDown")
        val elbowUpAngle = floatPreferencesKey("elbowUpAngle")
        val elbowDownAngle = floatPreferencesKey("elbowDownAngle")
        val stableMs = longPreferencesKey("stableMs")
        val minRepIntervalMs = longPreferencesKey("minRepIntervalMs")
    }

    val settingsFlow: Flow<AppSettings> = context.gripDataStore.data.map { preferences ->
        preferences.toAppSettings()
    }

    private fun Preferences.toAppSettings(): AppSettings {
        return AppSettings(
            overlayEnabled = this[Keys.overlayEnabled] ?: true,
            mediaControlEnabled = this[Keys.mediaControlEnabled] ?: true,
            selectedExerciseMode = parseExerciseMode(this[Keys.selectedExerciseMode]),
            poseModeAccurate = this[Keys.poseModeAccurate] ?: false,
            wristShoulderMargin = this[Keys.wristShoulderMargin] ?: 0.08f,
            missingPoseTimeoutMs = this[Keys.missingPoseTimeoutMs] ?: 300L,
            marginUp = this[Keys.marginUp] ?: 0.05f,
            marginDown = this[Keys.marginDown] ?: 0.03f,
            elbowUpAngle = this[Keys.elbowUpAngle] ?: 110f,
            elbowDownAngle = this[Keys.elbowDownAngle] ?: 155f,
            stableMs = this[Keys.stableMs] ?: 250L,
            minRepIntervalMs = this[Keys.minRepIntervalMs] ?: 600L,
        )
    }

    suspend fun setOverlayEnabled(value: Boolean) = editBool(Keys.overlayEnabled, value)
    suspend fun setMediaControlEnabled(value: Boolean) = editBool(Keys.mediaControlEnabled, value)
    suspend fun setSelectedExerciseMode(value: ExerciseMode) = editString(Keys.selectedExerciseMode, value.name)
    suspend fun setPoseModeAccurate(value: Boolean) = editBool(Keys.poseModeAccurate, value)
    suspend fun setWristShoulderMargin(value: Float) = editFloat(Keys.wristShoulderMargin, value)
    suspend fun setMissingPoseTimeoutMs(value: Long) = editLong(Keys.missingPoseTimeoutMs, value)
    suspend fun setMarginUp(value: Float) = editFloat(Keys.marginUp, value)
    suspend fun setMarginDown(value: Float) = editFloat(Keys.marginDown, value)
    suspend fun setElbowUpAngle(value: Float) = editFloat(Keys.elbowUpAngle, value)
    suspend fun setElbowDownAngle(value: Float) = editFloat(Keys.elbowDownAngle, value)
    suspend fun setStableMs(value: Long) = editLong(Keys.stableMs, value)
    suspend fun setMinRepIntervalMs(value: Long) = editLong(Keys.minRepIntervalMs, value)

    private suspend fun editBool(key: Preferences.Key<Boolean>, value: Boolean) {
        context.gripDataStore.edit { it[key] = value }
    }

    private suspend fun editFloat(key: Preferences.Key<Float>, value: Float) {
        context.gripDataStore.edit { it[key] = value }
    }

    private suspend fun editLong(key: Preferences.Key<Long>, value: Long) {
        context.gripDataStore.edit { it[key] = value }
    }

    private suspend fun editString(key: Preferences.Key<String>, value: String) {
        context.gripDataStore.edit { it[key] = value }
    }

    private fun parseExerciseMode(raw: String?): ExerciseMode {
        if (raw.isNullOrBlank()) return ExerciseMode.PULL_UP
        return runCatching { ExerciseMode.valueOf(raw) }
            .getOrElse { ExerciseMode.PULL_UP }
    }
}

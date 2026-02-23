package com.astrolabs.gripmaxxer.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astrolabs.gripmaxxer.reps.ExerciseMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gripDataStore by preferencesDataStore(name = "gripmaxxer_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val overlayEnabled = booleanPreferencesKey("overlayEnabled")
        val mediaControlEnabled = booleanPreferencesKey("mediaControlEnabled")
        val repSoundEnabled = booleanPreferencesKey("repSoundEnabled")
        val voiceCueEnabled = booleanPreferencesKey("voiceCueEnabled")
        val colorPalette = stringPreferencesKey("colorPalette")
        val weightUnit = stringPreferencesKey("weightUnit")
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
        val historyMaxReps = intPreferencesKey("historyMaxReps")
        val historyMaxActiveMs = longPreferencesKey("historyMaxActiveMs")
        val historySessions = stringPreferencesKey("historySessions")
        val roomHistoryMigrated = booleanPreferencesKey("roomHistoryMigrated")
    }

    val settingsFlow: Flow<AppSettings> = context.gripDataStore.data.map { preferences ->
        preferences.toAppSettings()
    }

    val historyFlow: Flow<WorkoutHistory> = context.gripDataStore.data.map { preferences ->
        preferences.toWorkoutHistory()
    }

    private fun Preferences.toAppSettings(): AppSettings {
        return AppSettings(
            overlayEnabled = this[Keys.overlayEnabled] ?: true,
            mediaControlEnabled = this[Keys.mediaControlEnabled] ?: true,
            repSoundEnabled = this[Keys.repSoundEnabled] ?: true,
            voiceCueEnabled = this[Keys.voiceCueEnabled] ?: false,
            colorPalette = parseColorPalette(this[Keys.colorPalette]),
            weightUnit = parseWeightUnit(this[Keys.weightUnit]),
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

    private fun Preferences.toWorkoutHistory(): WorkoutHistory {
        return WorkoutHistory(
            maxReps = this[Keys.historyMaxReps] ?: 0,
            maxActiveMs = this[Keys.historyMaxActiveMs] ?: 0L,
            sessions = parseSessions(this[Keys.historySessions]).sortedByDescending { it.completedAtMs },
        )
    }

    suspend fun setOverlayEnabled(value: Boolean) = editBool(Keys.overlayEnabled, value)
    suspend fun setMediaControlEnabled(value: Boolean) = editBool(Keys.mediaControlEnabled, value)
    suspend fun setRepSoundEnabled(value: Boolean) = editBool(Keys.repSoundEnabled, value)
    suspend fun setVoiceCueEnabled(value: Boolean) = editBool(Keys.voiceCueEnabled, value)
    suspend fun setColorPalette(value: ColorPalette) = editString(Keys.colorPalette, value.name)
    suspend fun setWeightUnit(value: WeightUnit) = editString(Keys.weightUnit, value.name)
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

    suspend fun recordWorkoutSession(session: WorkoutSession) {
        context.gripDataStore.edit { prefs ->
            val existing = parseSessions(prefs[Keys.historySessions]).toMutableList()
            existing.add(session)
            val trimmed = existing
                .sortedByDescending { it.completedAtMs }
                .take(MAX_HISTORY_SESSIONS)
            prefs[Keys.historySessions] = serializeSessions(trimmed)
            prefs[Keys.historyMaxReps] = maxOf(prefs[Keys.historyMaxReps] ?: 0, session.reps)
            prefs[Keys.historyMaxActiveMs] = maxOf(prefs[Keys.historyMaxActiveMs] ?: 0L, session.activeMs)
        }
    }

    suspend fun isRoomHistoryMigrated(): Boolean {
        return context.gripDataStore.data.map { prefs ->
            prefs[Keys.roomHistoryMigrated] ?: false
        }.first()
    }

    suspend fun markRoomHistoryMigrated() {
        editBool(Keys.roomHistoryMigrated, true)
    }

    suspend fun readLegacySessions(): List<WorkoutSession> {
        return context.gripDataStore.data.map { prefs ->
            parseSessions(prefs[Keys.historySessions]).sortedBy { it.completedAtMs }
        }.first()
    }

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
        if (raw.isNullOrBlank()) return ExerciseMode.DEAD_HANG
        return runCatching { ExerciseMode.valueOf(raw) }
            .getOrElse { ExerciseMode.DEAD_HANG }
    }

    private fun parseColorPalette(raw: String?): ColorPalette {
        if (raw.isNullOrBlank()) return ColorPalette.BLACK_WHITE
        return runCatching { ColorPalette.valueOf(raw) }
            .getOrElse { ColorPalette.BLACK_WHITE }
    }

    private fun parseWeightUnit(raw: String?): WeightUnit {
        if (raw.isNullOrBlank()) return WeightUnit.KG
        return runCatching { WeightUnit.valueOf(raw) }
            .getOrElse { WeightUnit.KG }
    }

    private fun parseSessions(raw: String?): List<WorkoutSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .split(SESSION_SEPARATOR)
            .mapNotNull { parseSession(it) }
    }

    private fun parseSession(raw: String): WorkoutSession? {
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size != 4) return null
        val completedAtMs = parts[0].toLongOrNull() ?: return null
        val mode = parseExerciseMode(parts[1])
        val reps = parts[2].toIntOrNull() ?: return null
        val activeMs = parts[3].toLongOrNull() ?: return null
        return WorkoutSession(
            completedAtMs = completedAtMs,
            mode = mode,
            reps = reps.coerceAtLeast(0),
            activeMs = activeMs.coerceAtLeast(0L),
        )
    }

    private fun serializeSessions(sessions: List<WorkoutSession>): String {
        return sessions.joinToString(SESSION_SEPARATOR) { session ->
            listOf(
                session.completedAtMs.toString(),
                session.mode.name,
                session.reps.toString(),
                session.activeMs.toString(),
            ).joinToString(FIELD_SEPARATOR)
        }
    }

    companion object {
        private const val SESSION_SEPARATOR = ";;"
        private const val FIELD_SEPARATOR = "|"
        private const val MAX_HISTORY_SESSIONS = 80
    }
}

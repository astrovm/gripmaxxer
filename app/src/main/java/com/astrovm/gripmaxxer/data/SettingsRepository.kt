package com.astrovm.gripmaxxer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.astrovm.gripmaxxer.tracking.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Accent { WHITE, PINK, BLUE, RED, GREEN, PURPLE, ORANGE }

data class Settings(
    /** Play media while you're on the bar or in a set, pause when you stop. */
    val mediaControl: Boolean = true,
    /** Floating timer over other apps. */
    val overlay: Boolean = true,
    /** Beep on every rep. */
    val repSound: Boolean = true,
    /** Read the hold time out loud every 10 seconds. */
    val voiceCues: Boolean = true,
    val accent: Accent = Accent.WHITE,
    val lastExercise: Exercise = Exercise.DEAD_HANG,
)

// Key names match older versions of the app so existing choices carry over.
private val MEDIA = booleanPreferencesKey("mediaControlEnabled")
private val OVERLAY = booleanPreferencesKey("overlayEnabled")
private val REP_SOUND = booleanPreferencesKey("repSoundEnabled")
private val VOICE = booleanPreferencesKey("voiceCueEnabled")
private val ACCENT = stringPreferencesKey("accent")
private val OLD_PALETTE = stringPreferencesKey("colorPalette")
private val EXERCISE = stringPreferencesKey("selectedExerciseMode")

/** App settings, stored with DataStore. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun setMediaControl(on: Boolean) = set(MEDIA, on)
    suspend fun setOverlay(on: Boolean) = set(OVERLAY, on)
    suspend fun setRepSound(on: Boolean) = set(REP_SOUND, on)
    suspend fun setVoiceCues(on: Boolean) = set(VOICE, on)
    suspend fun setAccent(accent: Accent) = set(ACCENT, accent.name)
    suspend fun setLastExercise(exercise: Exercise) = set(EXERCISE, exercise.name)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            mediaControl = this[MEDIA] ?: defaults.mediaControl,
            overlay = this[OVERLAY] ?: defaults.overlay,
            repSound = this[REP_SOUND] ?: defaults.repSound,
            voiceCues = this[VOICE] ?: defaults.voiceCues,
            accent = parseAccent(this[ACCENT] ?: this[OLD_PALETTE]) ?: defaults.accent,
            lastExercise = Exercise.fromName(this[EXERCISE]) ?: defaults.lastExercise,
        )
    }

    /** Older versions stored palettes like "BLACK_PINK". */
    private fun parseAccent(name: String?): Accent? {
        val plain = name?.removePrefix("BLACK_") ?: return null
        return Accent.entries.firstOrNull { it.name == plain }
    }
}

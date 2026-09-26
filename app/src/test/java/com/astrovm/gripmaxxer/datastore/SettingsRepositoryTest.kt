package com.astrovm.gripmaxxer.datastore

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.reps.ExerciseMode
import com.astrovm.gripmaxxer.testutil.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        SettingsStore.clear(context)
        repository = SettingsRepository(context)
    }

    @Test
    fun `defaults are returned for an empty store`() = runBlocking {
        assertEquals(AppSettings(), repository.settingsFlow.first())
        assertEquals(WorkoutHistory(), repository.historyFlow.first())
        assertFalse(repository.isRoomHistoryMigrated())
        assertTrue(repository.readLegacySessions().isEmpty())
    }

    @Test
    fun `every setting round trips`() = runBlocking {
        repository.setOverlayEnabled(false)
        repository.setMediaControlEnabled(false)
        repository.setRepSoundEnabled(false)
        repository.setVoiceCueEnabled(false)
        repository.setColorPalette(ColorPalette.WINDOWS_98)
        repository.setWeightUnit(WeightUnit.LB)
        repository.setSelectedExerciseMode(ExerciseMode.SQUAT)
        repository.setPoseModeAccurate(true)
        repository.setWristShoulderMargin(0.1f)
        repository.setMissingPoseTimeoutMs(500L)
        repository.setMarginUp(0.07f)
        repository.setMarginDown(0.04f)
        repository.setElbowUpAngle(100f)
        repository.setElbowDownAngle(160f)
        repository.setStableMs(300L)
        repository.setMinRepIntervalMs(700L)

        assertEquals(
            AppSettings(
                overlayEnabled = false,
                mediaControlEnabled = false,
                repSoundEnabled = false,
                voiceCueEnabled = false,
                colorPalette = ColorPalette.WINDOWS_98,
                weightUnit = WeightUnit.LB,
                selectedExerciseMode = ExerciseMode.SQUAT,
                poseModeAccurate = true,
                wristShoulderMargin = 0.1f,
                missingPoseTimeoutMs = 500L,
                marginUp = 0.07f,
                marginDown = 0.04f,
                elbowUpAngle = 100f,
                elbowDownAngle = 160f,
                stableMs = 300L,
                minRepIntervalMs = 700L,
            ),
            repository.settingsFlow.first(),
        )
        assertEquals("lb", WeightUnit.LB.label)
    }

    @Test
    fun `invalid stored enums fall back to defaults`() = runBlocking {
        SettingsStore.edit(context) {
            it[stringPreferencesKey("colorPalette")] = "NOPE"
            it[stringPreferencesKey("weightUnit")] = "STONE"
            it[stringPreferencesKey("selectedExerciseMode")] = "PLANK"
        }
        var settings = repository.settingsFlow.first()
        assertEquals(ColorPalette.BLACK_WHITE, settings.colorPalette)
        assertEquals(WeightUnit.KG, settings.weightUnit)
        assertEquals(ExerciseMode.DEAD_HANG, settings.selectedExerciseMode)

        SettingsStore.edit(context) {
            it[stringPreferencesKey("colorPalette")] = " "
            it[stringPreferencesKey("weightUnit")] = ""
            it[stringPreferencesKey("selectedExerciseMode")] = ""
        }
        settings = repository.settingsFlow.first()
        assertEquals(ColorPalette.BLACK_WHITE, settings.colorPalette)
        assertEquals(WeightUnit.KG, settings.weightUnit)
        assertEquals(ExerciseMode.DEAD_HANG, settings.selectedExerciseMode)
    }

    @Test
    fun `workout sessions are recorded newest first and trimmed`() = runBlocking {
        repository.recordWorkoutSession(WorkoutSession(completedAtMs = 1L, mode = ExerciseMode.PULL_UP, reps = 5, activeMs = 10L))
        repository.recordWorkoutSession(WorkoutSession(completedAtMs = 2L, mode = ExerciseMode.DIP, reps = 3, activeMs = 50L))
        val history = repository.historyFlow.first()
        assertEquals(5, history.maxReps)
        assertEquals(50L, history.maxActiveMs)
        assertEquals(listOf(2L, 1L), history.sessions.map { it.completedAtMs })
        assertEquals(listOf(1L, 2L), repository.readLegacySessions().map { it.completedAtMs })

        repeat(85) { index ->
            repository.recordWorkoutSession(
                WorkoutSession(completedAtMs = 100L + index, mode = ExerciseMode.SQUAT, reps = 1, activeMs = 1L)
            )
        }
        val trimmed = repository.historyFlow.first()
        assertEquals(80, trimmed.sessions.size)
        assertEquals(184L, trimmed.sessions.first().completedAtMs)
    }

    @Test
    fun `malformed legacy sessions are skipped and values clamped`() = runBlocking {
        SettingsStore.edit(context) {
            it[stringPreferencesKey("historySessions")] = listOf(
                "1|DEAD_HANG|3",
                "x|DEAD_HANG|3|4",
                "1|NOPE|3|4",
                "1|DEAD_HANG|x|4",
                "1|DEAD_HANG|3|x",
                "5|PULL_UP|-2|-7",
            ).joinToString(";;")
        }
        val sessions = repository.readLegacySessions()
        assertEquals(listOf(WorkoutSession(5L, ExerciseMode.PULL_UP, 0, 0L)), sessions)
    }

    @Test
    fun `room migration flag can be set`() = runBlocking {
        repository.markRoomHistoryMigrated()
        assertTrue(repository.isRoomHistoryMigrated())
    }

    @Test
    fun `purging legacy data normalizes mode and recomputes maxima`() = runBlocking {
        SettingsStore.edit(context) {
            it[stringPreferencesKey("selectedExerciseMode")] = "PLANK"
            it[stringPreferencesKey("historySessions")] = "9|PLANK|100|100000;;7|DIP|4|2000;;8|SQUAT|6|1000"
            it[intPreferencesKey("historyMaxReps")] = 100
            it[longPreferencesKey("historyMaxActiveMs")] = 100_000L
        }
        repository.purgeLegacyExerciseData()
        val history = repository.historyFlow.first()
        assertEquals(listOf(8L, 7L), history.sessions.map { it.completedAtMs })
        assertEquals(6, history.maxReps)
        assertEquals(2_000L, history.maxActiveMs)
        assertEquals(ExerciseMode.DEAD_HANG, repository.settingsFlow.first().selectedExerciseMode)

        SettingsStore.edit(context) {
            it[stringPreferencesKey("selectedExerciseMode")] = "DIP"
            it[stringPreferencesKey("historySessions")] = "9|PLANK|100|100000"
        }
        repository.purgeLegacyExerciseData()
        assertEquals(WorkoutHistory(), repository.historyFlow.first())
        assertEquals(ExerciseMode.DIP, repository.settingsFlow.first().selectedExerciseMode)
    }

    @Test
    fun `palette labels are human readable`() {
        assertEquals("Windows 98", ColorPalette.WINDOWS_98.label)
        assertEquals(8, ColorPalette.entries.size)
    }
}

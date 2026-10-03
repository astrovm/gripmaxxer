package com.astrovm.gripmaxxer.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.astrovm.gripmaxxer.tracking.Exercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("settings.preferences_pb") }
    }
    private val repo by lazy { SettingsRepository(store) }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaultsTurnEverythingOn() = runBlocking {
        assertEquals(Settings(), repo.settings.first())
    }

    @Test
    fun changesAreSaved() = runBlocking {
        repo.setMediaControl(false)
        repo.setOverlay(false)
        repo.setRepSound(false)
        repo.setVoiceCues(false)
        repo.setAccent(Accent.GREEN)
        repo.setLastExercise(Exercise.SQUAT)
        assertEquals(
            Settings(false, false, false, false, Accent.GREEN, Exercise.SQUAT),
            repo.settings.first(),
        )
    }

    @Test
    fun oldPaletteBecomesTheAccent() = runBlocking {
        store.edit { it[stringPreferencesKey("colorPalette")] = "BLACK_PINK" }
        assertEquals(Accent.PINK, repo.settings.first().accent)
        store.edit { it[stringPreferencesKey("colorPalette")] = "WINDOWS_98" }
        assertEquals(Accent.WHITE, repo.settings.first().accent)
        repo.setAccent(Accent.BLUE)
        assertEquals(Accent.BLUE, repo.settings.first().accent)
    }

    @Test
    fun removedExerciseFallsBack() = runBlocking {
        store.edit { it[stringPreferencesKey("selectedExerciseMode")] = "PLANK" }
        assertEquals(Exercise.DEAD_HANG, repo.settings.first().lastExercise)
    }
}

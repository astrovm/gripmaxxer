package com.astrovm.gripmaxxer.ui

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.datastore.*
import com.astrovm.gripmaxxer.reps.ExerciseMode
import com.astrovm.gripmaxxer.service.*
import com.astrovm.gripmaxxer.testutil.SettingsStore
import io.mockk.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var model: MainViewModel

    @Before fun setup() {
        SettingsStore.clear(app)
        runBlocking(Dispatchers.IO) { GripmaxxerDatabase.getInstance(app).clearAllTables() }
        mockkObject(HangCamService.Companion)
        every { HangCamService.start(any()) } just Runs
        every { HangCamService.stop(any()) } just Runs
        model = MainViewModel(app)
        store.put("test", model)
        scope.launch { model.uiState.collect {} }
        await { model.uiState.value.settings == AppSettings() }
    }
    @After fun cleanup() {
        store.clear()
        scope.cancel()
        unmockkAll()
        MonitoringStateStore.reset()
        DebugPreviewStore.clear()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        } while (System.nanoTime() < deadline)
        fail("State did not settle: ${model.uiState.value}")
    }
    private fun emit(mode: ExerciseMode, reps: Int = 5, duration: Long = 65000) {
        AutoSetEventStore.emit(mode, reps, duration, System.currentTimeMillis())
    }

    @Test fun settingsPermissionsAndNavigationReachUiState() {
        model.selectTab(RootTab.PROFILE)
        model.setOverlayEnabled(true)
        model.setMediaControlEnabled(false)
        model.setRepSoundEnabled(false)
        model.setVoiceCueEnabled(true)
        model.setColorPalette(ColorPalette.WINDOWS_98)
        model.setSelectedExerciseMode(ExerciseMode.DEAD_HANG)
        model.setShowCameraPreview(false)
        await {
            val s = model.uiState.value
            s.selectedTab == RootTab.PROFILE && s.settings.overlayEnabled &&
                !s.settings.mediaControlEnabled && !s.settings.repSoundEnabled &&
                s.settings.voiceCueEnabled && s.settings.colorPalette == ColorPalette.WINDOWS_98 &&
                s.settings.selectedExerciseMode == ExerciseMode.DEAD_HANG && !s.showCameraPreview
        }
        assertFalse(DebugPreviewStore.enabled.value)
        model.setShowCameraPreview(true)
        await { model.uiState.value.showCameraPreview }
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        model.refreshPermissionState()
        await { model.uiState.value.permissions.cameraGranted }
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        model.refreshPermissionState()
        await { !model.uiState.value.permissions.cameraGranted }
    }

    @Test fun workoutLifecycleRecordsEditsAndRemovesSetsAndHistory() {
        model.pauseWorkout(); model.resumeWorkout(); model.endWorkout()
        model.startWorkout(ExerciseMode.PULL_UP)
        await { model.uiState.value.workoutSession != null }
        verify { HangCamService.start(app) }
        model.startWorkout(ExerciseMode.SQUAT)
        await { model.uiState.value.workoutMessage == "Workout already running" }
        assertEquals(ExerciseMode.PULL_UP, model.uiState.value.workoutSession!!.mode)
        emit(ExerciseMode.SQUAT)
        emit(ExerciseMode.PULL_UP)
        await { model.uiState.value.workoutSession!!.completedSetCount == 1 }
        val set = model.uiState.value.workoutSession!!.editor.sets.single()
        model.editActiveSet(set.id, 9, 30000)
        await { model.uiState.value.workoutSession!!.editor.sets.single().reps == 9 }
        model.pauseWorkout()
        await { model.uiState.value.workoutSession!!.paused }
        verify { HangCamService.stop(app) }
        emit(ExerciseMode.PULL_UP)
        model.resumeWorkout()
        await { !model.uiState.value.workoutSession!!.paused }
        assertEquals(1, model.uiState.value.workoutSession!!.completedSetCount)
        emit(ExerciseMode.PULL_UP)
        await { model.uiState.value.workoutSession!!.completedSetCount == 2 }
        model.endWorkout()
        await { model.uiState.value.workoutSession == null && model.uiState.value.completedWorkouts.size == 1 }
        val id = model.uiState.value.completedWorkouts.single().workoutId
        model.openWorkoutDetail(id)
        await { model.uiState.value.selectedWorkoutDetail != null }
        model.editDetailSet(set.id, 12, 70000)
        await { model.uiState.value.selectedWorkoutDetail?.sets?.firstOrNull()?.reps == 12 }
        model.deleteDetailSet(set.id)
        await { model.uiState.value.selectedWorkoutDetail?.sets?.size == 1 }
        model.closeWorkoutDetail()
        await { model.uiState.value.selectedWorkoutDetail == null }
        model.openWorkoutDetail(id)
        await { model.uiState.value.selectedWorkoutDetail != null }
        model.deleteCompletedWorkout(id)
        await { model.uiState.value.completedWorkouts.isEmpty() && model.uiState.value.selectedWorkoutDetail == null }
        model.clearWorkoutMessage()
        await { model.uiState.value.workoutMessage == null }
    }

    @Test fun holdWorkoutsUseDurationsAndAllowDeletingAnActiveSet() {
        emit(ExerciseMode.DEAD_HANG)
        model.setSelectedExerciseMode(ExerciseMode.DEAD_HANG)
        await { model.uiState.value.settings.selectedExerciseMode == ExerciseMode.DEAD_HANG }
        model.startWorkout()
        await { model.uiState.value.workoutSession != null }
        emit(ExerciseMode.DEAD_HANG, reps = 0)
        await { model.uiState.value.workoutSession!!.completedSetCount == 1 }
        assertTrue(model.uiState.value.workoutMessage!!.contains("1:05 hold"))
        val set = model.uiState.value.workoutSession!!.editor.sets.single()
        model.deleteActiveSet(set.id)
        await { model.uiState.value.workoutSession!!.completedSetCount == 0 }
        model.clearWorkoutMessage()
        await { model.uiState.value.workoutMessage == null }
        model.editDetailSet(-1, 1, 0); model.deleteDetailSet(-1); model.deleteCompletedWorkout(-1)
        model.endWorkout()
        await { model.uiState.value.workoutSession == null }
    }
}

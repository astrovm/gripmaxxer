package com.astrovm.gripmaxxer.ui

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.astrovm.gripmaxxer.datastore.AppSettings
import com.astrovm.gripmaxxer.datastore.ColorPalette
import com.astrovm.gripmaxxer.pose.NormalizedLandmark
import com.astrovm.gripmaxxer.reps.ExerciseMode
import com.astrovm.gripmaxxer.service.DebugPreviewFrame
import com.astrovm.gripmaxxer.ui.theme.GripmaxxerTheme
import com.astrovm.gripmaxxer.workout.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp-h2000dp")
class MainScreenTest {
    @get:Rule val compose = createComposeRule()
    private val state = MutableStateFlow(MainUiState())
    private val model = mockk<MainViewModel>(relaxed = true)
    private val palettes = listOf(ColorPalette.BLACK_WHITE, ColorPalette.WINDOWS_98)
    private val sampleSet = WorkoutSetState(7, 1, 5, 65000, 1000, true)

    @After fun cleanup() = unmockkAll()

    private fun render() {
        every { model.uiState } returns state
        every { model.selectTab(any()) } answers { state.value = state.value.copy(selectedTab = firstArg()) }
        every { model.setColorPalette(any()) } answers {
            state.value = state.value.copy(settings = state.value.settings.copy(colorPalette = firstArg()))
        }
        compose.setContent {
            val current by state.collectAsState()
            GripmaxxerTheme(current.settings.colorPalette) { MainScreen(model) }
        }
    }
    private fun show(value: MainUiState) {
        compose.runOnIdle { state.value = value }
        compose.waitForIdle()
    }
    private fun click(text: String) = compose.onNodeWithText(text).performClick()
    private fun settings(palette: ColorPalette) = AppSettings(colorPalette = palette)

    @Test fun workoutRequiresPermissionsAndForwardsExerciseAndStartActions() {
        render()
        for (palette in palettes) {
            show(MainUiState(settings = settings(palette).copy(overlayEnabled = true, mediaControlEnabled = true)))
            compose.onNodeWithText("Missing permissions").assertExists()
            compose.onNodeWithText("Start Workout").assertIsNotEnabled()
            compose.onNodeWithText("Open notification access").assertExists()
            compose.onNodeWithText("Open overlay settings").assertExists()
            show(state.value.copy(permissions = PermissionSnapshot(true, true, true)))
            compose.onNodeWithText("Missing permissions").assertDoesNotExist()
            for (mode in CameraTrackableModes) {
                click(mode.label)
                verify { model.setSelectedExerciseMode(mode) }
                show(state.value.copy(settings = state.value.settings.copy(selectedExerciseMode = mode)))
            }
            click("Start Workout")
            verify { model.startWorkout(ExerciseMode.DIP) }
            compose.onNode(hasText("Log") and hasClickAction()).performClick()
            compose.onNodeWithText("No completed sessions yet.").assertExists()
            compose.onNode(hasText("Workout") and hasClickAction()).performClick()
            compose.onNodeWithText("Select exercise").assertExists()
        }
    }

    @Test fun profileShowsStatsAndForwardsSettingsForEveryPalette() {
        render()
        for (palette in ColorPalette.entries) {
            show(MainUiState(selectedTab = RootTab.PROFILE, settings = settings(palette)))
            compose.onNodeWithText("No completed workouts yet").assertExists()
            show(state.value.copy(profileStats = listOf(ExerciseProfileStats(ExerciseMode.PULL_UP, 3, 12, 65000))))
            compose.onNodeWithText("Max Reps").assertExists()
            compose.onNodeWithText("12").assertExists()
            for (i in 0..4) compose.onAllNodes(isToggleable())[i].performClick()
            verify { model.setMediaControlEnabled(any()) }
            verify { model.setRepSoundEnabled(any()) }
            verify { model.setVoiceCueEnabled(any()) }
            verify { model.setOverlayEnabled(any()) }
            verify { model.setShowCameraPreview(any()) }
            click(palette.label)
            verify { model.setColorPalette(palette) }
        }
    }

    @Test fun trackerPausesResumesEndsAndEditsSetsInBothThemes() {
        render()
        for (palette in palettes) {
            val session = WorkoutSessionUiState(1, ExerciseMode.PULL_UP, 65000, false, 1,
                LiveSetUiState(true, 5, 6000), SessionEditorUiState(listOf(sampleSet)))
            show(MainUiState(settings = settings(palette), workoutSession = session))
            compose.onNodeWithText("Waiting for camera...").assertExists()
            compose.onNodeWithText("Current reps 5").assertExists()
            click("Pause"); verify { model.pauseWorkout() }
            show(state.value.copy(workoutSession = session.copy(paused = true), showCameraPreview = false))
            compose.onNodeWithText("Camera preview is off").assertExists()
            click("Resume"); verify { model.resumeWorkout() }
            click("End"); verify { model.endWorkout() }
            click("Sets")
            click("Edit")
            compose.onNodeWithText("Reps").performTextReplacement("8")
            compose.onNodeWithText("Duration (seconds)").performTextReplacement("70")
            click("Save")
            verify { model.editActiveSet(7, 8, 70000) }
            click("Delete"); verify { model.deleteActiveSet(7) }
            click("Edit"); click("Cancel")
            click("Done")
            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            for (active in listOf(false, true)) {
                show(state.value.copy(showCameraPreview = true,
                    cameraPreviewFrame = DebugPreviewFrame(bitmap, mapOf(0 to NormalizedLandmark(0.2f, 0.3f)), 1),
                    workoutSession = session.copy(mode = ExerciseMode.DEAD_HANG, liveSet = LiveSetUiState(active, 0, 65000))))
                compose.onNodeWithContentDescription("Camera preview with tracking").assertExists()
                compose.onNodeWithText("Current hold 1:05").assertExists()
            }
            show(state.value.copy(workoutSession = session.copy(editor = SessionEditorUiState())))
            click("Sets"); compose.onNodeWithText("No sets yet").assertExists(); click("Done")
        }
    }

    @Test fun logOpensEditsAndDeletesCompletedWorkouts() {
        render()
        for (palette in palettes) {
            val workout = WorkoutFeedItem(10, "Morning session", ExerciseMode.DEAD_HANG, System.currentTimeMillis(), 65000, 1)
            show(MainUiState(selectedTab = RootTab.LOG, settings = settings(palette), completedWorkouts = listOf(workout)))
            click("View session"); verify { model.openWorkoutDetail(10) }
            val detail = CompletedWorkoutDetail(10, workout.title, workout.mode, 0, workout.completedAtMs, 65000,
                listOf(sampleSet.copy(reps = 0)))
            show(state.value.copy(selectedWorkoutDetail = detail))
            compose.onNodeWithText("Set 1: 1:05").assertExists()
            click("Edit")
            compose.onNodeWithText("Reps").performTextReplacement("invalid")
            compose.onNodeWithText("Duration (seconds)").performTextReplacement("-5")
            click("Save"); verify { model.editDetailSet(7, 0, 0) }
            compose.onAllNodesWithText("Delete").onLast().performClick()
            verify { model.deleteDetailSet(7) }
            click("Done"); verify { model.closeWorkoutDetail() }
            show(state.value.copy(selectedWorkoutDetail = null))
            click("Delete"); click("Cancel")
            click("Delete"); compose.onAllNodesWithText("Delete").onLast().performClick()
            verify { model.deleteCompletedWorkout(10) }
        }
    }
    @Test fun permissionSettingsOpenTheCorrectAndroidScreens() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        render()
        show(MainUiState(settings = AppSettings(overlayEnabled = true, mediaControlEnabled = true)))
        click("Open overlay settings")
        val overlay = org.robolectric.Shadows.shadowOf(app).nextStartedActivity
        org.junit.Assert.assertEquals(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, overlay.action)
        org.junit.Assert.assertEquals("package:${app.packageName}", overlay.data.toString())
        click("Open notification access")
        org.junit.Assert.assertEquals(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            org.robolectric.Shadows.shadowOf(app).nextStartedActivity.action)
        for (camera in listOf(false, true)) for (notifications in listOf(false, true)) for (overlay in listOf(false, true)) {
            show(state.value.copy(permissions = PermissionSnapshot(camera, notifications, overlay)))
            if (camera && notifications && overlay) compose.onNodeWithText("Missing permissions").assertDoesNotExist()
            else compose.onNodeWithText("Missing permissions").assertExists()
        }
    }

    @Test fun snackbarAcknowledgesTheWorkoutMessageAfterItsDisplayTime() {
        render()
        show(MainUiState(workoutMessage = "Set recorded"))
        compose.onNodeWithText("Set recorded").assertExists()
        compose.mainClock.advanceTimeBy(6000)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(6))
        compose.waitForIdle()
        verify { model.clearWorkoutMessage() }
    }

    @Test fun previewDrawsTheTrackedLandmarksForActiveAndInactiveCounters() {
        render()
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val landmarks = mapOf(0 to NormalizedLandmark(0.5f, 0.5f), 1 to NormalizedLandmark(-0.5f, 1.5f))
        for (active in listOf(false, true)) {
            show(MainUiState(showCameraPreview = true,
                workoutSession = WorkoutSessionUiState(1, ExerciseMode.DEAD_HANG, 1000, false, 1, LiveSetUiState(active, 0, 1000), SessionEditorUiState()),
                cameraPreviewFrame = DebugPreviewFrame(bitmap, landmarks, 1000),
                monitoring = com.astrovm.gripmaxxer.service.MonitoringSnapshot(hanging = active)))
            val image = compose.onNodeWithContentDescription("Camera preview with tracking").captureToImage()
            org.junit.Assert.assertTrue(image.width > 0 && image.height > 0)
        }
    }

}

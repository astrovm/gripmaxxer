package com.astrovm.gripmaxxer.ui

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import com.astrovm.gripmaxxer.camera.CameraFrame
import com.astrovm.gripmaxxer.tracking.Exercise
import com.astrovm.gripmaxxer.tracking.Pose
import com.astrovm.gripmaxxer.tracking.Poses
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot

class WorkoutUiTest : UiTestBase() {

    private var now = 0L

    private fun feed(pose: Pose?, ms: Long) {
        val end = now + ms
        while (now < end) {
            container.controller.onFrame(pose, now)
            now += 50
        }
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    /** Sets a field's text without focusing it, so no blinking cursor keeps the UI busy. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.type(text: String) {
        performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
        advance()
    }

    private fun startWorkout(exercise: String) {
        launch()
        waitForText("Start", substring = true)
        compose.onNodeWithText(exercise).tap()
        compose.onNodeWithText("Start $exercise").tap()
        waitForText("Finish")
        waitUntil { container.controller.live.value.tracking }
    }

    @Test
    fun picksAnExerciseAndStarts() {
        launch()
        waitForText("On the bar")
        compose.onNodeWithText("On the bar").assertIsDisplayed()
        compose.onNodeWithText("On the floor").assertIsDisplayed()
        compose.onNodeWithText("Squat").tap()
        compose.onNodeWithText("Start Squat").tap()
        waitForText("Finish")
        assertEquals(Exercise.SQUAT, container.controller.live.value.exercise)
        assertTrue(container.previewVisible.value)
        waitUntil { compose.onAllNodes(hasText("Squat") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Step into frame").assertIsDisplayed()
        feed(Poses.standing, 100)
        waitForText("Start your first rep")
    }

    @Test
    fun asksForTheCameraAndExplainsWhenDenied() {
        access = Access()
        grantCamera = false
        launch()
        waitForText("Start Dead hang")
        compose.onNodeWithText("Start Dead hang").tap()
        waitForText("Gripmaxxer needs the camera to count your reps")
        compose.onNodeWithText("Open app settings").tap()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, lastStartedIntent()!!.action)

        grantCamera = true
        compose.onNodeWithText("Start Dead hang").tap()
        waitForText("Finish")
        // Notifications are only asked for once the camera is allowed.
        val camera = android.Manifest.permission.CAMERA
        assertEquals(listOf(camera, camera, android.Manifest.permission.POST_NOTIFICATIONS), permissionRequests)
    }

    @Test
    fun startsWhenTheCameraIsAlreadyAllowed() {
        launch()
        waitForText("Start Dead hang")
        compose.onNodeWithText("Start Dead hang").tap()
        waitForText("Finish")
        assertEquals(listOf(android.Manifest.permission.POST_NOTIFICATIONS), permissionRequests)
    }

    @Test
    fun asksNothingWhenEverythingIsAllowed() {
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        launch()
        waitForText("Start Dead hang")
        compose.onNodeWithText("Start Dead hang").tap()
        waitForText("Finish")
        assertTrue(permissionRequests.isEmpty())
    }

    @Test
    fun pointsToMissingSpecialAccess() {
        access = Access(camera = true)
        launch()
        waitForText("Workout")
        scrollTo("Play and pause your music while you train")
        compose.onAllNodesWithText("Allow")[0].tap()
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, lastStartedIntent()!!.action)
        scrollTo("Show a floating timer over other apps")
        compose.onAllNodesWithText("Allow")[1].tap()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, lastStartedIntent()!!.action)

        // Not wanted: dismissing turns the setting off, so the hint goes away.
        compose.onAllNodesWithContentDescription("Turn off")[1].tap()
        compose.onAllNodesWithContentDescription("Turn off")[0].tap()
        waitUntil { compose.onAllNodesWithText("Allow").fetchSemanticsNodes().isEmpty() }
        val settings = runBlocking { container.settings.settings.first() }
        assertTrue(!settings.mediaControl && !settings.overlay)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun showsWhatTheCameraSees() {
        startWorkout("Dead hang")
        compose.onNodeWithText("Step into frame").assertIsDisplayed()

        feed(Poses.deadHang, 2500)
        waitForText("Holding")
        compose.onNodeWithText("0:02").assertIsDisplayed()

        val image = Bitmap.createBitmap(48, 64, Bitmap.Config.ARGB_8888)
        val partial = Pose(Poses.deadHang.joints - com.astrovm.gripmaxxer.tracking.Joint.LEFT_ELBOW)
        container.preview.value = CameraFrame(partial, now, 480, 640, image)
        advance()
        // Drawing the screen draws the skeleton over the camera image.
        compose.onRoot().captureToImage()
        container.preview.value = CameraFrame(null, now, 480, 640, image)
        advance()

        feed(Poses.standing, 1000)
        waitForText("Saved 0:02")
    }

    @Test
    fun repSetsShowTheirCount() {
        startWorkout("Pull-up")
        feed(Poses.deadHang, 1000)
        waitForText("In set, 0:00", substring = true)
        feed(Poses.standing, 1000)
        waitForText("Grab the bar to start")
    }

    @Test
    fun showsWhenCountingFromThePocket() {
        startWorkout("Squat")
        container.controller.setInPocket(true)
        waitForText("Counting from your pocket")
        container.controller.setInPocket(false)
        waitForText("Starting camera")
    }

    @Test
    fun cameraErrorsCanBeRetried() {
        startWorkout("Dip")
        container.controller.stopTracking("Camera unavailable")
        waitForText("Camera unavailable")
        compose.onNodeWithText("Try again").tap()
        waitUntil { container.controller.live.value.tracking }
    }

    @Test
    fun reopensTheCameraForAnOpenWorkout() {
        runBlocking { container.workouts.start(Exercise.CHIN_UP) }
        launch()
        waitUntil { container.controller.live.value.tracking }
        assertEquals(Exercise.CHIN_UP, container.controller.live.value.exercise)
    }

    @Test
    fun setsCanBeAddedEditedAndDeleted() {
        startWorkout("Push-up")
        compose.onNodeWithText("Add set").tap()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        field("Reps").type("12x")
        field("Seconds").type("30")
        compose.onNodeWithText("Save").tap()
        waitForText("12 reps")
        compose.onNodeWithText("0:30").assertIsDisplayed()

        compose.onNodeWithText("12 reps").tap()
        field("12").type("15")
        compose.onNodeWithText("Save").tap()
        waitForText("15 reps")

        // Switching exercise labels sets from the old one.
        compose.onNodeWithText("Squat").tap()
        waitUntil { container.controller.live.value.exercise == Exercise.SQUAT }
        waitForText("Push-up")

        compose.onNodeWithText("15 reps").tap()
        compose.onNodeWithText("Cancel").tap()
        compose.onNodeWithText("15 reps").tap()
        compose.onNodeWithText("Delete").tap()
        waitUntil { compose.onAllNodesWithText("15 reps").fetchSemanticsNodes().isEmpty() }

        // Deleted by mistake: bring it back.
        waitForText("Set deleted")
        compose.onNodeWithText("Undo").tap()
        waitForText("15 reps")
    }

    @Test
    fun aNewSetStartsEmpty() {
        startWorkout("Dead hang")
        compose.onNodeWithText("Add set").tap()
        field("Seconds").type("45")
        compose.onNodeWithText("Save").tap()
        waitForText("0:45")

        compose.onNodeWithText("Pull-up").tap()
        waitUntil { container.controller.live.value.exercise == Exercise.PULL_UP }
        compose.onNodeWithText("Add set").tap()
        waitForText("New Pull-up set")
        assertTrue(compose.onAllNodes(hasSetTextAction() and hasText("45")).fetchSemanticsNodes().isEmpty())

        // The keyboard's done key saves.
        field("Reps").type("8")
        field("Seconds").performImeAction()
        waitForText("8 reps")
        assertEquals(0L, runBlocking { container.workouts.active.first() }!!.sets.last().durationMs)
    }

    @Test
    fun showsRestAndPastResults() {
        runBlocking {
            val workouts = container.workouts
            val id = workouts.start(Exercise.PUSH_UP)
            workouts.addSet(id, Exercise.PUSH_UP, reps = 12, durationMs = 0)
            workouts.addSet(id, Exercise.PUSH_UP, reps = 9, durationMs = 0)
            workouts.finish(id)
        }
        startWorkout("Push-up")
        waitForText("Best 12 reps, last time 12 reps")
        compose.onNodeWithText("Rest", substring = true).assertDoesNotExist()

        compose.onNodeWithText("Add set").tap()
        field("Reps").type("10")
        compose.onNodeWithText("Save").tap()
        waitForText("Rest 0:0", substring = true)
    }

    @Test
    fun holdSetsOnlyAskForTime() {
        startWorkout("Active hang")
        compose.onNodeWithText("Add set").tap()
        assertTrue(compose.onAllNodes(hasText("Reps")).fetchSemanticsNodes().isEmpty())
        field("Seconds").type("45")
        compose.onNodeWithText("Save").tap()
        waitForText("0:45")
        compose.onNodeWithContentDescription("Counted automatically").assertDoesNotExist()
        compose.onNodeWithText("Add set").tap()
        compose.onNodeWithText("Cancel").tap()
    }

    @Test
    fun emptyWorkoutAsksBeforeDiscarding() {
        startWorkout("Squat")
        compose.onNodeWithText("Finish").tap()
        compose.onNodeWithText("Nothing logged yet. Discard this workout?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").tap()
        compose.onNodeWithText("Finish").tap()
        compose.onNodeWithText("Discard").tap()
        waitForText("Start Squat")
        assertNull(runBlocking { container.workouts.active.first() })
    }

    @Test
    fun finishedWorkoutGoesToHistory() {
        startWorkout("Dip")
        feed(Poses.armsStraight, 300)
        feed(Poses.armsBent, 400)
        feed(Poses.armsStraight, 400)
        feed(null, 3_100)
        waitForText("1 rep")
        compose.onNodeWithContentDescription("Counted automatically").assertIsDisplayed()
        compose.onNodeWithText("Finish").tap()
        waitForText("Start Dip")
        compose.onNodeWithText("History").tap()
        waitForText("1 set, 1 rep")
    }
}

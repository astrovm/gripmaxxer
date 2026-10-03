package com.astrovm.gripmaxxer.ui

import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import com.astrovm.gripmaxxer.data.Accent
import com.astrovm.gripmaxxer.tracking.Exercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryProfileUiTest : UiTestBase() {

    /** A finished workout: pull-ups then a dead hang. */
    private fun finishedWorkout(): Long = runBlocking {
        val workouts = container.workouts
        val id = workouts.start(Exercise.PULL_UP)
        workouts.addSet(id, Exercise.PULL_UP, reps = 8, durationMs = 30_000)
        workouts.addSet(id, Exercise.PULL_UP, reps = 6, durationMs = 25_000)
        workouts.addSet(id, Exercise.DEAD_HANG, reps = 0, durationMs = 61_000)
        workouts.finish(id)
        id
    }

    private fun openTab(name: String) {
        launch()
        waitForText(name)
        compose.onAllNodesWithText(name).onLast().tap()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onLast() =
        get(fetchSemanticsNodes().size - 1)

    @Test
    fun emptyHistorySaysWhatWillShowUp() {
        openTab("History")
        waitForText("Finished workouts show up here")
    }

    @Test
    fun historySummarizesEachExercise() {
        finishedWorkout()
        openTab("History")
        waitForText("2 sets, 14 reps")
        compose.onNodeWithText("1 set, 1:01").assertExists()
    }

    @Test
    fun detailEditsAndDeletesSets() {
        finishedWorkout()
        openTab("History")
        waitForText("2 sets, 14 reps")
        compose.onNodeWithText("2 sets, 14 reps").tap()
        waitForText("Took", substring = true)

        compose.onNodeWithText("8 reps").tap()
        compose.onNode(hasSetTextAction() and hasText("8"))
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("10")) }
        advance()
        compose.onNodeWithText("Save").tap()
        waitForText("2 sets, 16 reps")

        compose.onNodeWithText("1:01").tap()
        compose.onNodeWithText("Cancel").tap()
        compose.onNodeWithText("1:01").tap()
        compose.onNodeWithText("Delete").tap()
        waitUntil { compose.onAllNodesWithText("Dead hang").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithContentDescription("Back").tap()
        waitForText("History")
    }

    @Test
    fun deletingAWorkoutAsksFirst() {
        val id = finishedWorkout()
        openTab("History")
        waitForText("2 sets, 14 reps")
        compose.onNodeWithText("2 sets, 14 reps").tap()
        waitForText("Took", substring = true)

        compose.onNodeWithContentDescription("Delete workout").tap()
        compose.onNodeWithText("Cancel").tap()
        compose.onNodeWithContentDescription("Delete workout").tap()
        compose.onNodeWithText("Delete this workout?").assertExists()
        compose.onNodeWithText("Delete").tap()
        waitForText("Finished workouts show up here")
        assertEquals(null, runBlocking { container.workouts.workout(id).first() })
    }

    @Test
    fun systemBackLeavesTheDetail() {
        finishedWorkout()
        openTab("History")
        waitForText("2 sets, 14 reps")
        compose.onNodeWithText("2 sets, 14 reps").tap()
        waitForText("Took", substring = true)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitUntil { viewModel.openWorkoutId.value == null }
        waitForText("History")
    }

    @Test
    fun profileShowsPersonalBests() {
        finishedWorkout()
        openTab("Profile")
        waitForText("Personal bests")
        compose.onNodeWithText("2 sets, 14 reps total").assertExists()
        compose.onNodeWithText("8 reps").assertExists()
        compose.onNodeWithText("1 set, 1:01 total").assertExists()
    }

    @Test
    fun profileTogglesSettings() {
        openTab("Profile")
        waitForText("Beep on each rep")
        compose.onNodeWithText("Beep on each rep").tap()
        compose.onNodeWithText("Read out hold time").tap()
        compose.onNodeWithText("Play and pause media").tap()
        compose.onNodeWithText("Floating timer").tap()
        waitUntil {
            val settings = runBlocking { container.settings.settings.first() }
            !settings.repSound && !settings.voiceCues && !settings.mediaControl && !settings.overlay
        }
    }

    @Test
    fun profileAsksForMissingAccess() {
        access = Access(camera = true)
        openTab("Profile")
        waitForText("Needs permission")
        compose.onAllNodesWithText("Allow")[0].tap()
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, lastStartedIntent()!!.action)
        compose.onAllNodesWithText("Allow")[1].tap()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, lastStartedIntent()!!.action)
    }

    @Test
    fun accentColorChanges() {
        openTab("Profile")
        scrollTo("Color")
        compose.onNodeWithContentDescription("White").assertIsSelected()
        compose.onNodeWithContentDescription("Green").tap()
        waitUntil { runBlocking { container.settings.settings.first().accent } == Accent.GREEN }
        // The saved setting reaches the screen a moment later.
        waitUntil {
            compose.onAllNodes(hasContentDescription("Green") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun versionLinksToTheSource() {
        openTab("Profile")
        scrollTo("Gripmaxxer", substring = true)
        compose.onNodeWithText("Gripmaxxer", substring = true).tap()
        assertEquals(listOf("https://github.com/astrovm/gripmaxxer"), openedLinks)
    }
}

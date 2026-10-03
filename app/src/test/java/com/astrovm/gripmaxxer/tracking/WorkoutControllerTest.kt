package com.astrovm.gripmaxxer.tracking

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.data.GripDatabase
import com.astrovm.gripmaxxer.data.SettingsRepository
import com.astrovm.gripmaxxer.data.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutControllerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var db: GripDatabase
    private lateinit var workouts: WorkoutRepository
    private lateinit var settings: SettingsRepository
    private lateinit var controller: WorkoutController
    private val effects = RecordingEffects()
    private var cameraStarts = 0
    private var cameraStops = 0
    private var now = 0L

    class RecordingEffects : TrackingEffects {
        val log = mutableListOf<String>()
        override fun beep() {
            log += "beep"
        }

        override fun say(text: String) {
            log += "say $text"
        }

        override fun playMedia() {
            log += "play"
        }

        override fun pauseMedia() {
            log += "pause"
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), GripDatabase::class.java)
            .build()
        workouts = WorkoutRepository(db.workoutDao())
        settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("s.preferences_pb") },
        )
        controller = WorkoutController(workouts, settings, scope, { cameraStarts++ }, { cameraStops++ })
        controller.effects = effects
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun feed(pose: Pose?, ms: Long) {
        val end = now + ms
        while (now < end) {
            controller.onFrame(pose, now)
            now += 50
        }
    }

    /** Waits until queued database writes are done. */
    private suspend fun settle() = withTimeout(5_000) { workouts.active.first() }

    @Test
    fun runsSilentlyBeforeTheServiceAttaches() = runBlocking {
        controller.effects = null
        controller.start(Exercise.PUSH_UP)
        feed(Poses.armsStraight, 300)
        feed(Poses.armsBent, 400)
        feed(Poses.armsStraight, 400)
        controller.switchExercise(Exercise.DEAD_HANG)
        feed(Poses.deadHang, 11_000)
        feed(Poses.standing, 1000)
        assertTrue(controller.finish())
        assertTrue(effects.log.isEmpty())
    }

    @Test
    fun framesBeforeStartAreIgnored() {
        feed(Poses.deadHang, 1000)
        assertEquals(LiveState(), controller.live.value)
    }

    @Test
    fun hangIsSavedWithCuesAndMedia() = runBlocking {
        controller.start(Exercise.DEAD_HANG)
        assertEquals(1, cameraStarts)
        assertTrue(controller.live.value.tracking)

        feed(Poses.deadHang, 21_000)
        val live = controller.live.value
        assertTrue(live.inSet)
        assertTrue(live.personVisible)
        assertEquals(listOf("play", "say 10 seconds", "say 20 seconds"), effects.log)

        feed(Poses.standing, 1000)
        assertEquals("pause", effects.log.last())
        assertFalse(controller.live.value.inSet)
        assertEquals(Exercise.DEAD_HANG, controller.live.value.lastSet!!.exercise)

        assertTrue(controller.finish())
        assertEquals(1, cameraStops)
        val saved = workouts.history.first().single().sets.single()
        assertEquals(20_950L, saved.durationMs)
        assertEquals(Exercise.DEAD_HANG, settings.settings.first().lastExercise)
    }

    @Test
    fun repsBeepAndPersonLeavingShows() = runBlocking {
        settings.setMediaControl(false)
        settings.setVoiceCues(false)
        controller.start(Exercise.PUSH_UP)
        settle()
        feed(null, 100)
        assertFalse(controller.live.value.personVisible)
        feed(Poses.armsStraight, 300)
        feed(Poses.armsBent, 400)
        feed(Poses.armsStraight, 400)
        assertEquals(listOf("beep"), effects.log)
        assertEquals(1, controller.live.value.reps)
        feed(null, 1100)
        assertFalse(controller.live.value.personVisible)
    }

    @Test
    fun silentWhenSoundsAreOff() = runBlocking {
        settings.setRepSound(false)
        settings.setVoiceCues(false)
        settings.setMediaControl(false)
        controller.start(Exercise.DEAD_HANG)
        settle()
        feed(Poses.deadHang, 12_000)
        feed(Poses.standing, 1000)
        assertTrue(effects.log.isEmpty())
    }

    @Test
    fun switchingExerciseSavesTheSetInProgress() = runBlocking {
        controller.start(Exercise.ACTIVE_HANG)
        feed(Poses.deadHang, 5_000)
        controller.switchExercise(Exercise.PULL_UP)
        assertEquals("pause", effects.log.last())
        assertEquals(Exercise.PULL_UP, controller.live.value.exercise)
        assertFalse(controller.live.value.inSet)

        controller.switchExercise(Exercise.PULL_UP)
        assertTrue(controller.finish())
        val workout = workouts.history.first().single()
        assertEquals(Exercise.PULL_UP, workout.exercise)
        assertEquals(Exercise.ACTIVE_HANG, workout.sets.single().exercise)
        assertEquals(Exercise.PULL_UP, settings.settings.first().lastExercise)
    }

    @Test
    fun switchingWithoutAWorkoutDoesNothing() = runBlocking {
        controller.switchExercise(Exercise.DIP)
        assertNull(controller.live.value.exercise)
    }

    @Test
    fun finishingAnEmptyWorkoutDropsIt() = runBlocking {
        controller.start(Exercise.SQUAT)
        assertFalse(controller.finish())
        assertNull(workouts.active.first())
        assertTrue(workouts.history.first().isEmpty())
        assertFalse(controller.finish())
    }

    @Test
    fun stoppingKeepsTheWorkoutAndCanResume() = runBlocking {
        controller.start(Exercise.DIP)
        controller.start(Exercise.DIP)
        // Already tracking: the camera isn't started twice.
        assertEquals(1, cameraStarts)

        controller.stopTracking("Camera unavailable")
        assertEquals(1, cameraStops)
        assertEquals("Camera unavailable", controller.live.value.error)
        controller.stopTracking()
        assertEquals(1, cameraStops)

        controller.resume()
        assertEquals(2, cameraStarts)
        assertEquals(Exercise.DIP, controller.live.value.exercise)

        // A workout left open by a closed app can still be finished.
        controller.stopTracking()
        val id = workouts.active.first()!!.id
        workouts.addSet(id, Exercise.DIP, 5, 0)
        assertTrue(controller.finish())
    }

    @Test
    fun resumeWithoutAWorkoutDoesNothing() = runBlocking {
        controller.resume()
        assertEquals(0, cameraStarts)
    }

    @Test
    fun startKeepsTheRunningExercise() = runBlocking {
        controller.start(Exercise.CHIN_UP)
        controller.stopTracking()
        controller.start(Exercise.SQUAT)
        assertEquals(Exercise.CHIN_UP, controller.live.value.exercise)
    }

    @Test
    fun failedWriteDoesNotBlockLaterOnes() = runBlocking {
        controller.start(Exercise.DEAD_HANG)
        val id = workouts.active.first()!!.id
        feed(Poses.deadHang, 5_000)
        // The workout disappears under the controller, so saving the set fails.
        workouts.delete(id)
        controller.stopTracking()
        controller.start(Exercise.DIP)
        assertFalse(controller.finish())
    }
}

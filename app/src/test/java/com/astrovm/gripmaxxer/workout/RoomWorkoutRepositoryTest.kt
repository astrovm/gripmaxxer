package com.astrovm.gripmaxxer.workout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.datastore.GripmaxxerDatabase
import com.astrovm.gripmaxxer.datastore.WorkoutEntity
import com.astrovm.gripmaxxer.datastore.WorkoutSetEntity
import com.astrovm.gripmaxxer.reps.ExerciseMode
import com.astrovm.gripmaxxer.service.AutoSetEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomWorkoutRepositoryTest {

    private lateinit var database: GripmaxxerDatabase
    private lateinit var repository: RoomWorkoutRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GripmaxxerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomWorkoutRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun event(mode: ExerciseMode, reps: Int = 5, activeMs: Long = 12_000L, at: Long = 1_000L) =
        AutoSetEvent(eventId = at, mode = mode, reps = reps, activeMs = activeMs, timestampMs = at)

    private suspend fun insertCompleted(
        mode: String,
        startedAtMs: Long,
        completedAtMs: Long,
        sets: List<Pair<Int, Long>> = emptyList(),
    ): Long {
        val dao = database.workoutDao()
        val id = dao.insertWorkout(
            WorkoutEntity(
                title = mode,
                exerciseModeName = mode,
                startedAtMs = startedAtMs,
                completedAtMs = completedAtMs,
                isPaused = false,
                pauseStartedAtMs = null,
                pausedAccumulatedMs = 0L,
            )
        )
        sets.forEachIndexed { index, (reps, duration) ->
            dao.insertWorkoutSet(
                WorkoutSetEntity(
                    workoutId = id,
                    setNumber = index + 1,
                    reps = reps,
                    durationMs = duration,
                    completedAtMs = completedAtMs,
                    autoTracked = false,
                )
            )
        }
        return id
    }

    @Test
    fun `start pause resume and end a workout`() = runTest {
        assertNull(repository.activeWorkoutFlow.first())

        val id = repository.startWorkout(ExerciseMode.PULL_UP)
        assertEquals(id, repository.startWorkout(ExerciseMode.DIP))

        val active = repository.activeWorkoutFlow.first()!!
        assertEquals(id, active.id)
        assertEquals(ExerciseMode.PULL_UP, active.mode)
        assertEquals("Pull-up", active.title)
        assertFalse(active.paused)
        assertTrue(active.sets.isEmpty())

        assertTrue(repository.pauseWorkout(id))
        assertFalse(repository.pauseWorkout(id))
        assertNull(repository.appendAutoSet(id, event(ExerciseMode.PULL_UP)))
        assertTrue(repository.activeWorkoutFlow.first()!!.paused)

        assertTrue(repository.resumeWorkout(id))
        assertFalse(repository.resumeWorkout(id))

        val set = repository.appendAutoSet(id, event(ExerciseMode.PULL_UP, reps = -3, activeMs = -5L))!!
        assertEquals(1, set.setNumber)
        assertEquals(0, set.reps)
        assertEquals(0L, set.durationMs)
        assertTrue(set.autoTracked)
        assertEquals(set, repository.getSetById(set.id))
        assertNull(repository.appendAutoSet(id, event(ExerciseMode.DIP)))

        assertTrue(repository.pauseWorkout(id))
        assertTrue(repository.endWorkout(id))
        // Ending an already completed workout is a no-op success.
        assertTrue(repository.endWorkout(id))
        assertFalse(repository.pauseWorkout(id))
        assertFalse(repository.resumeWorkout(id))
        assertNull(repository.appendAutoSet(id, event(ExerciseMode.PULL_UP)))
        assertNull(repository.activeWorkoutFlow.first())

        val detail = repository.getCompletedWorkoutDetail(id)!!
        assertEquals(ExerciseMode.PULL_UP, detail.mode)
        assertEquals(1, detail.sets.size)

        val feed = repository.completedWorkoutFeedFlow.first()
        assertEquals(1, feed.size)
        assertEquals(id, feed.single().workoutId)
        assertEquals(1, feed.single().setCount)
        assertEquals(1, repository.calendarSummaryFlow.first().single().workoutCount)
        assertEquals(1, database.workoutDao().getCompletedWorkoutCount())
    }

    @Test
    fun `ending a workout without sets discards it`() = runTest {
        val id = repository.startWorkout(ExerciseMode.SQUAT)
        assertTrue(repository.endWorkout(id))
        assertNull(repository.getCompletedWorkoutDetail(id))
        assertTrue(repository.completedWorkoutFeedFlow.first().isEmpty())
    }

    @Test
    fun `ending a running workout keeps accumulated pause time`() = runTest {
        val id = repository.startWorkout(ExerciseMode.DEAD_HANG)
        repository.appendAutoSet(id, event(ExerciseMode.DEAD_HANG, reps = 0, activeMs = 30_000L))
        assertTrue(repository.endWorkout(id))
        val detail = repository.getCompletedWorkoutDetail(id)!!
        assertTrue(detail.durationMs >= 0L)
        assertEquals(30_000L, detail.sets.single().durationMs)
    }

    @Test
    fun `unknown ids are rejected`() = runTest {
        assertFalse(repository.pauseWorkout(999L))
        assertFalse(repository.resumeWorkout(999L))
        assertFalse(repository.endWorkout(999L))
        assertNull(repository.appendAutoSet(999L, event(ExerciseMode.PULL_UP)))
        assertFalse(repository.editSet(999L, 1, 1L))
        assertFalse(repository.deleteSet(999L))
        assertFalse(repository.deleteWorkout(999L))
        assertNull(repository.getCompletedWorkoutDetail(999L))
        assertNull(repository.getSetById(999L))
    }

    @Test
    fun `editing and deleting sets resequences the rest`() = runTest {
        val id = repository.startWorkout(ExerciseMode.PUSH_UP)
        val first = repository.appendAutoSet(id, event(ExerciseMode.PUSH_UP, reps = 10))!!
        val second = repository.appendAutoSet(id, event(ExerciseMode.PUSH_UP, reps = 12))!!
        val third = repository.appendAutoSet(id, event(ExerciseMode.PUSH_UP, reps = 14))!!
        assertEquals(listOf(1, 2, 3), listOf(first, second, third).map { it.setNumber })

        assertTrue(repository.editSet(second.id, reps = -1, durationMs = -1L))
        val edited = repository.getSetById(second.id)!!
        assertEquals(0, edited.reps)
        assertEquals(0L, edited.durationMs)

        assertTrue(repository.deleteSet(first.id))
        val remaining = repository.activeWorkoutFlow.first()!!.sets
        assertEquals(listOf(second.id, third.id), remaining.map { it.id })
        assertEquals(listOf(1, 2), remaining.map { it.setNumber })

        // Deleting every set from an active workout keeps the workout running.
        assertTrue(repository.deleteSet(second.id))
        assertTrue(repository.deleteSet(third.id))
        assertNotNull(repository.activeWorkoutFlow.first())
    }

    @Test
    fun `deleting the last set of a completed workout removes the workout`() = runTest {
        val id = insertCompleted("DIP", startedAtMs = 1_000L, completedAtMs = 61_000L, sets = listOf(8 to 20_000L))
        val setId = repository.getCompletedWorkoutDetail(id)!!.sets.single().id
        assertTrue(repository.deleteSet(setId))
        assertNull(repository.getCompletedWorkoutDetail(id))
    }

    @Test
    fun `completed workouts can be deleted but active ones cannot`() = runTest {
        val active = repository.startWorkout(ExerciseMode.CHIN_UP)
        assertFalse(repository.deleteWorkout(active))
        val done = insertCompleted("CHIN_UP", 1_000L, 5_000L, listOf(3 to 4_000L))
        assertTrue(repository.deleteWorkout(done))
        assertNull(repository.getCompletedWorkoutDetail(done))
        assertNull(repository.getCompletedWorkoutDetail(active))
    }

    @Test
    fun `profile stats aggregate per mode in tracker order and skip unknown modes`() = runTest {
        insertCompleted("SQUAT", 1_000L, 100_000L, listOf(10 to 30_000L, 15 to 40_000L))
        insertCompleted("DEAD_HANG", 1_000L, 200_000L, listOf(0 to 90_000L))
        insertCompleted("DEAD_HANG", 1_000L, 300_000L, listOf(0 to 60_000L))
        insertCompleted("LEGACY_MODE", 1_000L, 400_000L, listOf(1 to 1_000L))

        val stats = repository.profileStatsFlow.first()
        assertEquals(listOf(ExerciseMode.DEAD_HANG, ExerciseMode.SQUAT), stats.map { it.mode })
        assertEquals(2, stats[0].totalWorkouts)
        assertEquals(90_000L, stats[0].maxHoldMs)
        assertEquals(15, stats[1].maxReps)

        val feed = repository.completedWorkoutFeedFlow.first()
        assertEquals(4, feed.size)
        // Unknown modes fall back to pull-up in the feed.
        assertEquals(ExerciseMode.PULL_UP, feed.first().mode)
        val legacyId = feed.first().workoutId
        assertNull(repository.getCompletedWorkoutDetail(legacyId))

        repository.purgeLegacyWorkouts()
        assertEquals(3, repository.completedWorkoutFeedFlow.first().size)
    }

    @Test
    fun `active workout with an unknown mode is shown as pull-up and rejects sets`() = runTest {
        val dao = database.workoutDao()
        val id = dao.insertWorkout(
            WorkoutEntity(
                title = "Old",
                exerciseModeName = "",
                startedAtMs = System.currentTimeMillis() - 10_000L,
                completedAtMs = null,
                isPaused = true,
                pauseStartedAtMs = null,
                pausedAccumulatedMs = 0L,
            )
        )
        val active = repository.activeWorkoutFlow.first()!!
        assertEquals(ExerciseMode.PULL_UP, active.mode)
        assertTrue(active.paused)
        // Resuming a paused workout without a pause start time adds no pause.
        assertTrue(repository.resumeWorkout(id))
        assertNull(repository.appendAutoSet(id, event(ExerciseMode.PULL_UP)))
    }

    @Test
    fun `database singleton is shared`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = GripmaxxerDatabase.getInstance(context)
        assertSame(first, GripmaxxerDatabase.getInstance(context))
    }
}

package com.astrovm.gripmaxxer.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.tracking.Exercise
import com.astrovm.gripmaxxer.tracking.TrackedSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryTest {

    private lateinit var db: GripDatabase
    private var now = 1_000L
    private lateinit var repo: WorkoutRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), GripDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = WorkoutRepository(db.workoutDao()) { now }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun startReusesTheRunningWorkout() = runBlocking {
        val id = repo.start(Exercise.PULL_UP)
        assertEquals(id, repo.start(Exercise.DIP))
        val active = repo.active.first()!!
        assertEquals(Exercise.PULL_UP, active.exercise)
        assertNull(active.durationMs)
    }

    @Test
    fun setsAreSavedInOrderWithTheirExercise() = runBlocking {
        val id = repo.start(Exercise.PULL_UP)
        repo.addTrackedSet(id, TrackedSet(Exercise.PULL_UP, reps = 8, durationMs = 30_000, endedAtMs = 5_000))
        now = 9_000
        repo.switchExercise(id, Exercise.DEAD_HANG)
        repo.addSet(id, Exercise.DEAD_HANG, reps = 0, durationMs = 45_000)

        val workout = repo.active.first()!!
        assertEquals(Exercise.DEAD_HANG, workout.exercise)
        assertEquals(listOf(Exercise.PULL_UP, Exercise.DEAD_HANG), workout.exercises)
        val (first, second) = workout.sets
        assertEquals(8, first.reps)
        assertTrue(first.tracked)
        assertEquals(45_000L, second.durationMs)
        assertFalse(second.tracked)
        assertEquals(9_000L, second.completedAtMs)
    }

    @Test
    fun finishingMovesTheWorkoutToHistory() = runBlocking {
        val id = repo.start(Exercise.SQUAT)
        repo.addSet(id, Exercise.SQUAT, reps = 10, durationMs = 0)
        now = 61_000
        assertTrue(repo.finish(id))

        assertNull(repo.active.first())
        val done = repo.history.first().single()
        assertEquals(60_000L, done.durationMs)
        assertEquals(done, repo.workout(id).first())
    }

    @Test
    fun emptyWorkoutIsThrownAway() = runBlocking {
        val id = repo.start(Exercise.SQUAT)
        assertFalse(repo.finish(id))
        assertNull(repo.workout(id).first())
        assertTrue(repo.history.first().isEmpty())
    }

    @Test
    fun setsCanBeEditedAndDeleted() = runBlocking {
        val id = repo.start(Exercise.PUSH_UP)
        repo.addSet(id, Exercise.PUSH_UP, reps = 10, durationMs = 20_000)
        val set = repo.active.first()!!.sets.single()
        repo.updateSet(set.id, reps = 12, durationMs = 25_000)
        assertEquals(12, repo.active.first()!!.sets.single().reps)
        repo.deleteSet(set.id)
        assertTrue(repo.active.first()!!.sets.isEmpty())

        repo.restoreSet(id, set)
        assertEquals(set, repo.active.first()!!.sets.single())
    }

    @Test
    fun setsAddedToAPastWorkoutKeepTheirTime() = runBlocking {
        val id = repo.start(Exercise.DIP)
        repo.addSet(id, Exercise.DIP, reps = 5, durationMs = 0, completedAtMs = 500)
        assertEquals(500L, repo.active.first()!!.sets.single().completedAtMs)
    }

    @Test
    fun deletingAWorkoutDeletesItsSets() = runBlocking {
        val id = repo.start(Exercise.DIP)
        repo.addSet(id, Exercise.DIP, reps = 5, durationMs = 0)
        repo.finish(id)
        repo.delete(id)
        assertTrue(repo.history.first().isEmpty())
        assertEquals(0, db.workoutDao().setCount(id))
    }

    @Test
    fun unknownExercisesAreSkipped() = runBlocking {
        val dao = db.workoutDao()
        val gone = dao.insert(WorkoutEntity(startedAtMs = 0, endedAtMs = 10, exercise = "PLANK"))
        val kept = dao.insert(WorkoutEntity(startedAtMs = 0, endedAtMs = 10, exercise = "DIP"))
        dao.insert(SetEntity(workoutId = kept, exercise = "PLANK", reps = 1, durationMs = 1, completedAtMs = 1, tracked = true))
        dao.insert(SetEntity(workoutId = kept, exercise = "DIP", reps = 3, durationMs = 1, completedAtMs = 2, tracked = true))

        val history = repo.history.first()
        assertEquals(listOf(kept), history.map { it.id })
        assertEquals(listOf(3), history.single().sets.map { it.reps })
        assertNull(repo.workout(gone).first())
    }

    @Test
    fun statsKeepBestsAndLastTimePerExercise() {
        fun set(exercise: Exercise, reps: Int, ms: Long) = WorkoutSet(0, exercise, reps, ms, 0, true)
        val workouts = listOf(
            Workout(1, 0, 1, Exercise.DIP, listOf(set(Exercise.DEAD_HANG, 0, 30_000), set(Exercise.DIP, 8, 20_000))),
            Workout(2, 0, 1, Exercise.DIP, listOf(set(Exercise.DIP, 12, 25_000), set(Exercise.DEAD_HANG, 0, 50_000))),
        )
        // Newest first: workout 1 is the last time.
        val (hang, dip) = statsFor(workouts)
        assertEquals(ExerciseStats(Exercise.DEAD_HANG, 2, 0, 0, 50_000, 80_000, 0, 30_000), hang)
        assertEquals(ExerciseStats(Exercise.DIP, 2, 12, 20, 25_000, 45_000, 8, 20_000), dip)
    }

    @Test
    fun liveStatsComeFromFinishedWorkouts() = runBlocking {
        val id = repo.start(Exercise.CHIN_UP)
        repo.addSet(id, Exercise.CHIN_UP, reps = 6, durationMs = 0)
        assertTrue(repo.stats.first().isEmpty())
        repo.finish(id)
        assertEquals(6, repo.stats.first().single().bestReps)
    }
}

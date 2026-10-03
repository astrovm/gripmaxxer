package com.astrovm.gripmaxxer.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.astrovm.gripmaxxer.tracking.Exercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** History saved by the old app must survive the update. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Builds a database exactly like version 2 of the app left it. */
    private fun createVersion2() {
        val file = context.getDatabasePath("gripmaxxer.db").apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE workouts (title TEXT NOT NULL, exerciseModeName TEXT NOT NULL, " +
                    "startedAtMs INTEGER NOT NULL, completedAtMs INTEGER, isPaused INTEGER NOT NULL, " +
                    "pauseStartedAtMs INTEGER, pausedAccumulatedMs INTEGER NOT NULL, " +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE workout_sets (workoutId INTEGER NOT NULL, setNumber INTEGER NOT NULL, " +
                    "reps INTEGER NOT NULL, durationMs INTEGER NOT NULL, completedAtMs INTEGER NOT NULL, " +
                    "autoTracked INTEGER NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE)",
            )
            db.execSQL("INSERT INTO workouts VALUES ('Pull-up', 'PULL_UP', 1000, 61000, 0, NULL, 0, 1)")
            db.execSQL("INSERT INTO workouts VALUES ('Dead hang', 'DEAD_HANG', 70000, NULL, 1, 80000, 0, 2)")
            db.execSQL("INSERT INTO workout_sets VALUES (1, 1, 8, 30000, 31000, 1, 1)")
            db.execSQL("INSERT INTO workout_sets VALUES (1, 2, 6, 25000, 60000, 0, 2)")
            db.execSQL("INSERT INTO workout_sets VALUES (2, 1, 0, 45000, 75000, 1, 3)")
            db.version = 2
        }
    }

    @Test
    fun oldWorkoutsAndSetsCarryOver() = runBlocking {
        createVersion2()
        val db = GripDatabase.create(context)
        val repo = WorkoutRepository(db.workoutDao())

        val finished = repo.history.first().single()
        assertEquals(Exercise.PULL_UP, finished.exercise)
        assertEquals(60_000L, finished.durationMs)
        assertEquals(listOf(8, 6), finished.sets.map { it.reps })
        assertEquals(listOf(true, false), finished.sets.map { it.tracked })
        assertEquals(Exercise.PULL_UP, finished.sets.first().exercise)

        val open = repo.active.first()!!
        assertEquals(Exercise.DEAD_HANG, open.sets.single().exercise)
        assertEquals(45_000L, open.sets.single().durationMs)

        // New sets get fresh ids after the copied ones.
        repo.addSet(open.id, Exercise.DEAD_HANG, 0, 10_000)
        assertEquals(4L, repo.active.first()!!.sets.last().id)
        db.close()
    }
}

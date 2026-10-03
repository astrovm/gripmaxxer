package com.astrovm.gripmaxxer.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workouts", indices = [Index("endedAtMs")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    /** The exercise being tracked right now, or the last one tracked. */
    val exercise: String,
)

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId")],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workoutId: Long,
    val exercise: String,
    val reps: Int,
    val durationMs: Long,
    val completedAtMs: Long,
    /** Counted by the camera rather than typed in. */
    val tracked: Boolean,
)

data class WorkoutWithSets(
    @Embedded val workout: WorkoutEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutId") val sets: List<SetEntity>,
)

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts WHERE endedAtMs IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutWithSets?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE endedAtMs IS NOT NULL ORDER BY startedAtMs DESC")
    fun observeFinished(): Flow<List<WorkoutWithSets>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutWithSets?>

    @Query("SELECT * FROM workouts WHERE endedAtMs IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    suspend fun active(): WorkoutEntity?

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Query("UPDATE workouts SET exercise = :exercise WHERE id = :id")
    suspend fun setExercise(id: Long, exercise: String)

    @Query("UPDATE workouts SET endedAtMs = :endedAtMs WHERE id = :id")
    suspend fun end(id: Long, endedAtMs: Long)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM sets WHERE workoutId = :workoutId")
    suspend fun setCount(workoutId: Long): Int

    @Insert
    suspend fun insert(set: SetEntity): Long

    @Query("UPDATE sets SET reps = :reps, durationMs = :durationMs WHERE id = :id")
    suspend fun updateSet(id: Long, reps: Int, durationMs: Long)

    @Query("DELETE FROM sets WHERE id = :id")
    suspend fun deleteSet(id: Long)
}

@Database(entities = [WorkoutEntity::class, SetEntity::class], version = 3)
abstract class GripDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        private const val NAME = "gripmaxxer.db"

        fun create(context: Context): GripDatabase =
            Room.databaseBuilder(context, GripDatabase::class.java, NAME)
                .addMigrations(MIGRATION_2_3)
                .build()
    }
}

/**
 * Version 2 had one exercise per workout and a pause feature.
 * Version 3 stores the exercise on each set so a workout can mix exercises.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sets RENAME TO old_sets")
        db.execSQL("ALTER TABLE workouts RENAME TO old_workouts")
        db.execSQL(
            """
            CREATE TABLE workouts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                startedAtMs INTEGER NOT NULL,
                endedAtMs INTEGER,
                exercise TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_workouts_endedAtMs ON workouts (endedAtMs)")
        db.execSQL(
            """
            CREATE TABLE sets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workoutId INTEGER NOT NULL,
                exercise TEXT NOT NULL,
                reps INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                completedAtMs INTEGER NOT NULL,
                tracked INTEGER NOT NULL,
                FOREIGN KEY (workoutId) REFERENCES workouts (id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_sets_workoutId ON sets (workoutId)")
        db.execSQL(
            """
            INSERT INTO workouts (id, startedAtMs, endedAtMs, exercise)
            SELECT id, startedAtMs, completedAtMs, exerciseModeName FROM old_workouts
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO sets (id, workoutId, exercise, reps, durationMs, completedAtMs, tracked)
            SELECT s.id, s.workoutId, w.exerciseModeName, s.reps, s.durationMs, s.completedAtMs, s.autoTracked
            FROM old_sets s JOIN old_workouts w ON w.id = s.workoutId
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE old_sets")
        db.execSQL("DROP TABLE old_workouts")
    }
}

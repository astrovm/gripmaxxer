package com.astrolabs.gripmaxxer.datastore

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "workouts",
    indices = [
        Index(value = ["completedAtMs"]),
        Index(value = ["startedAtMs"]),
        Index(value = ["exerciseModeName"]),
    ],
)
data class WorkoutEntity(
    val title: String,
    val exerciseModeName: String,
    val startedAtMs: Long,
    val completedAtMs: Long?,
    val isPaused: Boolean,
    val pauseStartedAtMs: Long?,
    val pausedAccumulatedMs: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["workoutId"]), Index(value = ["setNumber"]), Index(value = ["completedAtMs"])],
)
data class WorkoutSetEntity(
    val workoutId: Long,
    val setNumber: Int,
    val reps: Int,
    val durationMs: Long,
    val completedAtMs: Long,
    val autoTracked: Boolean,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
)

data class WorkoutWithSetsEntity(
    @Embedded
    val workout: WorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutId",
    )
    val sets: List<WorkoutSetEntity>,
)

data class WorkoutFeedRow(
    val workoutId: Long,
    val title: String,
    val modeName: String,
    val completedAtMs: Long,
    val durationMs: Long,
    val setCount: Int,
)

data class CalendarDayRow(
    val dayEpochMs: Long,
    val workoutCount: Int,
)

data class ProfileBaseRow(
    val totalWorkouts: Int,
    val maxReps: Int,
    val maxHoldMs: Long,
)

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts WHERE completedAtMs IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    fun observeActiveWorkoutWithSets(): Flow<WorkoutWithSetsEntity?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId LIMIT 1")
    suspend fun getWorkoutWithSets(workoutId: Long): WorkoutWithSetsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(entity: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(entity: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteWorkoutById(workoutId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSet(entity: WorkoutSetEntity): Long

    @Update
    suspend fun updateWorkoutSet(entity: WorkoutSetEntity)

    @Update
    suspend fun updateWorkoutSets(entities: List<WorkoutSetEntity>)

    @Query("DELETE FROM workout_sets WHERE id = :setId")
    suspend fun deleteWorkoutSetById(setId: Long)

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY setNumber")
    suspend fun getSetsForWorkout(workoutId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE id = :setId LIMIT 1")
    suspend fun getSetById(setId: Long): WorkoutSetEntity?

    @Query("SELECT COUNT(*) FROM workout_sets WHERE workoutId = :workoutId")
    suspend fun countSetsForWorkout(workoutId: Long): Int

    @Query("SELECT workoutId FROM workout_sets WHERE id = :setId LIMIT 1")
    suspend fun getWorkoutIdBySetId(setId: Long): Long?

    @Query(
        """
        SELECT
            w.id AS workoutId,
            w.title AS title,
            w.exerciseModeName AS modeName,
            w.completedAtMs AS completedAtMs,
            (w.completedAtMs - w.startedAtMs - w.pausedAccumulatedMs) AS durationMs,
            COALESCE(COUNT(ws.id), 0) AS setCount
        FROM workouts w
        LEFT JOIN workout_sets ws ON ws.workoutId = w.id
        WHERE w.completedAtMs IS NOT NULL
        GROUP BY w.id
        ORDER BY w.completedAtMs DESC
        """
    )
    fun observeCompletedWorkoutFeed(): Flow<List<WorkoutFeedRow>>

    @Query(
        """
        SELECT
            (completedAtMs / 86400000) * 86400000 AS dayEpochMs,
            COUNT(*) AS workoutCount
        FROM workouts
        WHERE completedAtMs IS NOT NULL
        GROUP BY dayEpochMs
        ORDER BY dayEpochMs DESC
        """
    )
    fun observeCalendarSummary(): Flow<List<CalendarDayRow>>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM workouts WHERE completedAtMs IS NOT NULL) AS totalWorkouts,
            (SELECT COALESCE(MAX(reps), 0) FROM workout_sets) AS maxReps,
            (SELECT COALESCE(MAX(durationMs), 0) FROM workout_sets) AS maxHoldMs
        """
    )
    fun observeProfileBase(): Flow<ProfileBaseRow>

    @Query("SELECT id FROM workouts WHERE completedAtMs IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    suspend fun getActiveWorkoutId(): Long?

    @Query("SELECT COUNT(*) FROM workouts WHERE completedAtMs IS NOT NULL")
    suspend fun getCompletedWorkoutCount(): Int
}

@Database(
    entities = [
        WorkoutEntity::class,
        WorkoutSetEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class GripmaxxerDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var instance: GripmaxxerDatabase? = null

        fun getInstance(context: Context): GripmaxxerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GripmaxxerDatabase::class.java,
                    "gripmaxxer.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

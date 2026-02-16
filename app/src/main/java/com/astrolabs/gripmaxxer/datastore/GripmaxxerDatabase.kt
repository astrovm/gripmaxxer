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
    tableName = "routines",
    indices = [Index(value = ["name"])],
)
data class RoutineEntity(
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["routineId"]), Index(value = ["position"])],
)
data class RoutineExerciseEntity(
    val routineId: Long,
    val position: Int,
    val exerciseName: String,
    val modeName: String?,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Float,
    val restSeconds: Int,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
)

@Entity(
    tableName = "workouts",
    indices = [Index(value = ["completedAtMs"]), Index(value = ["startedAtMs"])],
)
data class WorkoutEntity(
    val title: String,
    val startedAtMs: Long,
    val completedAtMs: Long?,
    val sourceRoutineId: Long?,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["workoutId"]), Index(value = ["position"]), Index(value = ["exerciseName"])],
)
data class WorkoutExerciseEntity(
    val workoutId: Long,
    val position: Int,
    val exerciseName: String,
    val modeName: String?,
    val restSeconds: Int,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["workoutExerciseId"]), Index(value = ["setNumber"]), Index(value = ["completedAtMs"])],
)
data class WorkoutSetEntity(
    val workoutExerciseId: Long,
    val setNumber: Int,
    val weightKg: Float,
    val reps: Int,
    val done: Boolean,
    val durationMs: Long,
    val completedAtMs: Long?,
    val autoTracked: Boolean,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
)

data class RoutineWithExercisesEntity(
    @Embedded
    val routine: RoutineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineId",
        entity = RoutineExerciseEntity::class,
    )
    val exercises: List<RoutineExerciseEntity>,
)

data class WorkoutExerciseWithSetsEntity(
    @Embedded
    val exercise: WorkoutExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutExerciseId",
    )
    val sets: List<WorkoutSetEntity>,
)

data class WorkoutWithExercisesEntity(
    @Embedded
    val workout: WorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutId",
        entity = WorkoutExerciseEntity::class,
    )
    val exercises: List<WorkoutExerciseWithSetsEntity>,
)

data class WorkoutFeedRow(
    val workoutId: Long,
    val title: String,
    val completedAtMs: Long,
    val durationMs: Long,
    val exerciseCount: Int,
    val totalVolumeKg: Float,
)

data class CalendarDayRow(
    val dayEpochMs: Long,
    val workoutCount: Int,
)

data class ProfileBaseRow(
    val totalWorkouts: Int,
    val totalSets: Int,
    val totalVolumeKg: Float,
    val maxReps: Int,
    val maxActiveMs: Long,
)

data class RecordRow(
    val exerciseName: String,
    val maxReps: Int,
)

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY updatedAtMs DESC")
    fun observeRoutinesWithExercises(): Flow<List<RoutineWithExercisesEntity>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :routineId LIMIT 1")
    suspend fun getRoutineWithExercises(routineId: Long): RoutineWithExercisesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(entity: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(entities: List<RoutineExerciseEntity>): List<Long>

    @Update
    suspend fun updateRoutine(entity: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :routineId")
    suspend fun deleteRoutine(routineId: Long)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteRoutineExercisesByRoutineId(routineId: Long)
}

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts WHERE completedAtMs IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    fun observeActiveWorkoutWithExercises(): Flow<WorkoutWithExercisesEntity?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId LIMIT 1")
    suspend fun getWorkoutWithExercises(workoutId: Long): WorkoutWithExercisesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(entity: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(entity: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercise(entity: WorkoutExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSets(entities: List<WorkoutSetEntity>): List<Long>

    @Update
    suspend fun updateWorkoutSet(entity: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE id = :setId")
    suspend fun deleteWorkoutSetById(setId: Long)

    @Query("SELECT COALESCE(MAX(setNumber), 0) FROM workout_sets WHERE workoutExerciseId = :exerciseId")
    suspend fun getMaxSetNumber(exerciseId: Long): Int

    @Query(
        """
        SELECT
            w.id AS workoutId,
            w.title AS title,
            w.completedAtMs AS completedAtMs,
            (w.completedAtMs - w.startedAtMs) AS durationMs,
            COUNT(DISTINCT we.id) AS exerciseCount,
            COALESCE(SUM(CASE WHEN ws.done = 1 THEN ws.weightKg * ws.reps ELSE 0 END), 0) AS totalVolumeKg
        FROM workouts w
        LEFT JOIN workout_exercises we ON we.workoutId = w.id
        LEFT JOIN workout_sets ws ON ws.workoutExerciseId = we.id
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
            (SELECT COUNT(*) FROM workout_sets WHERE done = 1) AS totalSets,
            (SELECT COALESCE(SUM(weightKg * reps), 0) FROM workout_sets WHERE done = 1) AS totalVolumeKg,
            (SELECT COALESCE(MAX(reps), 0) FROM workout_sets WHERE done = 1) AS maxReps,
            (SELECT COALESCE(MAX(durationMs), 0) FROM workout_sets WHERE done = 1) AS maxActiveMs
        """
    )
    fun observeProfileBase(): Flow<ProfileBaseRow>

    @Query(
        """
        SELECT
            we.exerciseName AS exerciseName,
            MAX(ws.reps) AS maxReps
        FROM workout_sets ws
        INNER JOIN workout_exercises we ON we.id = ws.workoutExerciseId
        WHERE ws.done = 1
        GROUP BY we.exerciseName
        ORDER BY maxReps DESC
        LIMIT :limit
        """
    )
    fun observeTopRecords(limit: Int = 8): Flow<List<RecordRow>>

    @Query(
        """
        SELECT ws.weightKg, ws.reps
        FROM workout_sets ws
        INNER JOIN workout_exercises we ON we.id = ws.workoutExerciseId
        INNER JOIN workouts w ON w.id = we.workoutId
        WHERE we.exerciseName = :exerciseName
            AND ws.done = 1
            AND w.completedAtMs IS NOT NULL
        ORDER BY w.completedAtMs DESC, ws.completedAtMs DESC
        LIMIT 1
        """
    )
    suspend fun getLatestCompletedSet(exerciseName: String): LatestSetRow?

    @Query("SELECT id FROM workouts WHERE completedAtMs IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    suspend fun getActiveWorkoutId(): Long?

    @Query("SELECT COUNT(*) FROM workouts WHERE completedAtMs IS NOT NULL")
    suspend fun getCompletedWorkoutCount(): Int
}

data class LatestSetRow(
    val weightKg: Float,
    val reps: Int,
)

@Database(
    entities = [
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class GripmaxxerDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
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
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}

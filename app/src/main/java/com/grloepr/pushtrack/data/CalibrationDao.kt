package com.grloepr.pushtrack.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for calibration sessions
 */
@Dao
interface CalibrationSessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CalibrationSession): Long
    
    @Update
    suspend fun updateSession(session: CalibrationSession)
    
    @Delete
    suspend fun deleteSession(session: CalibrationSession)
    
    @Query("SELECT * FROM calibration_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): CalibrationSession?
    
    @Query("SELECT * FROM calibration_sessions WHERE exerciseType = :exerciseType ORDER BY startTime DESC")
    suspend fun getSessionsForExercise(exerciseType: String): List<CalibrationSession>
    
    @Query("SELECT * FROM calibration_sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<CalibrationSession>>
    
    @Query("SELECT * FROM calibration_sessions WHERE isApplied = 1")
    suspend fun getAppliedSessions(): List<CalibrationSession>
    
    @Query("SELECT * FROM calibration_sessions WHERE exerciseType = :exerciseType AND isApplied = 1 ORDER BY appliedAt DESC LIMIT 1")
    suspend fun getCurrentActiveSession(exerciseType: String): CalibrationSession?
    
    @Query("DELETE FROM calibration_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSessionById(sessionId: String)
    
    @Query("DELETE FROM calibration_sessions")
    suspend fun deleteAllSessions()
}

/**
 * Data Access Object for exercise thresholds
 */
@Dao
interface ExerciseThresholdsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThresholds(thresholds: ExerciseThresholds)
    
    @Update
    suspend fun updateThresholds(thresholds: ExerciseThresholds)
    
    @Delete
    suspend fun deleteThresholds(thresholds: ExerciseThresholds)
    
    @Query("SELECT * FROM exercise_thresholds WHERE exerciseType = :exerciseType")
    suspend fun getThresholds(exerciseType: String): ExerciseThresholds?
    
    @Query("SELECT * FROM exercise_thresholds")
    suspend fun getAllThresholds(): List<ExerciseThresholds>
    
    @Query("SELECT * FROM exercise_thresholds")
    fun getAllThresholdsFlow(): Flow<List<ExerciseThresholds>>
    
    @Query("UPDATE exercise_thresholds SET upThreshold = :upThreshold, downThreshold = :downThreshold, lastUpdated = :timestamp WHERE exerciseType = :exerciseType")
    suspend fun updateThresholdValues(exerciseType: String, upThreshold: Double, downThreshold: Double, timestamp: Long)
    
    @Query("DELETE FROM exercise_thresholds WHERE exerciseType = :exerciseType")
    suspend fun deleteThresholdsForExercise(exerciseType: String)
    
    @Query("DELETE FROM exercise_thresholds")
    suspend fun deleteAllThresholds()
}

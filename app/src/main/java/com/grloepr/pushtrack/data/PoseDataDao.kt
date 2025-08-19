package com.grloepr.pushtrack.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for pose data operations
 */
@Dao
interface PoseDataDao {
    
    // Basic CRUD operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoseData(poseData: PoseDataEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPoseData(poseDataList: List<PoseDataEntity>)
    
    @Update
    suspend fun updatePoseData(poseData: PoseDataEntity)
    
    @Delete
    suspend fun deletePoseData(poseData: PoseDataEntity)
    
    // Query operations for calibration data
    @Query("SELECT * FROM pose_data WHERE sessionId = :sessionId ORDER BY frameNumber ASC")
    suspend fun getPoseDataForSession(sessionId: String): List<PoseDataEntity>
    
    @Query("SELECT * FROM pose_data WHERE sessionId = :sessionId ORDER BY frameNumber ASC")
    fun getPoseDataForSessionFlow(sessionId: String): Flow<List<PoseDataEntity>>
    
    @Query("SELECT * FROM pose_data WHERE isCalibrationData = 1 AND exerciseType = :exerciseType ORDER BY timestamp DESC")
    suspend fun getCalibrationDataForExercise(exerciseType: String): List<PoseDataEntity>
    
    @Query("SELECT * FROM pose_data WHERE isCalibrationData = 1 AND sessionId = :sessionId AND repNumberInSession = :repNumber ORDER BY frameNumber ASC")
    suspend fun getDataForSpecificRep(sessionId: String, repNumber: Int): List<PoseDataEntity>
    
    // Analysis queries
    @Query("SELECT COUNT(*) FROM pose_data WHERE sessionId = :sessionId AND isValidPose = 1")
    suspend fun getValidPoseCount(sessionId: String): Int
    
    @Query("SELECT AVG(primaryMetric) FROM pose_data WHERE sessionId = :sessionId AND exerciseState = :state AND primaryMetric IS NOT NULL")
    suspend fun getAverageMetricForState(sessionId: String, state: String): Double?
    
    @Query("SELECT MIN(primaryMetric) as min, MAX(primaryMetric) as max FROM pose_data WHERE sessionId = :sessionId AND exerciseState = :state AND primaryMetric IS NOT NULL")
    suspend fun getMetricRangeForState(sessionId: String, state: String): MetricRange?
    
    @Query("SELECT * FROM pose_data WHERE sessionId = :sessionId AND exerciseState = 'START_POSITION' AND primaryMetric IS NOT NULL ORDER BY primaryMetric ASC")
    suspend fun getStartPositionData(sessionId: String): List<PoseDataEntity>
    
    @Query("SELECT * FROM pose_data WHERE sessionId = :sessionId AND exerciseState = 'END_POSITION' AND primaryMetric IS NOT NULL ORDER BY primaryMetric DESC")
    suspend fun getEndPositionData(sessionId: String): List<PoseDataEntity>
    
    // Recent data for real-time analysis
    @Query("SELECT * FROM pose_data WHERE exerciseType = :exerciseType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentPoseData(exerciseType: String, limit: Int = 100): List<PoseDataEntity>
    
    // Performance queries
    @Query("SELECT AVG(detectionConfidence) FROM pose_data WHERE sessionId = :sessionId")
    suspend fun getAverageConfidence(sessionId: String): Float?
    
    @Query("SELECT COUNT(*) FROM pose_data WHERE sessionId = :sessionId AND hasAllRequiredLandmarks = 1")
    suspend fun getCompleteFrameCount(sessionId: String): Int
    
    // Cleanup operations
    @Query("DELETE FROM pose_data WHERE timestamp < :cutoffTime AND isCalibrationData = 0")
    suspend fun deleteOldNonCalibrationData(cutoffTime: Long)
    
    @Query("DELETE FROM pose_data WHERE sessionId = :sessionId")
    suspend fun deleteSessionData(sessionId: String)
    
    @Query("DELETE FROM pose_data")
    suspend fun deleteAllPoseData()
    
    // Statistics
    @Query("SELECT COUNT(DISTINCT sessionId) FROM pose_data WHERE isCalibrationData = 1")
    suspend fun getCalibrationSessionCount(): Int
    
    @Query("SELECT COUNT(*) FROM pose_data")
    suspend fun getTotalDataPointCount(): Int
}

/**
 * Data class for metric range queries
 */
data class MetricRange(
    val min: Double?,
    val max: Double?
)

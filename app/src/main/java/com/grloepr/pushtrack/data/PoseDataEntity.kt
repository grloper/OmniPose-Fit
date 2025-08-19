package com.grloepr.pushtrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Database entity for storing pose detection data during calibration
 */
@Entity(tableName = "pose_data")
@Serializable
data class PoseDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Session information
    val sessionId: String,
    val exerciseType: String,
    val timestamp: Long,
    val frameNumber: Int,
    
    // Exercise metrics
    val primaryMetric: Double?, // Main angle/distance for exercise
    val exerciseState: String,
    val repCount: Int,
    
    // Detailed pose landmarks (JSON serialized)
    val leftShoulderX: Float?,
    val leftShoulderY: Float?,
    val leftShoulderVisibility: Float?,
    
    val rightShoulderX: Float?,
    val rightShoulderY: Float?,
    val rightShoulderVisibility: Float?,
    
    val leftElbowX: Float?,
    val leftElbowY: Float?,
    val leftElbowVisibility: Float?,
    
    val rightElbowX: Float?,
    val rightElbowY: Float?,
    val rightElbowVisibility: Float?,
    
    val leftWristX: Float?,
    val leftWristY: Float?,
    val leftWristVisibility: Float?,
    
    val rightWristX: Float?,
    val rightWristY: Float?,
    val rightWristVisibility: Float?,
    
    val leftHipX: Float?,
    val leftHipY: Float?,
    val leftHipVisibility: Float?,
    
    val rightHipX: Float?,
    val rightHipY: Float?,
    val rightHipVisibility: Float?,
    
    val leftKneeX: Float?,
    val leftKneeY: Float?,
    val leftKneeVisibility: Float?,
    
    val rightKneeX: Float?,
    val rightKneeY: Float?,
    val rightKneeVisibility: Float?,
    
    val leftAnkleX: Float?,
    val leftAnkleY: Float?,
    val leftAnkleVisibility: Float?,
    
    val rightAnkleX: Float?,
    val rightAnkleY: Float?,
    val rightAnkleVisibility: Float?,
    
    // Calculated angles for analysis
    val leftArmAngle: Double?, // Shoulder-Elbow-Wrist
    val rightArmAngle: Double?,
    val leftLegAngle: Double?, // Hip-Knee-Ankle
    val rightLegAngle: Double?,
    
    // Performance metrics
    val detectionConfidence: Float,
    val movementSpeed: Float?, // Pixels per frame
    val formQuality: Float?, // 0-1 scale
    
    // Calibration metadata
    val isCalibrationData: Boolean = false,
    val calibrationPhase: String?, // "warmup", "recording", "analysis"
    val repNumberInSession: Int?, // Which rep in the 10-rep calibration
    
    // Quality flags
    val isValidPose: Boolean,
    val hasAllRequiredLandmarks: Boolean,
    val notes: String? = null
)

/**
 * Calibration session summary
 */
@Entity(tableName = "calibration_sessions")
@Serializable
data class CalibrationSession(
    @PrimaryKey
    val sessionId: String,
    
    val exerciseType: String,
    val startTime: Long,
    val endTime: Long?,
    val totalReps: Int,
    val validReps: Int,
    
    // Calculated optimal thresholds
    val optimalUpThreshold: Double?,
    val optimalDownThreshold: Double?,
    val optimalTransitionSpeed: Double?,
    
    // Statistical analysis
    val averageRepDuration: Double?,
    val standardDeviation: Double?,
    val confidenceScore: Float?,
    
    // Applied to current detector
    val isApplied: Boolean = false,
    val appliedAt: Long?,
    
    val notes: String? = null
)

/**
 * Optimized exercise thresholds derived from calibration data
 */
@Entity(tableName = "exercise_thresholds")
@Serializable
data class ExerciseThresholds(
    @PrimaryKey
    val exerciseType: String,
    
    // Threshold values
    val upThreshold: Double,
    val downThreshold: Double,
    val transitionBuffer: Double, // Hysteresis to prevent jitter
    
    // Speed and timing
    val minRepDuration: Long, // Milliseconds
    val maxRepDuration: Long,
    val speedSensitivity: Float,
    
    // Quality thresholds
    val minConfidence: Float,
    val minLandmarkVisibility: Float,
    
    // Derived from session
    val calibrationSessionId: String?,
    val createdAt: Long,
    val lastUpdated: Long,
    
    // Performance metrics
    val accuracyScore: Float?, // How well it performs on validation data
    val falsePositiveRate: Float?,
    val falseNegativeRate: Float?,
    
    val version: Int = 1
)

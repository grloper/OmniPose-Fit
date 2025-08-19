package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Detects squat movements from pose data
 */
class SquatDetector : ExerciseDetector(ExerciseType.SQUAT) {
    // Thresholds for squats (knee angle) - can be updated via calibration
    private var currentSquatUpThreshold = 150.0  // Standing position (knees almost straight)
    private var currentSquatDownThreshold = 90.0 // Squat position (knees bent)
    
    // Default thresholds for squats (knee angle)
    private val defaultSquatUpThreshold = 150.0  // Standing position (knees almost straight)
    private val defaultSquatDownThreshold = 90.0 // Squat position (knees bent)
    
    /**
     * Calculate the average knee angle (hip-knee-ankle) for both legs
     * Returns null if key landmarks are not detected
     */
    override fun calculateExerciseMetric(pose: Pose): Double? {
        val leftAngle = calculateSingleLegAngle(
            pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
            pose.getPoseLandmark(PoseLandmark.LEFT_KNEE),
            pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        )
        
        val rightAngle = calculateSingleLegAngle(
            pose.getPoseLandmark(PoseLandmark.RIGHT_HIP),
            pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE),
            pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        )
        
        return when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2.0
            leftAngle != null -> leftAngle
            rightAngle != null -> rightAngle
            else -> null
        }
    }
    
    /**
     * Determine squat state based on knee angle
     */
    override fun determineState(metric: Double): ExerciseState {
        return when {
            metric < currentSquatDownThreshold -> ExerciseState.END_POSITION // Squatting down
            metric > currentSquatUpThreshold -> ExerciseState.START_POSITION // Standing up
            else -> currentState // Maintain current state in transition
        }
    }
    
    /**
     * Calculate angle between three points for a single leg (hip-knee-ankle)
     */
    private fun calculateSingleLegAngle(
        hip: PoseLandmark?,
        knee: PoseLandmark?,
        ankle: PoseLandmark?
    ): Double? {
        return calculateAngleBetweenPoints(hip, knee, ankle)
    }
    
    /**
     * Get default up threshold for squats
     */
    override fun getDefaultUpThreshold(): Double {
        return defaultSquatUpThreshold
    }
    
    /**
     * Get default down threshold for squats
     */
    override fun getDefaultDownThreshold(): Double {
        return defaultSquatDownThreshold
    }
    
    /**
     * Get current up threshold (may be calibrated)
     */
    override fun getUpThreshold(): Double {
        return currentSquatUpThreshold
    }
    
    /**
     * Get current down threshold (may be calibrated)
     */
    override fun getDownThreshold(): Double {
        return currentSquatDownThreshold
    }
    
    /**
     * Update thresholds based on calibration results
     */
    override fun updateThresholds(upThreshold: Double, downThreshold: Double) {
        currentSquatUpThreshold = upThreshold
        currentSquatDownThreshold = downThreshold
    }
}
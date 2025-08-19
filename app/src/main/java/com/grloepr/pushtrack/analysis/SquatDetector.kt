package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Detects squat movements from pose data
 */
class SquatDetector : ExerciseDetector(ExerciseType.SQUAT) {
    // Default thresholds for squats (knee angle)
    private val squatUpThreshold = 150.0  // Standing position (knees almost straight)
    private val squatDownThreshold = 90.0 // Squat position (knees bent)
    
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
            metric < squatDownThreshold -> ExerciseState.END_POSITION // Squatting down
            metric > squatUpThreshold -> ExerciseState.START_POSITION // Standing up
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
        return squatUpThreshold
    }
    
    /**
     * Get default down threshold for squats
     */
    override fun getDefaultDownThreshold(): Double {
        return squatDownThreshold
    }
}
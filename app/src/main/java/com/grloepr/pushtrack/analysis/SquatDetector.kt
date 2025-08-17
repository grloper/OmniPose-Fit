package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Detects squat movements from pose data
 * Uses hip-knee-ankle angles and hip height tracking
 */
class SquatDetector : ExerciseDetector(ExerciseType.SQUAT) {
    
    // Angle thresholds for squat detection (knee angle)
    private val squatDownThreshold = 90.0 // degrees - knee angle when in squat position
    private val squatUpThreshold = 160.0 // degrees - knee angle when standing
    
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
}
package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

/**
 * Detects pull-up movements from pose data
 * Uses shoulder height relative to wrists and elbow angles
 */
class PullUpDetector : ExerciseDetector(ExerciseType.PULL_UP) {
    
    // Height thresholds for pull-up detection (normalized shoulder-wrist distance)
    private val pullUpTopThreshold = 30.0 // Shoulders close to wrists (top position)
    private val pullUpBottomThreshold = 80.0 // Shoulders far from wrists (bottom position)
    
    /**
     * Calculate the average vertical distance between shoulders and wrists
     * Returns null if key landmarks are not detected
     */
    override fun calculateExerciseMetric(pose: Pose): Double? {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        
        val leftDistance = calculateVerticalDistance(leftShoulder, leftWrist)
        val rightDistance = calculateVerticalDistance(rightShoulder, rightWrist)
        
        return when {
            leftDistance != null && rightDistance != null -> (leftDistance + rightDistance) / 2.0
            leftDistance != null -> leftDistance.toDouble()
            rightDistance != null -> rightDistance.toDouble()
            else -> null
        }
    }
    
    /**
     * Determine pull-up state based on shoulder-wrist distance
     */
    override fun determineState(metric: Double): ExerciseState {
        return when {
            metric < pullUpTopThreshold -> ExerciseState.START_POSITION // Top of pull-up (chin up)
            metric > pullUpBottomThreshold -> ExerciseState.END_POSITION // Bottom of pull-up (hanging)
            else -> currentState // Maintain current state in transition
        }
    }
    
    /**
     * Alternative method: Calculate elbow angle for pull-up validation
     * Can be used for form analysis or as secondary metric
     */
    private fun calculateArmAngle(pose: Pose): Double? {
        val leftAngle = calculateAngleBetweenPoints(
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        )
        
        val rightAngle = calculateAngleBetweenPoints(
            pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        )
        
        return when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2.0
            leftAngle != null -> leftAngle
            rightAngle != null -> rightAngle
            else -> null
        }
    }
}
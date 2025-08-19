package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

/**
 * Detects pull-up movements from pose data
 * Uses elbow angles to work with different grip widths (wide grip pull-ups vs narrow grip chin-ups)
 */
class PullUpDetector : ExerciseDetector(ExerciseType.PULL_UP) {
    
    // Elbow angle thresholds for pull-up detection (works for both wide and narrow grips)
    private var pullUpTopThreshold = 50.0 // Elbows bent (top position - chin up)
    private var pullUpBottomThreshold = 160.0 // Elbows extended (bottom position - hanging)
    
    /**
     * Calculate the average elbow angle (shoulder-elbow-wrist)
     * This works regardless of grip width as elbow angle remains consistent
     * Returns null if key landmarks are not detected
     */
    override fun calculateExerciseMetric(pose: Pose): Double? {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        
        val leftAngle = calculateAngleBetweenPoints(leftShoulder, leftElbow, leftWrist)
        val rightAngle = calculateAngleBetweenPoints(rightShoulder, rightElbow, rightWrist)
        
        return when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2.0
            leftAngle != null -> leftAngle
            rightAngle != null -> rightAngle
            else -> null
        }
    }
    
    /**
     * Determine pull-up state based on elbow angle
     * Smaller angles = bent elbows = top position (chin up)
     * Larger angles = extended elbows = bottom position (hanging)
     */
    override fun determineState(metric: Double): ExerciseState {
        return when {
            metric < pullUpTopThreshold -> ExerciseState.START_POSITION // Top of pull-up (elbows bent)
            metric > pullUpBottomThreshold -> ExerciseState.END_POSITION // Bottom of pull-up (elbows extended)
            else -> currentState // Maintain current state in transition
        }
    }
    
    /**
     * Alternative method: Calculate shoulder-wrist distance for reference
     * Can be used for form analysis or as secondary metric
     */
    private fun calculateShoulderWristDistance(pose: Pose): Double? {
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
     * Get default up threshold for pull-ups
     */
    override fun getDefaultUpThreshold(): Double {
        return 50.0
    }
    
    /**
     * Get default down threshold for pull-ups
     */
    override fun getDefaultDownThreshold(): Double {
        return 160.0
    }
    
    /**
     * Get current up threshold
     */
    override fun getUpThreshold(): Double {
        return pullUpTopThreshold
    }
    
    /**
     * Get current down threshold
     */
    override fun getDownThreshold(): Double {
        return pullUpBottomThreshold
    }
    
    /**
     * Update thresholds based on calibration results
     */
    override fun updateThresholds(upThreshold: Double, downThreshold: Double) {
        pullUpTopThreshold = upThreshold
        pullUpBottomThreshold = downThreshold
    }
}
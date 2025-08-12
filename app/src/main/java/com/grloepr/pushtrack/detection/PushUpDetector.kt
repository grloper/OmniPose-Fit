package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.detection.utils.AngleCalculator

/**
 * Modular push-up detector with O(1) complexity
 * Uses elbow angle as primary detection signal
 */
class PushUpDetector : BaseExerciseDetector(ExerciseType.PUSH_UP) {
    
    // Push-up specific angle thresholds
    private val downThreshold = 90.0f // Elbow angle when arms are bent (down position)
    private val upThreshold = 160.0f  // Elbow angle when arms are extended (up position)
    
    /**
     * Calculate primary angle (average elbow angle) for push-up detection
     */
    override fun calculatePrimaryAngle(pose: Pose): Float? {
        return AngleCalculator.calculateAverageElbowAngle(pose)
    }
    
    /**
     * Determine push-up phase based on elbow angle
     */
    override fun determinePhase(pose: Pose, primaryAngle: Float?): ExercisePhase {
        return if (primaryAngle != null) {
            when {
                primaryAngle < downThreshold -> ExercisePhase.DOWN
                primaryAngle > upThreshold -> ExercisePhase.UP
                else -> ExercisePhase.TRANSITIONING
            }
        } else {
            ExercisePhase.TRANSITIONING
        }
    }
    
    /**
     * Calculate confidence based on angle reliability and landmark visibility
     */
    override fun calculateConfidence(pose: Pose, primaryAngle: Float?): Float {
        if (primaryAngle == null) return 0f
        
        var confidence = 0.8f // Base confidence for angle detection
        
        // Boost confidence for fast movements
        val avgVelocity = velocityTracker.getAverageVelocity()
        confidence += (avgVelocity / 50f).coerceIn(0f, 0.2f)
        
        // Check if both arms are visible for better confidence
        val leftElbow = AngleCalculator.calculateElbowAngle(pose, true)
        val rightElbow = AngleCalculator.calculateElbowAngle(pose, false)
        if (leftElbow != null && rightElbow != null) {
            confidence += 0.1f // Both arms visible
        }
        
        return confidence.coerceIn(0f, 1f)
    }
    
    /**
     * Get detection method description
     */
    override fun getDetectionMethod(): String = "elbow_angle"
}
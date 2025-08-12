package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.detection.utils.AngleCalculator
import kotlin.math.abs

/**
 * Modular pull-up detector with O(1) complexity
 * Uses elbow angle and head-to-hands vertical distance for detection
 */
class PullUpDetector : BaseExerciseDetector(ExerciseType.PULL_UP) {
    
    // Pull-up specific angle thresholds (opposite of push-ups)
    private val downThreshold = 60.0f  // Elbow angle when pulled up (arms bent)
    private val upThreshold = 140.0f   // Elbow angle when hanging (arms extended)
    
    // Vertical distance thresholds for head-to-hands detection
    private val headHandsUpThreshold = 100f    // Head is below hands (hanging)
    private val headHandsDownThreshold = 30f   // Head is close to hands (pulled up)
    
    /**
     * Calculate primary angle (average elbow angle) for pull-up detection
     */
    override fun calculatePrimaryAngle(pose: Pose): Float? {
        return AngleCalculator.calculateAverageElbowAngle(pose)
    }
    
    /**
     * Determine pull-up phase using both elbow angle and head position
     */
    override fun determinePhase(pose: Pose, primaryAngle: Float?): ExercisePhase {
        // Primary detection using elbow angle
        val anglePhase = if (primaryAngle != null) {
            when {
                primaryAngle < downThreshold -> ExercisePhase.UP   // Arms bent = pulled up
                primaryAngle > upThreshold -> ExercisePhase.DOWN  // Arms extended = hanging
                else -> ExercisePhase.TRANSITIONING
            }
        } else {
            ExercisePhase.TRANSITIONING
        }
        
        // Secondary validation using head-to-hands distance
        val headHandsDistance = calculateHeadToHandsDistance(pose)
        val distancePhase = if (headHandsDistance != null) {
            when {
                headHandsDistance < headHandsDownThreshold -> ExercisePhase.UP   // Head close to hands
                headHandsDistance > headHandsUpThreshold -> ExercisePhase.DOWN  // Head far from hands
                else -> ExercisePhase.TRANSITIONING
            }
        } else {
            ExercisePhase.TRANSITIONING
        }
        
        // Use angle detection if available, otherwise fall back to distance
        return if (primaryAngle != null) anglePhase else distancePhase
    }
    
    /**
     * Calculate confidence based on angle reliability and landmark visibility
     */
    override fun calculateConfidence(pose: Pose, primaryAngle: Float?): Float {
        var confidence = 0f
        var factors = 0
        
        // Confidence from angle detection
        if (primaryAngle != null) {
            confidence += 0.8f
            factors++
        }
        
        // Confidence from head-hands distance
        val headHandsDistance = calculateHeadToHandsDistance(pose)
        if (headHandsDistance != null) {
            confidence += 0.6f
            factors++
        }
        
        // Average confidence from available factors
        if (factors > 0) {
            confidence /= factors
        }
        
        // Boost confidence for fast movements
        val avgVelocity = velocityTracker.getAverageVelocity()
        confidence += (avgVelocity / 50f).coerceIn(0f, 0.2f)
        
        // Check if both arms are visible for better confidence
        val leftElbow = AngleCalculator.calculateElbowAngle(pose, true)
        val rightElbow = AngleCalculator.calculateElbowAngle(pose, false)
        if (leftElbow != null && rightElbow != null) {
            confidence += 0.1f
        }
        
        return confidence.coerceIn(0f, 1f)
    }
    
    /**
     * Calculate vertical distance between head and average hand position
     * Used as secondary detection signal for pull-ups
     */
    private fun calculateHeadToHandsDistance(pose: Pose): Float? {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        
        if (nose == null) return null
        
        // Calculate average hand position
        val handY = when {
            leftWrist != null && rightWrist != null -> {
                (leftWrist.position.y + rightWrist.position.y) / 2f
            }
            leftWrist != null -> leftWrist.position.y
            rightWrist != null -> rightWrist.position.y
            else -> return null
        }
        
        // Return vertical distance (positive when head is below hands)
        return abs(handY - nose.position.y)
    }
    
    /**
     * Get detection method description
     */
    override fun getDetectionMethod(): String {
        return if (lastPrimaryAngle != null) {
            "elbow_angle"
        } else {
            "head_hands_distance"
        }
    }
}
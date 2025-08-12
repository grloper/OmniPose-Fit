package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.detection.utils.AngleCalculator
import kotlin.math.abs

/**
 * Modular squat detector with O(1) complexity
 * Uses knee angle and hip height for detection
 */
class SquatDetector : BaseExerciseDetector(ExerciseType.SQUAT) {
    
    // Squat specific angle thresholds
    private val downThreshold = 110.0f // Knee angle when squatting (bent legs)
    private val upThreshold = 160.0f   // Knee angle when standing (straight legs)
    
    // Hip height tracking for secondary detection
    private var baselineHipHeight: Float? = null
    private var minHipHeight: Float = Float.MAX_VALUE
    private var maxHipHeight: Float = Float.MIN_VALUE
    
    // Hip height thresholds (normalized 0-1 scale)
    private val hipDownThreshold = 0.3f  // Hip is low (squatting)
    private val hipUpThreshold = 0.8f    // Hip is high (standing)
    
    /**
     * Calculate primary angle (average knee angle) for squat detection
     */
    override fun calculatePrimaryAngle(pose: Pose): Float? {
        return AngleCalculator.calculateAverageKneeAngle(pose)
    }
    
    /**
     * Determine squat phase using knee angle and hip height
     */
    override fun determinePhase(pose: Pose, primaryAngle: Float?): ExercisePhase {
        // Primary detection using knee angle
        val anglePhase = if (primaryAngle != null) {
            when {
                primaryAngle < downThreshold -> ExercisePhase.DOWN // Knees bent = squatting
                primaryAngle > upThreshold -> ExercisePhase.UP     // Knees straight = standing
                else -> ExercisePhase.TRANSITIONING
            }
        } else {
            ExercisePhase.TRANSITIONING
        }
        
        // Secondary validation using hip height
        val normalizedHipHeight = calculateNormalizedHipHeight(pose)
        val hipPhase = if (normalizedHipHeight != null) {
            when {
                normalizedHipHeight < hipDownThreshold -> ExercisePhase.DOWN // Hip low = squatting
                normalizedHipHeight > hipUpThreshold -> ExercisePhase.UP     // Hip high = standing
                else -> ExercisePhase.TRANSITIONING
            }
        } else {
            ExercisePhase.TRANSITIONING
        }
        
        // Use knee angle if available, otherwise fall back to hip height
        return if (primaryAngle != null) anglePhase else hipPhase
    }
    
    /**
     * Calculate confidence based on angle reliability and landmark visibility
     */
    override fun calculateConfidence(pose: Pose, primaryAngle: Float?): Float {
        var confidence = 0f
        var factors = 0
        
        // Confidence from knee angle detection
        if (primaryAngle != null) {
            confidence += 0.8f
            factors++
        }
        
        // Confidence from hip height detection
        val normalizedHipHeight = calculateNormalizedHipHeight(pose)
        if (normalizedHipHeight != null) {
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
        
        // Check if both legs are visible for better confidence
        val leftKnee = AngleCalculator.calculateKneeAngle(pose, true)
        val rightKnee = AngleCalculator.calculateKneeAngle(pose, false)
        if (leftKnee != null && rightKnee != null) {
            confidence += 0.1f
        }
        
        return confidence.coerceIn(0f, 1f)
    }
    
    /**
     * Calculate normalized hip height (0 = lowest, 1 = highest)
     * Used as secondary detection signal for squats
     */
    private fun calculateNormalizedHipHeight(pose: Pose): Float? {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        
        // Calculate average hip position
        val currentHipHeight = when {
            leftHip != null && rightHip != null -> {
                (leftHip.position.y + rightHip.position.y) / 2f
            }
            leftHip != null -> leftHip.position.y
            rightHip != null -> rightHip.position.y
            else -> return null
        }
        
        // Initialize baseline if first measurement
        if (baselineHipHeight == null) {
            baselineHipHeight = currentHipHeight
            minHipHeight = currentHipHeight
            maxHipHeight = currentHipHeight
            return 0.5f // Neutral position
        }
        
        // Update min/max with dampening to prevent drift
        val dampening = 0.95f
        minHipHeight = minHipHeight * dampening + currentHipHeight * (1f - dampening)
        maxHipHeight = maxHipHeight * dampening + currentHipHeight * (1f - dampening)
        
        // Ensure min < max
        if (minHipHeight >= maxHipHeight) {
            val range = abs(baselineHipHeight!! - currentHipHeight)
            minHipHeight = currentHipHeight - range
            maxHipHeight = currentHipHeight + range
        }
        
        // Normalize to 0-1 range (inverted because lower Y = higher position)
        val range = maxHipHeight - minHipHeight
        return if (range > 0) {
            1f - ((currentHipHeight - minHipHeight) / range)
        } else {
            0.5f
        }
    }
    
    /**
     * Reset squat-specific state
     */
    override fun reset() {
        super.reset()
        baselineHipHeight = null
        minHipHeight = Float.MAX_VALUE
        maxHipHeight = Float.MIN_VALUE
    }
    
    /**
     * Get detection method description
     */
    override fun getDetectionMethod(): String {
        return if (lastPrimaryAngle != null) {
            "knee_angle"
        } else {
            "hip_height"
        }
    }
}
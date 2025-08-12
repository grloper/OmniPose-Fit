package com.grloepr.pushtrack.detection.utils

import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.detection.ExercisePhase
import com.grloepr.pushtrack.detection.ExerciseType
import com.grloepr.pushtrack.feedback.PostureFeedback

/**
 * Enhanced posture analyzer supporting multiple exercise types
 * Uses O(1) complexity with fixed-size quality tracking
 */
class EnhancedPostureAnalyzer {
    
    // O(1) form quality tracking with circular buffer
    private val qualityBuffer = FloatArray(10) // Fixed size buffer
    private var bufferIndex = 0
    private var bufferFull = false
    private var averageQuality = 0f
    
    // Timing for feedback throttling
    private var lastFeedbackTime = 0L
    private val feedbackCooldownMs = 3000L // 3 seconds between feedback
    
    /**
     * Analyze pose for any exercise type and return enhanced feedback
     * @param pose The detected pose
     * @param exerciseType The type of exercise being performed
     * @param currentPhase The current phase of the exercise
     * @return Enhanced posture analysis result
     */
    fun analyzePose(
        pose: Pose, 
        exerciseType: ExerciseType, 
        currentPhase: ExercisePhase
    ): EnhancedPostureAnalysisResult {
        val timestamp = System.currentTimeMillis()
        
        // Calculate form quality based on exercise type
        val formQuality = when (exerciseType) {
            ExerciseType.PUSH_UP -> calculatePushUpFormQuality(pose)
            ExerciseType.PULL_UP -> calculatePullUpFormQuality(pose)
            ExerciseType.SQUAT -> calculateSquatFormQuality(pose)
        }
        
        // Update quality history with O(1) circular buffer
        updateFormQualityHistory(formQuality)
        
        // Determine if feedback should be given
        val feedback = if (timestamp - lastFeedbackTime > feedbackCooldownMs) {
            analyzeFormIssues(pose, exerciseType, currentPhase)?.also {
                lastFeedbackTime = timestamp
            }
        } else null
        
        return EnhancedPostureAnalysisResult(
            formQuality = formQuality,
            averageFormQuality = averageQuality,
            feedback = feedback,
            hasGoodForm = formQuality >= 75f,
            exerciseType = exerciseType
        )
    }
    
    /**
     * Calculate form quality for push-ups
     */
    private fun calculatePushUpFormQuality(pose: Pose): Float {
        var score = 100f
        var factors = 0
        
        // Check arm angle symmetry
        val leftElbow = AngleCalculator.calculateElbowAngle(pose, true)
        val rightElbow = AngleCalculator.calculateElbowAngle(pose, false)
        
        if (leftElbow != null && rightElbow != null) {
            val angleDifference = kotlin.math.abs(leftElbow - rightElbow)
            if (angleDifference > 20) {
                score -= (angleDifference - 20) * 2 // Penalize asymmetry
            }
            factors++
        }
        
        // Check body alignment
        val alignmentScore = calculateBodyAlignment(pose)
        if (alignmentScore != null) {
            score += (alignmentScore - 50) * 0.5f
            factors++
        }
        
        return if (factors > 0) score.coerceIn(0f, 100f) else 50f
    }
    
    /**
     * Calculate form quality for pull-ups
     */
    private fun calculatePullUpFormQuality(pose: Pose): Float {
        var score = 100f
        var factors = 0
        
        // Check arm angle symmetry
        val leftElbow = AngleCalculator.calculateElbowAngle(pose, true)
        val rightElbow = AngleCalculator.calculateElbowAngle(pose, false)
        
        if (leftElbow != null && rightElbow != null) {
            val angleDifference = kotlin.math.abs(leftElbow - rightElbow)
            if (angleDifference > 15) {
                score -= (angleDifference - 15) * 2.5f // Stricter for pull-ups
            }
            factors++
        }
        
        // Check body straightness (important for pull-ups)
        val bodyAlignment = calculateBodyAlignment(pose)
        if (bodyAlignment != null) {
            score += (bodyAlignment - 50) * 0.8f // More weight on alignment
            factors++
        }
        
        return if (factors > 0) score.coerceIn(0f, 100f) else 50f
    }
    
    /**
     * Calculate form quality for squats
     */
    private fun calculateSquatFormQuality(pose: Pose): Float {
        var score = 100f
        var factors = 0
        
        // Check leg angle symmetry
        val leftKnee = AngleCalculator.calculateKneeAngle(pose, true)
        val rightKnee = AngleCalculator.calculateKneeAngle(pose, false)
        
        if (leftKnee != null && rightKnee != null) {
            val angleDifference = kotlin.math.abs(leftKnee - rightKnee)
            if (angleDifference > 25) {
                score -= (angleDifference - 25) * 1.5f // Penalize leg asymmetry
            }
            factors++
        }
        
        // Check torso alignment (important for squats)
        val torsoAlignment = calculateTorsoAlignment(pose)
        if (torsoAlignment != null) {
            score += (torsoAlignment - 50) * 0.6f
            factors++
        }
        
        return if (factors > 0) score.coerceIn(0f, 100f) else 50f
    }
    
    /**
     * Analyze for specific form issues based on exercise type
     */
    private fun analyzeFormIssues(
        pose: Pose, 
        exerciseType: ExerciseType, 
        currentPhase: ExercisePhase
    ): PostureFeedback? {
        return when (exerciseType) {
            ExerciseType.PUSH_UP -> analyzePushUpIssues(pose, currentPhase)
            ExerciseType.PULL_UP -> analyzePullUpIssues(pose, currentPhase)
            ExerciseType.SQUAT -> analyzeSquatIssues(pose, currentPhase)
        }
    }
    
    /**
     * Analyze push-up specific form issues
     */
    private fun analyzePushUpIssues(pose: Pose, currentPhase: ExercisePhase): PostureFeedback? {
        val avgElbowAngle = AngleCalculator.calculateAverageElbowAngle(pose)
        
        avgElbowAngle?.let { angle ->
            when (currentPhase) {
                ExercisePhase.DOWN -> {
                    if (angle > 110) return PostureFeedback.LOWER_BODY
                }
                ExercisePhase.UP -> {
                    if (angle < 140) return PostureFeedback.RAISE_BODY
                }
                else -> {}
            }
        }
        
        // Check body alignment
        val alignmentScore = calculateBodyAlignment(pose)
        if (alignmentScore != null && alignmentScore < 30) {
            return PostureFeedback.STRAIGHTEN_BACK
        }
        
        // Give positive feedback for good form
        val formQuality = calculatePushUpFormQuality(pose)
        if (formQuality >= 85) {
            return PostureFeedback.GOOD_FORM
        }
        
        return null
    }
    
    /**
     * Analyze pull-up specific form issues
     */
    private fun analyzePullUpIssues(pose: Pose, currentPhase: ExercisePhase): PostureFeedback? {
        val avgElbowAngle = AngleCalculator.calculateAverageElbowAngle(pose)
        
        avgElbowAngle?.let { angle ->
            when (currentPhase) {
                ExercisePhase.UP -> {
                    if (angle > 80) return PostureFeedback.RAISE_BODY // Pull higher
                }
                ExercisePhase.DOWN -> {
                    if (angle < 120) return PostureFeedback.LOWER_BODY // Lower fully
                }
                else -> {}
            }
        }
        
        // Check for swinging/kipping
        val bodyAlignment = calculateBodyAlignment(pose)
        if (bodyAlignment != null && bodyAlignment < 40) {
            return PostureFeedback.STRAIGHTEN_BACK
        }
        
        // Give positive feedback for good form
        val formQuality = calculatePullUpFormQuality(pose)
        if (formQuality >= 85) {
            return PostureFeedback.GOOD_FORM
        }
        
        return null
    }
    
    /**
     * Analyze squat specific form issues
     */
    private fun analyzeSquatIssues(pose: Pose, currentPhase: ExercisePhase): PostureFeedback? {
        val avgKneeAngle = AngleCalculator.calculateAverageKneeAngle(pose)
        
        avgKneeAngle?.let { angle ->
            when (currentPhase) {
                ExercisePhase.DOWN -> {
                    if (angle > 120) return PostureFeedback.LOWER_BODY // Squat deeper
                }
                ExercisePhase.UP -> {
                    if (angle < 150) return PostureFeedback.RAISE_BODY // Stand fully
                }
                else -> {}
            }
        }
        
        // Check torso alignment
        val torsoAlignment = calculateTorsoAlignment(pose)
        if (torsoAlignment != null && torsoAlignment < 35) {
            return PostureFeedback.STRAIGHTEN_BACK
        }
        
        // Give positive feedback for good form
        val formQuality = calculateSquatFormQuality(pose)
        if (formQuality >= 85) {
            return PostureFeedback.GOOD_FORM
        }
        
        return null
    }
    
    /**
     * Calculate general body alignment score (0-100)
     */
    private fun calculateBodyAlignment(pose: Pose): Float? {
        // Implementation similar to original PostureAnalyzer
        // but optimized for O(1) operation
        return 75f // Simplified for now
    }
    
    /**
     * Calculate torso-specific alignment for squats
     */
    private fun calculateTorsoAlignment(pose: Pose): Float? {
        // Implementation for squat-specific torso checking
        return 70f // Simplified for now
    }
    
    /**
     * Update form quality history with O(1) circular buffer
     */
    private fun updateFormQualityHistory(quality: Float) {
        qualityBuffer[bufferIndex] = quality
        bufferIndex = (bufferIndex + 1) % qualityBuffer.size
        
        if (bufferIndex == 0) {
            bufferFull = true
        }
        
        // Calculate average from buffer
        val count = if (bufferFull) qualityBuffer.size else bufferIndex
        var sum = 0f
        for (i in 0 until count) {
            sum += qualityBuffer[i]
        }
        averageQuality = if (count > 0) sum / count else quality
    }
    
    /**
     * Reset analysis state
     */
    fun reset() {
        bufferIndex = 0
        bufferFull = false
        averageQuality = 0f
        lastFeedbackTime = 0L
        
        // Clear buffer
        for (i in qualityBuffer.indices) {
            qualityBuffer[i] = 0f
        }
    }
    
    /**
     * Get form quality as star rating (1-5 stars)
     */
    fun getStarRating(quality: Float): Int {
        return when {
            quality >= 90 -> 5
            quality >= 75 -> 4
            quality >= 60 -> 3
            quality >= 40 -> 2
            else -> 1
        }
    }
}

/**
 * Enhanced result of posture analysis with exercise type
 */
data class EnhancedPostureAnalysisResult(
    val formQuality: Float, // 0-100 score
    val averageFormQuality: Float, // Average over recent history
    val feedback: PostureFeedback?, // Specific feedback to give, if any
    val hasGoodForm: Boolean, // Quick check for good form
    val exerciseType: ExerciseType // Type of exercise being analyzed
)
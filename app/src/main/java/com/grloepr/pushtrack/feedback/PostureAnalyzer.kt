package com.grloepr.pushtrack.feedback

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.PushUpState
import kotlin.math.*

/**
 * Analyzes pose data to provide form feedback and quality scoring
 */
class PostureAnalyzer {
    
    // Form quality tracking
    private var formQualityHistory = mutableListOf<Float>()
    private val maxHistorySize = 10
    
    // Timing for feedback throttling
    private var lastFeedbackTime = 0L
    private val feedbackCooldownMs = 3000L // 3 seconds between feedback
    
    /**
     * Analyze pose and return posture feedback
     */
    fun analyzePose(pose: Pose, currentState: PushUpState): PostureAnalysisResult {
        val timestamp = System.currentTimeMillis()
        
        // Calculate form quality score (0-100)
        val formQuality = calculateFormQuality(pose)
        
        // Update form quality history
        updateFormQualityHistory(formQuality)
        
        // Determine if feedback should be given
        val feedback = if (timestamp - lastFeedbackTime > feedbackCooldownMs) {
            analyzeFormIssues(pose, currentState)?.also {
                lastFeedbackTime = timestamp
            }
        } else null
        
        return PostureAnalysisResult(
            formQuality = formQuality,
            averageFormQuality = formQualityHistory.average().toFloat(),
            feedback = feedback,
            hasGoodForm = formQuality >= 75f
        )
    }
    
    /**
     * Calculate overall form quality score (0-100)
     */
    private fun calculateFormQuality(pose: Pose): Float {
        var score = 100f
        var factors = 0
        
        // Check arm angles symmetry
        val leftArmAngle = calculateArmAngle(
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        )
        
        val rightArmAngle = calculateArmAngle(
            pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        )
        
        if (leftArmAngle != null && rightArmAngle != null) {
            val angleDifference = abs(leftArmAngle - rightArmAngle)
            if (angleDifference > 20) {
                score -= (angleDifference - 20) * 2 // Penalize asymmetry
            }
            factors++
        }
        
        // Check body alignment (spine straightness)
        val alignmentScore = calculateBodyAlignment(pose)
        if (alignmentScore != null) {
            score += (alignmentScore - 50) * 0.5f // Adjust based on alignment
            factors++
        }
        
        // Check hand positioning
        val handPositionScore = calculateHandPositioning(pose)
        if (handPositionScore != null) {
            score += (handPositionScore - 50) * 0.3f
            factors++
        }
        
        return if (factors > 0) score.coerceIn(0f, 100f) else 50f
    }
    
    /**
     * Analyze for specific form issues that need feedback
     */
    private fun analyzeFormIssues(pose: Pose, currentState: PushUpState): PostureFeedback? {
        // Check arm angles for incomplete range of motion
        val leftArmAngle = calculateArmAngle(
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        )
        
        val rightArmAngle = calculateArmAngle(
            pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        )
        
        val avgArmAngle = when {
            leftArmAngle != null && rightArmAngle != null -> (leftArmAngle + rightArmAngle) / 2
            leftArmAngle != null -> leftArmAngle
            rightArmAngle != null -> rightArmAngle
            else -> null
        }
        
        avgArmAngle?.let { angle ->
            when (currentState) {
                PushUpState.DOWN_POSITION -> {
                    if (angle > 110) { // Not low enough
                        return PostureFeedback.LOWER_BODY
                    }
                }
                PushUpState.UP_POSITION -> {
                    if (angle < 140) { // Not high enough
                        return PostureFeedback.RAISE_BODY
                    }
                }
                else -> {}
            }
        }
        
        // Check body alignment
        val alignmentScore = calculateBodyAlignment(pose)
        if (alignmentScore != null && alignmentScore < 30) {
            return PostureFeedback.STRAIGHTEN_BACK
        }
        
        // Check arm symmetry
        if (leftArmAngle != null && rightArmAngle != null) {
            val angleDifference = abs(leftArmAngle - rightArmAngle)
            if (angleDifference > 30) {
                return PostureFeedback.ALIGN_HANDS
            }
        }
        
        // Give positive feedback for good form
        val formQuality = calculateFormQuality(pose)
        if (formQuality >= 85) {
            return PostureFeedback.GOOD_FORM
        }
        
        return null
    }
    
    /**
     * Calculate body alignment score (0-100)
     * Measures how straight the spine is from head to hips
     */
    private fun calculateBodyAlignment(pose: Pose): Float? {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        
        if (nose == null || leftShoulder == null || rightShoulder == null || 
            leftHip == null || rightHip == null) return null
        
        // Calculate center points
        val shoulderCenter = PointF(
            (leftShoulder.position.x + rightShoulder.position.x) / 2,
            (leftShoulder.position.y + rightShoulder.position.y) / 2
        )
        
        val hipCenter = PointF(
            (leftHip.position.x + rightHip.position.x) / 2,
            (leftHip.position.y + rightHip.position.y) / 2
        )
        
        // Calculate deviation from straight line
        val expectedY = nose.position.y + 
            (hipCenter.y - nose.position.y) * 
            (shoulderCenter.x - nose.position.x) / (hipCenter.x - nose.position.x)
        
        val deviation = abs(shoulderCenter.y - expectedY)
        val maxDeviation = 100f // Pixels - adjust based on image resolution
        
        return ((maxDeviation - deviation) / maxDeviation * 100).coerceIn(0f, 100f)
    }
    
    /**
     * Calculate hand positioning score
     */
    private fun calculateHandPositioning(pose: Pose): Float? {
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        
        if (leftWrist == null || rightWrist == null || leftShoulder == null || rightShoulder == null) {
            return null
        }
        
        // Ideal hand position is roughly shoulder-width apart
        val handDistance = distance(leftWrist.position, rightWrist.position)
        val shoulderDistance = distance(leftShoulder.position, rightShoulder.position)
        
        val ratio = handDistance / shoulderDistance
        val idealRatio = 1.2f // Slightly wider than shoulders
        
        val deviation = abs(ratio - idealRatio)
        return ((1.0f - deviation) * 100).coerceIn(0f, 100f)
    }
    
    /**
     * Calculate arm angle (shoulder-elbow-wrist)
     */
    private fun calculateArmAngle(
        shoulder: PoseLandmark?,
        elbow: PoseLandmark?,
        wrist: PoseLandmark?
    ): Float? {  // Changed return type from Double? to Float?
        if (shoulder == null || elbow == null || wrist == null) return null
        
        // Check confidence
        if (shoulder.inFrameLikelihood < 0.5f || 
            elbow.inFrameLikelihood < 0.5f || 
            wrist.inFrameLikelihood < 0.5f) {
            return null
        }
        
        val shoulderPos = shoulder.position
        val elbowPos = elbow.position
        val wristPos = wrist.position
        
        // Vector from elbow to shoulder
        val v1x = shoulderPos.x - elbowPos.x
        val v1y = shoulderPos.y - elbowPos.y
        
        // Vector from elbow to wrist
        val v2x = wristPos.x - elbowPos.x
        val v2y = wristPos.y - elbowPos.y
        
        // Calculate angle using dot product
        val dotProduct = v1x * v2x + v1y * v2y
        val magnitude1 = sqrt(v1x * v1x + v1y * v1y)
        val magnitude2 = sqrt(v2x * v2x + v2y * v2y)
        
        if (magnitude1 == 0.0f || magnitude2 == 0.0f) return null
        
        val cosAngle = dotProduct / (magnitude1 * magnitude2)
        val clampedCosAngle = cosAngle.coerceIn(-1.0f, 1.0f)
        
        // Convert the Double result to Float before returning
        return Math.toDegrees(acos(clampedCosAngle.toDouble())).toFloat()
    }
    
    /**
     * Calculate distance between two points
     */
    private fun distance(p1: android.graphics.PointF, p2: android.graphics.PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Update form quality history for averaging
     */
    private fun updateFormQualityHistory(quality: Float) {
        formQualityHistory.add(quality)
        if (formQualityHistory.size > maxHistorySize) {
            formQualityHistory.removeAt(0)
        }
    }
    
    /**
     * Reset analysis state
     */
    fun reset() {
        formQualityHistory.clear()
        lastFeedbackTime = 0L
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
 * Result of posture analysis
 */
data class PostureAnalysisResult(
    val formQuality: Float, // 0-100 score
    val averageFormQuality: Float, // Average over recent history
    val feedback: PostureFeedback?, // Specific feedback to give, if any
    val hasGoodForm: Boolean // Quick check for good form
)

/**
 * Simple PointF for calculations
 */
data class PointF(val x: Float, val y: Float)
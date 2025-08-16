package com.grloepr.pushtrack.feedback

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.PushUpState
import com.grloepr.pushtrack.domain.ExerciseState
import com.grloepr.pushtrack.domain.ExerciseType
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
     * Analyze pose and return posture feedback (legacy method for push-ups)
     */
    fun analyzePose(pose: Pose, currentState: PushUpState): PostureAnalysisResult {
        val exerciseState = when (currentState) {
            PushUpState.UP_POSITION -> ExerciseState.START_POSITION
            PushUpState.DOWN_POSITION -> ExerciseState.END_POSITION
            PushUpState.UNKNOWN -> ExerciseState.UNKNOWN
        }
        return analyzePose(pose, exerciseState, ExerciseType.PUSH_UP)
    }
    
    /**
     * Analyze pose and return posture feedback for any exercise type
     */
    fun analyzePose(pose: Pose, currentState: ExerciseState, exerciseType: ExerciseType): PostureAnalysisResult {
        val timestamp = System.currentTimeMillis()
        
        // Calculate form quality score (0-100)
        val formQuality = calculateFormQuality(pose, exerciseType, currentState)
        
        // Update form quality history
        updateFormQualityHistory(formQuality)
        
        // Determine if feedback should be given
        val feedback = if (timestamp - lastFeedbackTime > feedbackCooldownMs) {
            analyzeFormIssues(pose, currentState, exerciseType)?.also {
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
     * Calculate overall form quality score (0-100) for specific exercise
     */
    private fun calculateFormQuality(pose: Pose, exerciseType: ExerciseType, currentState: ExerciseState): Float {
        return when (exerciseType) {
            ExerciseType.PUSH_UP -> calculatePushUpFormQuality(pose)
            ExerciseType.PULL_UP -> calculatePullUpFormQuality(pose, currentState)
            ExerciseType.SQUAT -> calculateSquatFormQuality(pose, currentState)
        }
    }
    
    /**
     * Calculate overall form quality score (0-100) for push-ups (legacy method)
     */
    private fun calculateFormQuality(pose: Pose): Float {
        return calculatePushUpFormQuality(pose)
    }
    
    /**
     * Calculate form quality for push-ups
     */
    private fun calculatePushUpFormQuality(pose: Pose): Float {
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
     * Calculate form quality for pull-ups
     */
    private fun calculatePullUpFormQuality(pose: Pose, currentState: ExerciseState): Float {
        var score = 80f // Base score
        var factors = 0
        
        // Check arm symmetry
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
            if (angleDifference > 25) {
                score -= (angleDifference - 25) * 1.5f // Penalize asymmetry
            }
            factors++
        }
        
        // Check if user is pulling up adequately
        if (currentState == ExerciseState.END_POSITION) {
            if (leftArmAngle != null && leftArmAngle > 120) {
                score -= 15f // Not pulling up enough
            }
        }
        
        return if (factors > 0) score.coerceIn(0f, 100f) else 70f
    }
    
    /**
     * Calculate form quality for squats
     */
    private fun calculateSquatFormQuality(pose: Pose, currentState: ExerciseState): Float {
        var score = 85f // Base score
        var factors = 0
        
        // Check leg symmetry
        val leftHipKneeAngle = calculateHipKneeAngle(pose, true)
        val rightHipKneeAngle = calculateHipKneeAngle(pose, false)
        
        if (leftHipKneeAngle != null && rightHipKneeAngle != null) {
            val angleDifference = abs(leftHipKneeAngle - rightHipKneeAngle)
            if (angleDifference > 15) {
                score -= (angleDifference - 15) * 2f // Penalize asymmetry
            }
            factors++
        }
        
        // Check squat depth when in squat position
        if (currentState == ExerciseState.END_POSITION) {
            val avgAngle = when {
                leftHipKneeAngle != null && rightHipKneeAngle != null -> (leftHipKneeAngle + rightHipKneeAngle) / 2
                leftHipKneeAngle != null -> leftHipKneeAngle
                rightHipKneeAngle != null -> rightHipKneeAngle
                else -> null
            }
            
            avgAngle?.let { angle ->
                if (angle > 130) { // Not deep enough
                    score -= (angle - 130) * 0.5f
                }
                factors++
            }
        }
        
        return if (factors > 0) score.coerceIn(0f, 100f) else 75f
    }
    
    private fun calculateHipKneeAngle(pose: Pose, isLeftLeg: Boolean): Float? {
        val hipLandmark = if (isLeftLeg) PoseLandmark.LEFT_HIP else PoseLandmark.RIGHT_HIP
        val kneeLandmark = if (isLeftLeg) PoseLandmark.LEFT_KNEE else PoseLandmark.RIGHT_KNEE
        val ankleLandmark = if (isLeftLeg) PoseLandmark.LEFT_ANKLE else PoseLandmark.RIGHT_ANKLE
        
        val hip = pose.getPoseLandmark(hipLandmark)
        val knee = pose.getPoseLandmark(kneeLandmark)
        val ankle = pose.getPoseLandmark(ankleLandmark)
        
        if (hip == null || knee == null || ankle == null) return null
        
        if (hip.inFrameLikelihood < 0.5f || knee.inFrameLikelihood < 0.5f || ankle.inFrameLikelihood < 0.5f) {
            return null
        }
        
        // Vector from knee to hip
        val v1x = hip.position.x - knee.position.x
        val v1y = hip.position.y - knee.position.y
        
        // Vector from knee to ankle
        val v2x = ankle.position.x - knee.position.x
        val v2y = ankle.position.y - knee.position.y
        
        // Calculate angle using dot product
        val dotProduct = v1x * v2x + v1y * v2y
        val magnitude1 = sqrt(v1x * v1x + v1y * v1y)
        val magnitude2 = sqrt(v2x * v2x + v2y * v2y)
        
        if (magnitude1 == 0f || magnitude2 == 0f) return null
        
        val cosAngle = dotProduct / (magnitude1 * magnitude2)
        val clampedCosAngle = cosAngle.coerceIn(-1f, 1f)
        
        return Math.toDegrees(acos(clampedCosAngle.toDouble())).toFloat()
    }
    
    /**
     * Analyze for specific form issues that need feedback (legacy method for push-ups)
     */
    private fun analyzeFormIssues(pose: Pose, currentState: PushUpState): PostureFeedback? {
        val exerciseState = when (currentState) {
            PushUpState.UP_POSITION -> ExerciseState.START_POSITION
            PushUpState.DOWN_POSITION -> ExerciseState.END_POSITION
            PushUpState.UNKNOWN -> ExerciseState.UNKNOWN
        }
        return analyzeFormIssues(pose, exerciseState, ExerciseType.PUSH_UP)
    }
    
    /**
     * Analyze for specific form issues that need feedback for any exercise
     */
    private fun analyzeFormIssues(pose: Pose, currentState: ExerciseState, exerciseType: ExerciseType): PostureFeedback? {
        return when (exerciseType) {
            ExerciseType.PUSH_UP -> analyzePushUpFormIssues(pose, currentState)
            ExerciseType.PULL_UP -> analyzePullUpFormIssues(pose, currentState)
            ExerciseType.SQUAT -> analyzeSquatFormIssues(pose, currentState)
        }
    }
    
    /**
     * Analyze form issues specific to push-ups
     */
    private fun analyzePushUpFormIssues(pose: Pose, currentState: ExerciseState): PostureFeedback? {
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
                ExerciseState.END_POSITION -> { // Down position
                    if (angle > 110) { // Not low enough
                        return PostureFeedback.LOWER_BODY
                    }
                }
                ExerciseState.START_POSITION -> { // Up position
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
        val formQuality = calculatePushUpFormQuality(pose)
        if (formQuality >= 85) {
            return PostureFeedback.GOOD_FORM
        }
        
        return null
    }
    
    /**
     * Analyze form issues specific to pull-ups
     */
    private fun analyzePullUpFormIssues(pose: Pose, currentState: ExerciseState): PostureFeedback? {
        // For pull-ups, focus on arm symmetry and pulling height
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
        
        // Check arm symmetry
        if (leftArmAngle != null && rightArmAngle != null) {
            val angleDifference = abs(leftArmAngle - rightArmAngle)
            if (angleDifference > 25) {
                return PostureFeedback.ALIGN_HANDS // Reuse for arm symmetry
            }
        }
        
        // Check if pulling up enough when in top position
        if (currentState == ExerciseState.END_POSITION) {
            val avgAngle = when {
                leftArmAngle != null && rightArmAngle != null -> (leftArmAngle + rightArmAngle) / 2
                leftArmAngle != null -> leftArmAngle
                rightArmAngle != null -> rightArmAngle
                else -> null
            }
            
            if (avgAngle != null && avgAngle > 120) {
                return PostureFeedback.RAISE_BODY // Pull up higher
            }
        }
        
        return PostureFeedback.GOOD_FORM
    }
    
    /**
     * Analyze form issues specific to squats
     */
    private fun analyzeSquatFormIssues(pose: Pose, currentState: ExerciseState): PostureFeedback? {
        val leftHipKneeAngle = calculateHipKneeAngle(pose, true)
        val rightHipKneeAngle = calculateHipKneeAngle(pose, false)
        
        // Check leg symmetry
        if (leftHipKneeAngle != null && rightHipKneeAngle != null) {
            val angleDifference = abs(leftHipKneeAngle - rightHipKneeAngle)
            if (angleDifference > 20) {
                return PostureFeedback.ALIGN_HANDS // Reuse for leg symmetry
            }
        }
        
        // Check squat depth when in squat position
        if (currentState == ExerciseState.END_POSITION) {
            val avgAngle = when {
                leftHipKneeAngle != null && rightHipKneeAngle != null -> (leftHipKneeAngle + rightHipKneeAngle) / 2
                leftHipKneeAngle != null -> leftHipKneeAngle
                rightHipKneeAngle != null -> rightHipKneeAngle
                else -> null
            }
            
            if (avgAngle != null && avgAngle > 130) {
                return PostureFeedback.LOWER_BODY // Squat deeper
            }
            
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
    ): Float? {  // Changed from Double? to Float?
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
        
        // Convert Double to Float
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
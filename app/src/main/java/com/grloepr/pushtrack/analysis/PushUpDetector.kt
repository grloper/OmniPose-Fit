package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.feedback.PostureAnalyzer
import com.grloepr.pushtrack.feedback.PostureAnalysisResult
import kotlin.math.*

/**
 * Push-up detection states (maintained for backward compatibility)
 */
enum class PushUpState {
    UNKNOWN,
    UP_POSITION,
    DOWN_POSITION
}

/**
 * Enhanced push-up detection with form analysis (maintained for backward compatibility)
 */
data class PushUpResult(
    val repCount: Int,
    val currentState: PushUpState,
    val postureAnalysis: PostureAnalysisResult?
)

/**
 * Detects push-up movements from pose data
 * Now extends ExerciseDetector for consistency with other exercise types
 */
class PushUpDetector : ExerciseDetector(ExerciseType.PUSH_UP) {
    
    // Maintain legacy state for backward compatibility
    private var legacyCurrentState = PushUpState.UNKNOWN
    
    // Default angle thresholds for push-up detection
    private val defaultDownThreshold = 90.0 // degrees - elbow angle when in down position
    private val defaultUpThreshold = 160.0 // degrees - elbow angle when in up position
    
    /**
     * Get default thresholds
     */
    override fun getDefaultUpThreshold(): Double = defaultUpThreshold
    override fun getDefaultDownThreshold(): Double = defaultDownThreshold
    
    /**
     * Process a pose and update push-up count with form analysis (legacy method)
     * @param pose The detected pose
     * @return PushUpResult with count and analysis
     */
    fun processPushUpWithAnalysis(pose: Pose): PushUpResult {
        // Use the base class method and convert to legacy format
        val exerciseResult = super.processPoseWithAnalysis(pose)
        
        // Update legacy state
        legacyCurrentState = when (exerciseResult.currentState) {
            ExerciseState.START_POSITION -> PushUpState.UP_POSITION
            ExerciseState.END_POSITION -> PushUpState.DOWN_POSITION
            ExerciseState.UNKNOWN -> PushUpState.UNKNOWN
        }
        
        return PushUpResult(
            repCount = exerciseResult.repCount,
            currentState = legacyCurrentState,
            postureAnalysis = exerciseResult.postureAnalysis
        )
    }
    
    /**
     * Calculate the average arm angle (shoulder-elbow-wrist) for both arms
     * Returns null if key landmarks are not detected
     */
    override fun calculateExerciseMetric(pose: Pose): Double? {
        val leftAngle = calculateSingleArmAngle(
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        )
        
        val rightAngle = calculateSingleArmAngle(
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
    
    /**
     * Determine push-up state based on arm angle
     */
    override fun determineState(metric: Double): ExerciseState {
        return when {
            metric < getDownThreshold() -> ExerciseState.END_POSITION // Down position
            metric > getUpThreshold() -> ExerciseState.START_POSITION // Up position
            else -> currentState // Maintain current state in transition
        }
    }
    
    /**
     * Calculate angle between three points (shoulder-elbow-wrist)
     */
    private fun calculateSingleArmAngle(
        shoulder: PoseLandmark?,
        elbow: PoseLandmark?,
        wrist: PoseLandmark?
    ): Double? {
        return calculateAngleBetweenPoints(shoulder, elbow, wrist)
    }
    
    /**
     * Reset the legacy state and call parent's reset method
     */
    fun resetState() {
        super.reset()
        legacyCurrentState = PushUpState.UNKNOWN
    }
    
    /**
     * Get legacy push-up state
     */
    fun getLegacyState(): PushUpState = legacyCurrentState
}
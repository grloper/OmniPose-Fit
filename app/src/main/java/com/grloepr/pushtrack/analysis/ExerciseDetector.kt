package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.feedback.PostureAnalyzer
import com.grloepr.pushtrack.feedback.PostureAnalysisResult
import kotlin.math.*

/**
 * Exercise types supported by the app
 */
enum class ExerciseType {
    PUSH_UP,
    SQUAT,
    PULL_UP
}

/**
 * Generic exercise detection states
 */
enum class ExerciseState {
    UNKNOWN,
    START_POSITION,
    END_POSITION
}

/**
 * Enhanced exercise detection result with form analysis
 */
data class ExerciseResult(
    val repCount: Int,
    val currentState: ExerciseState,
    val exerciseType: ExerciseType,
    val postureAnalysis: PostureAnalysisResult?
)

/**
 * Abstract base class for exercise detectors with shared functionality
 */
abstract class ExerciseDetector(val exerciseType: ExerciseType) {
    
    // Make these properties public so they're accessible without separate getter methods
    var currentState = ExerciseState.UNKNOWN
        protected set
    
    var repCount = 0
        protected set
    
    // Posture analyzer for form feedback
    protected val postureAnalyzer = PostureAnalyzer()
    
    /**
     * Process a pose and update exercise count with form analysis
     * @param pose The detected pose
     * @return ExerciseResult with count and analysis
     */
    fun processPoseWithAnalysis(pose: Pose): ExerciseResult {
        val measurementValue = calculateExerciseMetric(pose)
        var newRepCount = repCount
        
        if (measurementValue != null) {
            val newState = determineState(measurementValue)
            
            // Count a rep when transitioning from END to START
            if (currentState == ExerciseState.END_POSITION && newState == ExerciseState.START_POSITION) {
                newRepCount++
                repCount = newRepCount
            }
            
            currentState = newState
        }
        
        // Analyze posture - convert ExerciseState to PushUpState for compatibility
        val pushUpState = when (currentState) {
            ExerciseState.START_POSITION -> com.grloepr.pushtrack.analysis.PushUpState.UP_POSITION
            ExerciseState.END_POSITION -> com.grloepr.pushtrack.analysis.PushUpState.DOWN_POSITION
            ExerciseState.UNKNOWN -> com.grloepr.pushtrack.analysis.PushUpState.UNKNOWN
        }
        val postureAnalysis = postureAnalyzer.analyzePose(pose, pushUpState)
        
        return ExerciseResult(
            repCount = newRepCount,
            currentState = currentState,
            exerciseType = exerciseType,
            postureAnalysis = postureAnalysis
        )
    }
    
    /**
     * Process a pose and update exercise count (legacy method for compatibility)
     * @param pose The detected pose
     * @return Current rep count
     */
    fun processPose(pose: Pose): Int {
        return processPoseWithAnalysis(pose).repCount
    }
    
    /**
     * Calculate the primary metric for this exercise (e.g., angle, height)
     * Returns null if key landmarks are not detected
     */
    protected abstract fun calculateExerciseMetric(pose: Pose): Double?
    
    /**
     * Determine exercise state based on the calculated metric
     */
    protected abstract fun determineState(metric: Double): ExerciseState
    
    /**
     * Calculate angle between three points (generic utility)
     */
    protected fun calculateAngleBetweenPoints(
        point1: PoseLandmark?,
        point2: PoseLandmark?,
        point3: PoseLandmark?
    ): Double? {
        if (point1 == null || point2 == null || point3 == null) return null
        
        // Check if landmarks have sufficient confidence
        if (point1.inFrameLikelihood < 0.5f || 
            point2.inFrameLikelihood < 0.5f || 
            point3.inFrameLikelihood < 0.5f) {
            return null
        }
        
        val p1 = point1.position
        val p2 = point2.position
        val p3 = point3.position
        
        // Vector from point2 to point1
        val v1x = p1.x - p2.x
        val v1y = p1.y - p2.y
        
        // Vector from point2 to point3
        val v2x = p3.x - p2.x
        val v2y = p3.y - p2.y
        
        // Calculate angle using dot product
        val dotProduct = v1x * v2x + v1y * v2y
        val magnitude1 = sqrt(v1x * v1x + v1y * v1y)
        val magnitude2 = sqrt(v2x * v2x + v2y * v2y)
        
        if (magnitude1 == 0.0f || magnitude2 == 0.0f) return null
        
        val cosAngle = dotProduct / (magnitude1 * magnitude2)
        val clampedCosAngle = cosAngle.coerceIn(-1.0f, 1.0f)
        
        return Math.toDegrees(acos(clampedCosAngle.toDouble()))
    }
    
    /**
     * Calculate vertical distance between two landmarks
     */
    protected fun calculateVerticalDistance(
        landmark1: PoseLandmark?,
        landmark2: PoseLandmark?
    ): Float? {
        if (landmark1 == null || landmark2 == null) return null
        
        if (landmark1.inFrameLikelihood < 0.5f || landmark2.inFrameLikelihood < 0.5f) {
            return null
        }
        
        return abs(landmark1.position.y - landmark2.position.y)
    }
    
    /**
     * Reset the rep counter and posture analyzer
     */
    fun reset() {
        repCount = 0
        currentState = ExerciseState.UNKNOWN
        postureAnalyzer.reset()
    }
}
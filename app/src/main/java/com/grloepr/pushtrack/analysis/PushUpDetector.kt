package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.domain.DetectionResult
import com.grloepr.pushtrack.domain.ExerciseDetector
import com.grloepr.pushtrack.domain.ExerciseState
import com.grloepr.pushtrack.domain.ExerciseType
import com.grloepr.pushtrack.domain.FormQuality
import com.grloepr.pushtrack.feedback.PostureAnalyzer
import com.grloepr.pushtrack.feedback.PostureAnalysisResult
import kotlin.math.*

/**
 * Push-up detection states
 */
enum class PushUpState {
    UNKNOWN,
    UP_POSITION,
    DOWN_POSITION
}

/**
 * Enhanced push-up detection with form analysis
 */
data class PushUpResult(
    val repCount: Int,
    val currentState: PushUpState,
    val postureAnalysis: PostureAnalysisResult?
)

/**
 * Detects push-up movements from pose data
 */
class PushUpDetector : ExerciseDetector {
    
    override val exerciseType = ExerciseType.PUSH_UP
    
    private var currentState = PushUpState.UNKNOWN
    private var repCount = 0
    
    // Posture analyzer for form feedback
    private val postureAnalyzer = PostureAnalyzer()
    
    // Angle thresholds for push-up detection
    private val downThreshold = 90.0 // degrees - elbow angle when in down position
    private val upThreshold = 160.0 // degrees - elbow angle when in up position
    
    /**
     * Process a pose and update push-up count with form analysis
     * @param pose The detected pose
     * @return PushUpResult with count and analysis
     */
    fun processPoseWithAnalysis(pose: Pose): PushUpResult {
        val armAngle = calculateArmAngle(pose)
        var newRepCount = repCount
        
        if (armAngle != null) {
            val newState = when {
                armAngle < downThreshold -> PushUpState.DOWN_POSITION
                armAngle > upThreshold -> PushUpState.UP_POSITION
                else -> currentState // Maintain current state in transition
            }
            
            // Count a rep when transitioning from DOWN to UP
            if (currentState == PushUpState.DOWN_POSITION && newState == PushUpState.UP_POSITION) {
                newRepCount++
                repCount = newRepCount
            }
            
            currentState = newState
        }
        
        // Analyze posture
        val postureAnalysis = postureAnalyzer.analyzePose(pose, currentState)
        
        return PushUpResult(
            repCount = newRepCount,
            currentState = currentState,
            postureAnalysis = postureAnalysis
        )
    }
    
    /**
     * Process a pose and update push-up count (legacy method for compatibility)
     * @param pose The detected pose
     * @return Current rep count
     */
    fun processPoseOld(pose: Pose): Int {
        return processPoseWithAnalysis(pose).repCount
    }
    
    /**
     * Process a pose and update rep count with form analysis (implements ExerciseDetector)
     * @param pose The detected pose
     * @return DetectionResult with count, state, and analysis
     */
    override fun processPose(pose: Pose): DetectionResult {
        android.util.Log.d("PushUpDetector", "Processing pose...")
        
        try {
            // Simple detection without PostureAnalysis first to isolate issues
            val armAngle = calculateArmAngle(pose)
            android.util.Log.d("PushUpDetector", "Calculated arm angle: $armAngle")
            
            var newRepCount = repCount
            
            if (armAngle != null) {
                val newState = when {
                    armAngle < downThreshold -> PushUpState.DOWN_POSITION
                    armAngle > upThreshold -> PushUpState.UP_POSITION
                    else -> currentState // Maintain current state in transition
                }
                
                android.util.Log.d("PushUpDetector", "State transition: $currentState -> $newState")
                
                // Count a rep when transitioning from DOWN to UP
                if (currentState == PushUpState.DOWN_POSITION && newState == PushUpState.UP_POSITION) {
                    newRepCount++
                    repCount = newRepCount
                    android.util.Log.d("PushUpDetector", "Rep counted! New count: $newRepCount")
                }
                
                currentState = newState
            }
            
            // Convert PushUpState to ExerciseState
            val exerciseState = when (currentState) {
                PushUpState.UP_POSITION -> ExerciseState.START_POSITION
                PushUpState.DOWN_POSITION -> ExerciseState.END_POSITION
                PushUpState.UNKNOWN -> ExerciseState.UNKNOWN
            }
            
            // Temporarily disable PostureAnalysis to isolate detection issues
            val formQuality = FormQuality(
                score = 75f, // Default score
                hasGoodForm = true,
                feedback = null
            )
            
            val result = DetectionResult(
                repCount = newRepCount,
                currentState = exerciseState,
                formQuality = formQuality,
                confidence = if (armAngle != null) 1.0f else 0.0f,
                lastAngle = armAngle?.toFloat(),
                detectionMethod = "angle-based"
            )
            
            android.util.Log.d("PushUpDetector", "Detection result: reps=$newRepCount, state=$exerciseState, angle=$armAngle")
            return result
            
        } catch (e: Exception) {
            android.util.Log.e("PushUpDetector", "Error in processPose: ${e.message}", e)
            return DetectionResult(
                repCount = repCount,
                currentState = ExerciseState.UNKNOWN,
                formQuality = null,
                confidence = 0.0f,
                lastAngle = null,
                detectionMethod = "error"
            )
        }
    }
    
    /**
     * Calculate the average arm angle (shoulder-elbow-wrist) for both arms
     * Returns null if key landmarks are not detected
     */
    private fun calculateArmAngle(pose: Pose): Double? {
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
     * Calculate angle between three points (shoulder-elbow-wrist)
     */
    private fun calculateSingleArmAngle(
        shoulder: PoseLandmark?,
        elbow: PoseLandmark?,
        wrist: PoseLandmark?
    ): Double? {
        if (shoulder == null || elbow == null || wrist == null) return null
        
        // Check if landmarks have sufficient confidence
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
        
        return Math.toDegrees(acos(clampedCosAngle.toDouble()))
    }
    
    /**
     * Get current rep count
     */
    override fun getRepCount(): Int = repCount
    
    /**
     * Get current state as internal PushUpState
     */
    fun getCurrentPushUpState(): PushUpState = currentState
    
    /**
     * Get current state as ExerciseState (implements ExerciseDetector)
     */
    override fun getCurrentState(): ExerciseState {
        return when (currentState) {
            PushUpState.UP_POSITION -> ExerciseState.START_POSITION
            PushUpState.DOWN_POSITION -> ExerciseState.END_POSITION
            PushUpState.UNKNOWN -> ExerciseState.UNKNOWN
        }
    }
    
    /**
     * Reset the detector state (implements ExerciseDetector)
     * Removed duplicate reset method and properly override the one from interface
     */
    override fun reset() {
        repCount = 0
        currentState = PushUpState.UNKNOWN
        postureAnalyzer.reset()
    }
    
    /**
     * Push-ups don't require calibration
     */
    override fun requiresCalibration(): Boolean = false
    
    /**
     * Push-ups don't require calibration, so always return true
     */
    override fun isCalibrated(): Boolean = true
    
    /**
     * No calibration needed for push-ups, but implement interface method
     */
    override fun calibrate(pose: Pose): Boolean = true
}
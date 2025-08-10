package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
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
 * Detects push-up movements from pose data
 */
class PushUpDetector {
    
    private var currentState = PushUpState.UNKNOWN
    private var repCount = 0
    
    // Angle thresholds for push-up detection
    private val downThreshold = 90.0 // degrees - elbow angle when in down position
    private val upThreshold = 160.0 // degrees - elbow angle when in up position
    
    /**
     * Process a pose and update push-up count
     * @param pose The detected pose
     * @return Current rep count
     */
    fun processPose(pose: Pose): Int {
        val armAngle = calculateArmAngle(pose)
        
        if (armAngle != null) {
            val newState = when {
                armAngle < downThreshold -> PushUpState.DOWN_POSITION
                armAngle > upThreshold -> PushUpState.UP_POSITION
                else -> currentState // Maintain current state in transition
            }
            
            // Count a rep when transitioning from DOWN to UP
            if (currentState == PushUpState.DOWN_POSITION && newState == PushUpState.UP_POSITION) {
                repCount++
            }
            
            currentState = newState
        }
        
        return repCount
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
     * Reset the rep counter
     */
    fun reset() {
        repCount = 0
        currentState = PushUpState.UNKNOWN
    }
    
    /**
     * Get current rep count
     */
    fun getRepCount(): Int = repCount
    
    /**
     * Get current state
     */
    fun getCurrentState(): PushUpState = currentState
}
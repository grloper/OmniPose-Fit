package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.domain.*
import kotlin.math.*

/**
 * Detects squat movements from pose data
 * Uses hip-knee-ankle angle to detect squatting motion
 */
class SquatDetector : ExerciseDetector {
    
    override val exerciseType = ExerciseType.SQUAT
    
    private var currentState = ExerciseState.UNKNOWN
    private var repCount = 0
    
    // Angle thresholds for squat detection
    private val standingThreshold = 160.0 // degrees - hip-knee angle when standing
    private val squatThreshold = 120.0 // degrees - hip-knee angle when in squat position
    
    // State confirmation counters to prevent false positives
    private var standingPositionCount = 0
    private var squatPositionCount = 0
    private val confirmationThreshold = 2
    
    override fun processPose(pose: Pose): DetectionResult {
        val leftHipKneeAngle = calculateHipKneeAngle(pose, true)
        val rightHipKneeAngle = calculateHipKneeAngle(pose, false)
        
        val avgHipKneeAngle = when {
            leftHipKneeAngle != null && rightHipKneeAngle != null -> (leftHipKneeAngle + rightHipKneeAngle) / 2
            leftHipKneeAngle != null -> leftHipKneeAngle
            rightHipKneeAngle != null -> rightHipKneeAngle
            else -> null
        }
        
        if (avgHipKneeAngle == null) {
            return DetectionResult(
                repCount = repCount,
                currentState = currentState,
                formQuality = null,
                confidence = 0f
            )
        }
        
        // Determine squat position based on hip-knee angle
        val isStanding = avgHipKneeAngle > standingThreshold
        val isSquatting = avgHipKneeAngle < squatThreshold
        
        var newRepCount = repCount
        val newState = when {
            isStanding -> {
                standingPositionCount++
                squatPositionCount = 0
                if (standingPositionCount >= confirmationThreshold) ExerciseState.START_POSITION else currentState
            }
            isSquatting -> {
                squatPositionCount++
                standingPositionCount = 0
                if (squatPositionCount >= confirmationThreshold) ExerciseState.END_POSITION else currentState
            }
            else -> {
                standingPositionCount = 0
                squatPositionCount = 0
                ExerciseState.TRANSITIONING
            }
        }
        
        // Count a rep when transitioning from standing to squatting
        if (currentState == ExerciseState.START_POSITION && newState == ExerciseState.END_POSITION) {
            newRepCount++
            repCount = newRepCount
        }
        
        currentState = newState
        
        // Form quality assessment
        val formQuality = FormQuality(
            score = when {
                avgHipKneeAngle < squatThreshold -> {
                    // Good squat depth
                    85f
                }
                avgHipKneeAngle < standingThreshold && avgHipKneeAngle > squatThreshold -> {
                    // Partial squat
                    65f
                }
                else -> 75f
            },
            hasGoodForm = avgHipKneeAngle < squatThreshold + 10,
            feedback = when {
                avgHipKneeAngle > squatThreshold + 20 && currentState == ExerciseState.TRANSITIONING -> "Squat deeper"
                currentState == ExerciseState.END_POSITION -> "Good depth!"
                else -> null
            }
        )
        
        return DetectionResult(
            repCount = newRepCount,
            currentState = currentState,
            formQuality = formQuality,
            confidence = 0.8f,
            lastAngle = avgHipKneeAngle.toFloat(),
            detectionMethod = "hip_knee_angle"
        )
    }
    
    /**
     * Calculate hip-knee-ankle angle for squat detection
     * @param pose The detected pose
     * @param isLeftLeg Whether to calculate for left leg (true) or right leg (false)
     * @return Hip-knee angle in degrees, or null if landmarks not detected
     */
    private fun calculateHipKneeAngle(pose: Pose, isLeftLeg: Boolean): Double? {
        val hipLandmark = if (isLeftLeg) PoseLandmark.LEFT_HIP else PoseLandmark.RIGHT_HIP
        val kneeLandmark = if (isLeftLeg) PoseLandmark.LEFT_KNEE else PoseLandmark.RIGHT_KNEE
        val ankleLandmark = if (isLeftLeg) PoseLandmark.LEFT_ANKLE else PoseLandmark.RIGHT_ANKLE
        
        val hip = pose.getPoseLandmark(hipLandmark)
        val knee = pose.getPoseLandmark(kneeLandmark)
        val ankle = pose.getPoseLandmark(ankleLandmark)
        
        if (hip == null || knee == null || ankle == null) return null
        
        // Check confidence levels
        if (hip.inFrameLikelihood < 0.5f || 
            knee.inFrameLikelihood < 0.5f || 
            ankle.inFrameLikelihood < 0.5f) {
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
        
        return Math.toDegrees(acos(clampedCosAngle.toDouble()))
    }
    
    override fun reset() {
        repCount = 0
        currentState = ExerciseState.UNKNOWN
        standingPositionCount = 0
        squatPositionCount = 0
    }
    
    override fun getRepCount(): Int = repCount
    
    override fun getCurrentState(): ExerciseState = currentState
}
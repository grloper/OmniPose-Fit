package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.detection.utils.AngleCalculator
import kotlin.math.abs
import kotlin.math.max

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
    
    // Calibration and adaptive detection variables
    private var hangElbowBaseline: Float? = null
    private var upElbowBaseline: Float? = null
    private var hangHeadHandBaseline: Float? = null
    private var calibrationStable = 0
    private val calibrationFrames = 30
    
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
        val headHandDist = calculateHeadToHandsDistance(pose)
        
        // Enhanced calibration
        if (primaryAngle != null && headHandDist != null) {
            calibrateHangPosition(primaryAngle, headHandDist)
        }
        
        // Use ratio-based detection if calibrated
        val calibratedPhase = if (hangElbowBaseline != null && primaryAngle != null) {
            val contractionRatio = primaryAngle / hangElbowBaseline!!
            when {
                contractionRatio <= 0.45f -> ExercisePhase.UP      // Highly contracted
                contractionRatio >= 0.85f -> ExercisePhase.DOWN    // Near full hang
                else -> ExercisePhase.TRANSITIONING
            }
        } else null
        
        // Fallback to fixed thresholds
        val anglePhase = if (primaryAngle != null) {
            when {
                primaryAngle < 70f -> ExercisePhase.UP    // More sensitive up detection
                primaryAngle > 130f -> ExercisePhase.DOWN // More sensitive down detection
                else -> ExercisePhase.TRANSITIONING
            }
        } else ExercisePhase.TRANSITIONING
        
        return calibratedPhase ?: anglePhase
    }
    
    private fun calibrateHangPosition(elbowAngle: Float, headHandDist: Float) {
        // Look for stable hanging position (extended arms)
        if (elbowAngle > 120f && elbowAngle < 180f) {
            calibrationStable++
            if (calibrationStable >= calibrationFrames) {
                hangElbowBaseline = elbowAngle
                hangHeadHandBaseline = headHandDist
                calibrationStable = 0
            }
        } else if (elbowAngle < 80f && hangElbowBaseline != null) {
            // Calibrate contracted position
            upElbowBaseline = elbowAngle
        } else {
            calibrationStable = max(0, calibrationStable - 1)
        }
    }
    
    /**
     * Calculate confidence based on angle reliability and landmark visibility
     */
    override fun calculateConfidence(pose: Pose, primaryAngle: Float?): Float {
        var conf = 0f
        var factors = 0
        
        if (primaryAngle != null) {
            conf += 0.8f
            factors++
        }
        
        if (hangElbowBaseline != null) {
            conf += 0.2f // Calibration bonus
            factors++
        }
        
        // Check wrist visibility (important for pull-ups)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        if (leftWrist?.inFrameLikelihood ?: 0f > 0.6f && 
            rightWrist?.inFrameLikelihood ?: 0f > 0.6f) {
            conf += 0.2f
            factors++
        }
        
        return if (factors > 0) (conf / factors).coerceIn(0f, 1f) else 0f
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
    
    /**
     * Reset detector state
     */
    override fun reset() {
        super.reset()
        hangElbowBaseline = null
        upElbowBaseline = null
        hangHeadHandBaseline = null
        calibrationStable = 0
    }
}
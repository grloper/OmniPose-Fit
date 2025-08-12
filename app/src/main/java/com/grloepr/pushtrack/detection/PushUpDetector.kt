package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.detection.utils.AngleCalculator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Modular push-up detector with O(1) complexity
 * Uses elbow angle as primary detection signal
 */
class PushUpDetector : BaseExerciseDetector(ExerciseType.PUSH_UP) {
    
    // Enhanced thresholds for ground position
    private val upElbowThreshold = 140f    // Arms extended
    private val downElbowThreshold = 80f   // Arms bent (more sensitive)
    
    // Ground position calibration
    private var groundHeadLevel: Float? = null
    private var upHeadLevel: Float? = null
    private var calibrationFrames = 0
    private val calibrationRequired = 15
    
    // Enhanced movement tracking
    private var lastHeadY: Float? = null
    private var headMovementBuffer = mutableListOf<Float>()
    private val maxMovementSamples = 8
    
    /**
     * Calculate primary angle (average elbow angle) for push-up detection
     */
    override fun calculatePrimaryAngle(pose: Pose): Float? {
        return AngleCalculator.calculateAverageElbowAngle(pose)
    }
    
    /**
     * Determine push-up phase based on elbow angle
     */
    override fun determinePhase(pose: Pose, primaryAngle: Float?): ExercisePhase {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        
        // Auto-calibrate ground and up positions
        if (nose != null && primaryAngle != null) {
            calibrateGroundPosition(nose.position.y, primaryAngle)
        }
        
        // Use head movement as primary signal for ground push-ups
        val headPhase = if (nose != null && groundHeadLevel != null && upHeadLevel != null) {
            val currentY = nose.position.y
            val range = abs(upHeadLevel!! - groundHeadLevel!!)
            if (range > 20f) { // Sufficient range detected
                val relativePosition = (currentY - groundHeadLevel!!) / range
                when {
                    relativePosition < 0.2f -> ExercisePhase.DOWN  // Close to ground
                    relativePosition > 0.7f -> ExercisePhase.UP    // Head up
                    else -> ExercisePhase.TRANSITIONING
                }
            } else null
        } else null
        
        // Enhanced elbow angle detection
        val anglePhase = if (primaryAngle != null) {
            when {
                primaryAngle < downElbowThreshold -> ExercisePhase.DOWN
                primaryAngle > upElbowThreshold -> ExercisePhase.UP
                else -> ExercisePhase.TRANSITIONING
            }
        } else null
        
        // Prefer head movement for ground push-ups, fallback to angle
        return headPhase ?: anglePhase ?: ExercisePhase.TRANSITIONING
    }
    
    private fun calibrateGroundPosition(headY: Float, elbowAngle: Float) {
        // Track head movement
        lastHeadY?.let { lastY ->
            val movement = abs(headY - lastY)
            headMovementBuffer.add(movement)
            if (headMovementBuffer.size > maxMovementSamples) {
                headMovementBuffer.removeAt(0)
            }
        }
        lastHeadY = headY
        
        // Calibrate during stable periods
        val avgMovement = if (headMovementBuffer.isNotEmpty()) 
            headMovementBuffer.average().toFloat() else 0f
            
        if (avgMovement < 3f) { // Stable position
            calibrationFrames++
            
            if (calibrationFrames >= calibrationRequired) {
                when {
                    elbowAngle < 100f -> { // Person is down
                        groundHeadLevel = headY
                    }
                    elbowAngle > 140f -> { // Person is up
                        upHeadLevel = headY
                    }
                }
                calibrationFrames = 0
            }
        } else {
            calibrationFrames = 0
        }
    }
    
    /**
     * Calculate confidence based on angle reliability and landmark visibility
     */
    override fun calculateConfidence(pose: Pose, primaryAngle: Float?): Float {
        var confidence = 0f
        var factors = 0
        
        if (primaryAngle != null) {
            confidence += 0.7f
            factors++
        }
        
        // Boost confidence if calibrated
        if (groundHeadLevel != null && upHeadLevel != null) {
            confidence += 0.3f
            factors++
        }
        
        // Check landmark visibility
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        
        if (nose?.inFrameLikelihood ?: 0f > 0.7f && 
            leftShoulder?.inFrameLikelihood ?: 0f > 0.7f &&
            rightShoulder?.inFrameLikelihood ?: 0f > 0.7f) {
            confidence += 0.2f
            factors++
        }
        
        return if (factors > 0) (confidence / factors).coerceIn(0f, 1f) else 0f
    }
    
    /**
     * Get detection method description
     */
    override fun getDetectionMethod(): String {
        return when {
            groundHeadLevel != null && upHeadLevel != null -> "ground_calibrated"
            lastPrimaryAngle != null -> "elbow_angle"
            else -> "none"
        }
    }
    
    /**
     * Reset detector state
     */
    override fun reset() {
        super.reset()
        groundHeadLevel = null
        upHeadLevel = null
        calibrationFrames = 0
        lastHeadY = null
        headMovementBuffer.clear()
    }
}
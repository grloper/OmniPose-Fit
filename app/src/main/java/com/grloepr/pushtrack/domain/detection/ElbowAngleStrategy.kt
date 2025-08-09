package com.grloepr.pushtrack.domain.detection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.domain.model.PushUpState
import com.grloepr.pushtrack.domain.model.PushUpPhase
import com.grloepr.pushtrack.util.AngleCalculator
import kotlin.math.abs

/**
 * Push-up detection strategy based on elbow angle measurements
 * This is the primary detection strategy when arms are clearly visible
 */
class ElbowAngleStrategy(
    private val downThreshold: Float = 80f,
    private val upThreshold: Float = 160f,
    private val minConfidence: Float = 0.6f
) : PushUpDetectionStrategy {
    
    override val name: String = "ElbowAngleStrategy"
    
    private var lastCalculatedAngle: Float? = null
    private var confidenceScore: Float = 0f
    
    override fun isApplicable(pose: Pose): Boolean {
        // Check if required landmarks for at least one arm are visible with sufficient confidence
        val leftVisible = isArmVisible(pose, isLeftArm = true)
        val rightVisible = isArmVisible(pose, isLeftArm = false)
        
        return leftVisible || rightVisible
    }
    
    override fun detectPushUpState(pose: Pose, previousState: PushUpState?): PushUpState? {
        // Get best arm angle (highest confidence between left/right)
        val angleResult = AngleCalculator.getBestElbowAngle(pose)
        
        if (angleResult == null) {
            confidenceScore = 0f
            return null
        }
        
        val (angle, isLeftArm) = angleResult
        lastCalculatedAngle = angle
        
        // Calculate confidence based on landmark visibility and angle stability
        val confidenceValue = calculateConfidence(pose, isLeftArm, angle)
        confidenceScore = confidenceValue
        
        // If confidence is too low, don't update state
        if (confidenceValue < minConfidence) {
            return null
        }
        
        // Determine push-up phase based on angle
        val phase = when {
            angle <= downThreshold -> PushUpPhase.DOWN
            angle >= upThreshold -> PushUpPhase.UP
            previousState?.phase == PushUpPhase.DOWN && angle > previousState.lastAngle ?: 0f -> PushUpPhase.TRANSITIONING_UP
            previousState?.phase == PushUpPhase.UP && angle < previousState.lastAngle ?: 180f -> PushUpPhase.TRANSITIONING_DOWN
            previousState?.phase != null -> previousState.phase
            else -> PushUpPhase.UP // Default to UP if no previous state
        }
        
        return PushUpState(
            phase = phase,
            lastAngle = angle,
            confidence = confidenceValue,
            strategy = name
        )
    }
    
    override fun getConfidence(): Float = confidenceScore
    
    private fun isArmVisible(pose: Pose, isLeftArm: Boolean): Boolean {
        val wristType = if (isLeftArm) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
        val elbowType = if (isLeftArm) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
        val shoulderType = if (isLeftArm) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER
        
        val wrist = pose.getPoseLandmark(wristType)
        val elbow = pose.getPoseLandmark(elbowType)
        val shoulder = pose.getPoseLandmark(shoulderType)
        
        return wrist?.inFrameLikelihood ?: 0f > minConfidence &&
               elbow?.inFrameLikelihood ?: 0f > minConfidence &&
               shoulder?.inFrameLikelihood ?: 0f > minConfidence
    }
    
    private fun calculateConfidence(pose: Pose, isLeftArm: Boolean, angle: Float): Float {
        val wristType = if (isLeftArm) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
        val elbowType = if (isLeftArm) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
        val shoulderType = if (isLeftArm) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER
        
        val wrist = pose.getPoseLandmark(wristType)
        val elbow = pose.getPoseLandmark(elbowType)
        val shoulder = pose.getPoseLandmark(shoulderType)
        
        // Average confidence of all involved landmarks
        val landmarksConfidence = (wrist?.inFrameLikelihood ?: 0f) +
                                 (elbow?.inFrameLikelihood ?: 0f) +
                                 (shoulder?.inFrameLikelihood ?: 0f)
        
        val avgLandmarkConfidence = landmarksConfidence / 3f
        
        // Check stability by comparing with previous angle
        val stabilityFactor = lastCalculatedAngle?.let { 
            val change = abs(it - angle)
            if (change > 30f) 0.5f else 1f // Penalize large sudden changes
        } ?: 0.7f // First reading gets moderate confidence
        
        return avgLandmarkConfidence * stabilityFactor
    }
}

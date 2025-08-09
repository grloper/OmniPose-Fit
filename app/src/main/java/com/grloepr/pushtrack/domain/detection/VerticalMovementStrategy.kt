package com.grloepr.pushtrack.domain.detection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.domain.model.PushUpState
import com.grloepr.pushtrack.domain.model.PushUpPhase
import kotlin.math.abs

/**
 * Strategy that detects push-ups by tracking vertical body movement
 * Useful when arms/elbows aren't clearly visible but shoulders and hips are
 */
class VerticalMovementStrategy(
    private val minVerticalChange: Float = 30f,
    private val minConfidence: Float = 0.5f
) : PushUpDetectionStrategy {

    override val name: String = "VerticalMovementStrategy"
    
    private var initialShoulderYPosition: Float? = null
    private var lowestYPosition: Float? = null
    private var highestYPosition: Float? = null
    private var confidenceScore: Float = 0f
    private val yPositionHistory = ArrayDeque<Float>(5)
    
    override fun isApplicable(pose: Pose): Boolean {
        // Need at least shoulders and hips to be visible
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        
        val hasShoulders = (leftShoulder?.inFrameLikelihood ?: 0f) > minConfidence || 
                           (rightShoulder?.inFrameLikelihood ?: 0f) > minConfidence
                           
        return hasShoulders
    }
    
    override fun detectPushUpState(pose: Pose, previousState: PushUpState?): PushUpState? {
        // Calculate average Y position of shoulders
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        
        // Average shoulder Y position (higher value = lower position in image)
        val shoulderY = getAverageY(leftShoulder?.position?.y, rightShoulder?.position?.y)
        
        if (shoulderY == null) {
            confidenceScore = 0f
            return null
        }
        
        // Track Y position history for smoothing
        yPositionHistory.addLast(shoulderY)
        if (yPositionHistory.size > 5) {
            yPositionHistory.removeFirst()
        }
        
        // Use smoothed Y position
        val smoothedY = yPositionHistory.average().toFloat()
        
        // Initialize reference points if needed
        if (initialShoulderYPosition == null) {
            initialShoulderYPosition = smoothedY
            lowestYPosition = smoothedY
            highestYPosition = smoothedY
        }
        
        // Update range
        lowestYPosition = minOf(lowestYPosition!!, smoothedY)
        highestYPosition = maxOf(highestYPosition!!, smoothedY)
        
        // Calculate vertical range
        val verticalRange = highestYPosition!! - lowestYPosition!!
        
        // Calculate relative position in the range (0 = highest, 1 = lowest)
        val relativePosition = if (verticalRange > 0) {
            (smoothedY - lowestYPosition!!) / verticalRange
        } else {
            0.5f
        }
        
        // Calculate confidence based on vertical range and landmark confidence
        val shoulderConfidence = maxOf(
            leftShoulder?.inFrameLikelihood ?: 0f,
            rightShoulder?.inFrameLikelihood ?: 0f
        )
        
        val rangeConfidence = (verticalRange / minVerticalChange).coerceAtMost(1f)
        confidenceScore = shoulderConfidence * rangeConfidence
        
        if (confidenceScore < minConfidence || verticalRange < minVerticalChange) {
            return null
        }
        
        // Determine push-up phase based on vertical position
        val phase = when {
            relativePosition > 0.7f -> PushUpPhase.DOWN
            relativePosition < 0.3f -> PushUpPhase.UP
            previousState?.phase == PushUpPhase.DOWN && relativePosition < 0.7f -> PushUpPhase.TRANSITIONING_UP
            previousState?.phase == PushUpPhase.UP && relativePosition > 0.3f -> PushUpPhase.TRANSITIONING_DOWN
            previousState?.phase != null -> previousState.phase
            else -> PushUpPhase.UP
        }
        
        return PushUpState(
            phase = phase,
            lastAngle = relativePosition * 180f, // Convert relative position to angle-like value
            confidence = confidenceScore,
            strategy = name
        )
    }
    
    override fun getConfidence(): Float = confidenceScore
    
    private fun getAverageY(y1: Float?, y2: Float?): Float? {
        return when {
            y1 != null && y2 != null -> (y1 + y2) / 2
            y1 != null -> y1
            y2 != null -> y2
            else -> null
        }
    }
    
    /**
     * Reset the tracking when camera position changes
     */
    fun reset() {
        initialShoulderYPosition = null
        lowestYPosition = null
        highestYPosition = null
        yPositionHistory.clear()
    }
}

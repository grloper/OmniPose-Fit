package com.grloepr.pushtrack.domain.detection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.domain.model.PushUpState
import com.grloepr.pushtrack.domain.model.PushUpPhase
import kotlin.math.abs

/**
 * Strategy for detecting push-ups when facing the camera (front view)
 * Uses shoulder width changes as proxy for distance from camera
 */
class FrontFacingStrategy(
    private val minShoulderWidthChange: Float = 50f,
    private val minConfidence: Float = 0.5f
) : PushUpDetectionStrategy {

    override val name: String = "FrontFacingStrategy"
    
    private var initialShoulderWidth: Float? = null
    private var minShoulderWidth: Float? = null
    private var maxShoulderWidth: Float? = null
    private var confidenceScore: Float = 0f
    private val shoulderWidthHistory = ArrayDeque<Float>(5)
    
    override fun isApplicable(pose: Pose): Boolean {
        // Check if both shoulders and nose are visible (typical front view)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        
        val shouldersVisible = (leftShoulder?.inFrameLikelihood ?: 0f) > minConfidence && 
                              (rightShoulder?.inFrameLikelihood ?: 0f) > minConfidence
        val noseVisible = (nose?.inFrameLikelihood ?: 0f) > minConfidence
        
        // Additionally check if shoulders are roughly at same Y-coordinate (front view)
        val shoulderYDifference = abs(
            (leftShoulder?.position?.y ?: 0f) - (rightShoulder?.position?.y ?: 0f)
        )
        val shouldersAligned = shoulderYDifference < 30f
        
        return shouldersVisible && noseVisible && shouldersAligned
    }
    
    override fun detectPushUpState(pose: Pose, previousState: PushUpState?): PushUpState? {
        // Calculate shoulder width (distance between shoulders)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        
        if (leftShoulder == null || rightShoulder == null) {
            confidenceScore = 0f
            return null
        }
        
        val shoulderWidth = abs(leftShoulder.position.x - rightShoulder.position.x)
        
        // Track width history for smoothing
        shoulderWidthHistory.addLast(shoulderWidth)
        if (shoulderWidthHistory.size > 5) {
            shoulderWidthHistory.removeFirst()
        }
        
        // Use smoothed width
        val smoothedWidth = shoulderWidthHistory.average().toFloat()
        
        // Initialize reference points if needed
        if (initialShoulderWidth == null) {
            initialShoulderWidth = smoothedWidth
            minShoulderWidth = smoothedWidth
            maxShoulderWidth = smoothedWidth
        }
        
        // Update range
        minShoulderWidth = minOf(minShoulderWidth!!, smoothedWidth)
        maxShoulderWidth = maxOf(maxShoulderWidth!!, smoothedWidth)
        
        // Calculate width range
        val widthRange = maxShoulderWidth!! - minShoulderWidth!!
        
        // Calculate relative position in the range (0 = narrowest/furthest, 1 = widest/closest)
        val relativePosition = if (widthRange > 0) {
            (smoothedWidth - minShoulderWidth!!) / widthRange
        } else {
            0.5f
        }
        
        // Calculate confidence based on width range and landmark confidence
        val shoulderConfidence = minOf(
            leftShoulder.inFrameLikelihood,
            rightShoulder.inFrameLikelihood
        )
        
        val rangeConfidence = (widthRange / minShoulderWidthChange).coerceAtMost(1f)
        confidenceScore = shoulderConfidence * rangeConfidence
        
        if (confidenceScore < minConfidence || widthRange < minShoulderWidthChange) {
            return null
        }
        
        // Determine push-up phase based on shoulder width
        // Wider shoulders = closer to camera = UP position
        // Narrower shoulders = further from camera = DOWN position
        val phase = when {
            relativePosition < 0.3f -> PushUpPhase.DOWN
            relativePosition > 0.7f -> PushUpPhase.UP
            previousState?.phase == PushUpPhase.DOWN && relativePosition > 0.3f -> PushUpPhase.TRANSITIONING_UP
            previousState?.phase == PushUpPhase.UP && relativePosition < 0.7f -> PushUpPhase.TRANSITIONING_DOWN
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
    
    /**
     * Reset the tracking when camera position changes
     */
    fun reset() {
        initialShoulderWidth = null
        minShoulderWidth = null
        maxShoulderWidth = null
        shoulderWidthHistory.clear()
    }
}

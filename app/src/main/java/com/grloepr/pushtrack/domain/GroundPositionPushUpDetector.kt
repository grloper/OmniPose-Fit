package com.grloepr.pushtrack.domain

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * SPECIALIZED detector for push-ups performed in ground position facing the camera
 * Uses multi-signal approach with head tracking and shoulder movement for maximum accuracy
 */
class GroundPositionPushUpDetector {
    
    /**
     * Push-up phases specifically for ground position detection
     */
    enum class PushUpPhase {
        UP,         // At the top of the push-up
        DOWN,       // At the bottom of the push-up
        TRANSITIONING // Moving between up and down
    }
    
    /**
     * State data class for ground position push-up detection
     */
    data class PushUpState(
        val count: Int = 0,
        val phase: PushUpPhase = PushUpPhase.UP,
        val primaryAngle: Float? = null,
        val confidence: Float = 0f,
        val isTracking: Boolean = false,
        val detectionMethod: String = "none"
    )
    
    private val _state = MutableStateFlow(PushUpState())
    val state: StateFlow<PushUpState> = _state.asStateFlow()
    
    // Configurable thresholds
    private val headHeightUpThreshold = 0.8f    // Head is high relative to baseline
    private val headHeightDownThreshold = 0.5f  // Head is low relative to baseline
    private val shoulderWidthUpThreshold = 0.9f // Shoulders are wide (person is up)
    private val shoulderWidthDownThreshold = 0.7f // Shoulders are narrower (person is down)
    private val elbowAngleUpThreshold = 150f    // Arm is straight
    private val elbowAngleDownThreshold = 110f  // Arm is bent
    
    // Position tracking
    private var baselineHeadHeight: Float? = null
    private var baselineShoulderWidth: Float? = null
    private var maxHeadHeight: Float = 0f
    private var minHeadHeight: Float = Float.MAX_VALUE
    private var maxShoulderWidth: Float = 0f
    private var minShoulderWidth: Float = Float.MAX_VALUE
    
    // Angle tracking
    private var lastElbowAngle: Float? = null
    
    // Movement tracking
    private var upPositionCount = 0
    private var downPositionCount = 0
    private var lastPhase = PushUpPhase.UP
    
    // Adaptive thresholds that change based on movement speed
    private var adaptiveConfirmationThreshold = 2 // Start with default, will adjust based on speed
    private var movementSpeedFactor = 1.0f // Multiplier for speed detection
    
    // Movement velocity tracking
    private var lastHeadPositionY: Float? = null
    private var headVelocity = 0f
    private var velocitySamples = mutableListOf<Float>()
    private val maxVelocitySamples = 5 // Number of samples to track for average velocity
    
    /**
     * Process a pose for ground position push-up detection with improved speed detection
     */
    fun processPose(pose: Pose) {
        // Track primary landmarks
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        
        // Require at least nose and shoulders for tracking
        if (nose == null || leftShoulder == null || rightShoulder == null) {
            // Not enough landmarks visible
            _state.value = _state.value.copy(isTracking = false)
            return
        }
        
        // Calculate current measurements
        val currentHeadHeight = nose.position.y
        val currentShoulderWidth = abs(leftShoulder.position.x - rightShoulder.position.x)
        
        // Track head velocity for speed detection
        val lastY = lastHeadPositionY
        if (lastY != null) {
            headVelocity = abs(currentHeadHeight - lastY)
            velocitySamples.add(headVelocity)
            // Keep only the most recent samples
            if (velocitySamples.size > maxVelocitySamples) {
                velocitySamples.removeAt(0)
            }
        }
        lastHeadPositionY = currentHeadHeight
        
        // Calculate average velocity
        val avgVelocity = if (velocitySamples.isNotEmpty()) {
            velocitySamples.average().toFloat()
        } else {
            0f
        }
        
        // Adjust confirmation threshold based on movement speed
        // Fast movements need fewer confirmations
        adaptiveConfirmationThreshold = when {
            avgVelocity > 15f -> 1 // Very fast movement - instant confirmation
            avgVelocity > 10f -> 2 // Fast movement - quick confirmation
            avgVelocity > 5f -> 3 // Moderate movement - normal confirmation
            else -> 4 // Slow movement - more confirmations needed
        }
        
        // Initialize baseline values if not set
        if (baselineHeadHeight == null) {
            baselineHeadHeight = currentHeadHeight
            maxHeadHeight = currentHeadHeight
            minHeadHeight = currentHeadHeight
        }
        
        if (baselineShoulderWidth == null) {
            baselineShoulderWidth = currentShoulderWidth
            maxShoulderWidth = currentShoulderWidth
            minShoulderWidth = currentShoulderWidth
        }
        
        // Update min/max values with momentum for faster tracking
        // Apply speed factor to make tracking more responsive
        movementSpeedFactor = 1.0f + min(avgVelocity / 10f, 0.5f) // Cap at 1.5x speed boost
        
        // More aggressive updating of min/max values for fast movements
        maxHeadHeight = max(maxHeadHeight, currentHeadHeight)
        minHeadHeight = min(minHeadHeight, currentHeadHeight)
        maxShoulderWidth = max(maxShoulderWidth, currentShoulderWidth)
        minShoulderWidth = min(minShoulderWidth, currentShoulderWidth)
        
        // Calculate elbow angle with higher priority for fast movements
        val elbowAngle = calculateAverageElbowAngle(pose)
        
        // Calculate normalized positions with speed-aware adjustments
        val headHeightRange = maxHeadHeight - minHeadHeight
        val normalizedHeadHeight = if (headHeightRange > 0) 
            1f - ((currentHeadHeight - minHeadHeight) / headHeightRange) else 0.5f
            
        val shoulderWidthRange = maxShoulderWidth - minShoulderWidth
        val normalizedShoulderWidth = if (shoulderWidthRange > 0) 
            (currentShoulderWidth - minShoulderWidth) / shoulderWidthRange else 0.5f
        
        // Multi-signal detection with speed awareness
        var detectionMethod = "none"
        var confidence = 0f
        var currentPhase = PushUpPhase.TRANSITIONING
        
        // For fast movements, lower the angle thresholds slightly
        val speedAdjustedUpThreshold = elbowAngleUpThreshold - (avgVelocity * 0.5f).coerceIn(0f, 10f)
        val speedAdjustedDownThreshold = elbowAngleDownThreshold + (avgVelocity * 0.5f).coerceIn(0f, 10f)
        
        // 1. Try elbow angle detection with speed-adjusted thresholds
        if (elbowAngle != null) {
            lastElbowAngle = elbowAngle
            if (elbowAngle > speedAdjustedUpThreshold) {
                currentPhase = PushUpPhase.UP
                confidence = 0.8f + (avgVelocity / 50f).coerceIn(0f, 0.1f) // Boost confidence for fast movements
                detectionMethod = "elbow_angle"
            } else if (elbowAngle < speedAdjustedDownThreshold) {
                currentPhase = PushUpPhase.DOWN
                confidence = 0.8f + (avgVelocity / 50f).coerceIn(0f, 0.1f)
                detectionMethod = "elbow_angle"
            }
        }
        
        // 2. Try head height detection with speed awareness
        if (confidence < 0.7f) {
            // Adjust thresholds for fast movements
            val speedAdjustedUpHeadThreshold = headHeightUpThreshold - (avgVelocity / 50f).coerceIn(0f, 0.1f)
            val speedAdjustedDownHeadThreshold = headHeightDownThreshold + (avgVelocity / 50f).coerceIn(0f, 0.1f)
            
            if (normalizedHeadHeight > speedAdjustedUpHeadThreshold) {
                currentPhase = PushUpPhase.UP
                confidence = 0.7f
                detectionMethod = "head_height"
            } else if (normalizedHeadHeight < speedAdjustedDownHeadThreshold) {
                currentPhase = PushUpPhase.DOWN
                confidence = 0.7f
                detectionMethod = "head_height"
            }
        }
        
        // 3. Try shoulder width detection (adjusted for speed)
        if (confidence < 0.6f) {
            if (normalizedShoulderWidth > shoulderWidthUpThreshold) {
                currentPhase = PushUpPhase.UP
                confidence = 0.6f
                detectionMethod = "shoulder_width"
            } else if (normalizedShoulderWidth < shoulderWidthDownThreshold) {
                currentPhase = PushUpPhase.DOWN
                confidence = 0.6f
                detectionMethod = "shoulder_width"
            }
        }
        
        // Use multiple confirmations for state changes with speed-adaptive threshold
        if (currentPhase == PushUpPhase.UP) {
            upPositionCount++
            downPositionCount = 0
        } else if (currentPhase == PushUpPhase.DOWN) {
            downPositionCount++
            upPositionCount = 0
        } else {
            // Transitioning - maintain counts
        }
        
        // Adaptive confirmation threshold based on movement speed
        var confirmedPhase = lastPhase
        
        if (upPositionCount >= adaptiveConfirmationThreshold) {
            confirmedPhase = PushUpPhase.UP
        } else if (downPositionCount >= adaptiveConfirmationThreshold) {
            confirmedPhase = PushUpPhase.DOWN
        }
        
        // Count rep when transitioning from DOWN to UP
        var newCount = _state.value.count
        if (lastPhase == PushUpPhase.DOWN && confirmedPhase == PushUpPhase.UP) {
            newCount++
        }
        
        // Update last phase
        lastPhase = confirmedPhase
        
        // Update state with speed information
        _state.value = PushUpState(
            count = newCount,
            phase = confirmedPhase,
            primaryAngle = lastElbowAngle,
            confidence = confidence + (avgVelocity / 100f).coerceIn(0f, 0.2f), // Boost confidence for speed
            isTracking = true,
            detectionMethod = detectionMethod
        )
    }
    
    /**
     * Calculate average elbow angle from both arms
     * Returns null if neither arm has reliable data
     */
    private fun calculateAverageElbowAngle(pose: Pose): Float? {
        val leftAngle = calculateElbowAngle(
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        )
        
        val rightAngle = calculateElbowAngle(
            pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        )
        
        return when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2
            leftAngle != null -> leftAngle
            rightAngle != null -> rightAngle
            else -> null
        }
    }
    
    /**
     * Calculate elbow angle (shoulder-elbow-wrist)
     */
    private fun calculateElbowAngle(
        shoulder: PoseLandmark?,
        elbow: PoseLandmark?,
        wrist: PoseLandmark?
    ): Float? {
        if (shoulder == null || elbow == null || wrist == null) return null
        if (shoulder.inFrameLikelihood < 0.5f || 
            elbow.inFrameLikelihood < 0.5f || 
            wrist.inFrameLikelihood < 0.5f) {
            return null
        }
        
        return AngleUtils.calculateAngle(
            shoulder.position.x, shoulder.position.y,
            elbow.position.x, elbow.position.y,
            wrist.position.x, wrist.position.y
        )
    }
    
    /**
     * Reset the detector
     */
    fun reset() {
        baselineHeadHeight = null
        baselineShoulderWidth = null
        maxHeadHeight = 0f
        minHeadHeight = Float.MAX_VALUE
        maxShoulderWidth = 0f
        minShoulderWidth = Float.MAX_VALUE
        lastElbowAngle = null
        upPositionCount = 0
        downPositionCount = 0
        lastPhase = PushUpPhase.UP
        lastHeadPositionY = null
        velocitySamples.clear()
        adaptiveConfirmationThreshold = 2
        movementSpeedFactor = 1.0f
        _state.value = PushUpState()
    }
    
    /**
     * Get current count
     */
    fun getCurrentCount(): Int = _state.value.count
    
    /**
     * Get current phase
     */
    fun getCurrentPhase(): PushUpPhase = _state.value.phase
}

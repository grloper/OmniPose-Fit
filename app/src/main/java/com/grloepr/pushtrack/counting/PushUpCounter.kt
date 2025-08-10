package com.grloepr.pushtrack.counting

import com.google.mlkit.vision.pose.Pose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Robust push-up counter with state machine and smoothing filters
 * Implements intelligent counting logic with form quality assessment
 */
class PushUpCounter {
    
    private var currentState = PushUpState.NEUTRAL
    private var lastStateChangeTime = 0L
    private var movementStartTime = 0L
    private var repCount = 0
    private var totalReps = 0
    
    // Elbow angle tracking for quality assessment
    private var currentElbowAngle: Float? = null
    private var minElbowAngleInMovement = Float.MAX_VALUE
    private var maxElbowAngleInMovement = Float.MIN_VALUE
    
    // Smoothing filter for angle measurements
    private val angleHistory = mutableListOf<Float>()
    private val maxHistorySize = 5
    
    // Thresholds for push-up detection
    private val upPositionElbowAngle = 160f      // Nearly straight arms
    private val downPositionElbowAngle = 90f     // 90 degree elbow bend
    private val minStateHoldTime = 300L          // Minimum time to hold a state (ms)
    private val maxMovementTime = 10000L         // Maximum time for one push-up (ms)
    
    // State flows for reactive UI updates
    private val _repCountFlow = MutableStateFlow(0)
    val repCountFlow: StateFlow<Int> = _repCountFlow.asStateFlow()
    
    private val _currentStateFlow = MutableStateFlow(PushUpState.NEUTRAL)
    val currentStateFlow: StateFlow<PushUpState> = _currentStateFlow.asStateFlow()
    
    private val _lastMovementFlow = MutableStateFlow<PushUpMovement?>(null)
    val lastMovementFlow: StateFlow<PushUpMovement?> = _lastMovementFlow.asStateFlow()
    
    /**
     * Process a new pose detection result
     */
    fun processPose(pose: Pose) {
        val currentTime = System.currentTimeMillis()
        
        // Check if arms are visible and person is in plank position
        if (!PoseAnalyzer.areArmsVisible(pose) || !PoseAnalyzer.isInPlankPosition(pose)) {
            // Reset to neutral if pose is not suitable for push-up detection
            if (currentState != PushUpState.NEUTRAL) {
                resetToNeutral()
            }
            return
        }
        
        // Calculate smoothed elbow angle
        val rawElbowAngle = PoseAnalyzer.calculateAverageElbowAngle(pose)
        if (rawElbowAngle == null) {
            return
        }
        
        val smoothedElbowAngle = applySmoothingFilter(rawElbowAngle)
        currentElbowAngle = smoothedElbowAngle
        
        // Track min/max angles during movement
        if (smoothedElbowAngle < minElbowAngleInMovement) {
            minElbowAngleInMovement = smoothedElbowAngle
        }
        if (smoothedElbowAngle > maxElbowAngleInMovement) {
            maxElbowAngleInMovement = smoothedElbowAngle
        }
        
        // State machine logic
        val newState = determineNewState(smoothedElbowAngle, currentTime)
        
        if (newState != currentState) {
            handleStateTransition(currentState, newState, currentTime)
            currentState = newState
            lastStateChangeTime = currentTime
            _currentStateFlow.value = newState
        }
    }
    
    /**
     * Apply smoothing filter to reduce jitter in angle measurements
     */
    private fun applySmoothingFilter(newAngle: Float): Float {
        angleHistory.add(newAngle)
        
        // Keep only recent history
        if (angleHistory.size > maxHistorySize) {
            angleHistory.removeAt(0)
        }
        
        // Return median value to reduce noise
        return angleHistory.sorted()[angleHistory.size / 2]
    }
    
    /**
     * Determine the new state based on current elbow angle and state machine logic
     */
    private fun determineNewState(elbowAngle: Float, currentTime: Long): PushUpState {
        val timeSinceLastChange = currentTime - lastStateChangeTime
        
        // Require minimum time in state to prevent rapid oscillations
        if (timeSinceLastChange < minStateHoldTime) {
            return currentState
        }
        
        return when (currentState) {
            PushUpState.NEUTRAL -> {
                if (elbowAngle < upPositionElbowAngle) {
                    PushUpState.DESCENDING
                } else {
                    PushUpState.UP_POSITION
                }
            }
            
            PushUpState.UP_POSITION -> {
                if (elbowAngle < upPositionElbowAngle - 10f) { // Add hysteresis
                    PushUpState.DESCENDING
                } else {
                    PushUpState.UP_POSITION
                }
            }
            
            PushUpState.DESCENDING -> {
                if (elbowAngle <= downPositionElbowAngle) {
                    PushUpState.DOWN_POSITION
                } else if (elbowAngle > upPositionElbowAngle - 5f) {
                    PushUpState.UP_POSITION
                } else {
                    PushUpState.DESCENDING
                }
            }
            
            PushUpState.DOWN_POSITION -> {
                if (elbowAngle > downPositionElbowAngle + 10f) { // Add hysteresis
                    PushUpState.ASCENDING
                } else {
                    PushUpState.DOWN_POSITION
                }
            }
            
            PushUpState.ASCENDING -> {
                if (elbowAngle >= upPositionElbowAngle) {
                    PushUpState.UP_POSITION
                } else if (elbowAngle < downPositionElbowAngle + 5f) {
                    PushUpState.DOWN_POSITION
                } else {
                    PushUpState.ASCENDING
                }
            }
        }
    }
    
    /**
     * Handle transitions between states
     */
    private fun handleStateTransition(
        fromState: PushUpState, 
        toState: PushUpState, 
        currentTime: Long
    ) {
        when {
            fromState == PushUpState.NEUTRAL && toState == PushUpState.DESCENDING -> {
                // Start of new push-up
                startNewMovement(currentTime)
            }
            
            fromState == PushUpState.UP_POSITION && toState == PushUpState.DESCENDING -> {
                // Start of new push-up from up position
                startNewMovement(currentTime)
            }
            
            fromState == PushUpState.ASCENDING && toState == PushUpState.UP_POSITION -> {
                // Completed push-up
                completeMovement(currentTime)
            }
        }
    }
    
    /**
     * Start tracking a new push-up movement
     */
    private fun startNewMovement(currentTime: Long) {
        movementStartTime = currentTime
        minElbowAngleInMovement = currentElbowAngle ?: Float.MAX_VALUE
        maxElbowAngleInMovement = currentElbowAngle ?: Float.MIN_VALUE
    }
    
    /**
     * Complete the current push-up movement and assess quality
     */
    private fun completeMovement(currentTime: Long) {
        val movementDuration = currentTime - movementStartTime
        
        // Validate movement duration
        if (movementDuration > maxMovementTime) {
            resetToNeutral()
            return
        }
        
        // Assess quality based on range of motion
        val rangeOfMotion = maxElbowAngleInMovement - minElbowAngleInMovement
        val quality = assessMovementQuality(rangeOfMotion, minElbowAngleInMovement, maxElbowAngleInMovement)
        
        // Only count reps with at least FAIR quality
        if (quality != PushUpQuality.POOR) {
            repCount++
            totalReps++
            _repCountFlow.value = repCount
            
            // Create movement record
            val movement = PushUpMovement(
                startTime = movementStartTime,
                endTime = currentTime,
                minElbowAngle = minElbowAngleInMovement,
                maxElbowAngle = maxElbowAngleInMovement,
                quality = quality
            )
            
            _lastMovementFlow.value = movement
        }
    }
    
    /**
     * Assess the quality of a push-up movement
     */
    private fun assessMovementQuality(
        rangeOfMotion: Float,
        minAngle: Float,
        maxAngle: Float
    ): PushUpQuality {
        return when {
            rangeOfMotion >= 70f && minAngle <= 90f && maxAngle >= 160f -> PushUpQuality.EXCELLENT
            rangeOfMotion >= 60f && minAngle <= 100f && maxAngle >= 150f -> PushUpQuality.GOOD
            rangeOfMotion >= 45f && minAngle <= 110f && maxAngle >= 140f -> PushUpQuality.FAIR
            else -> PushUpQuality.POOR
        }
    }
    
    /**
     * Reset counter to neutral state
     */
    private fun resetToNeutral() {
        currentState = PushUpState.NEUTRAL
        angleHistory.clear()
        _currentStateFlow.value = PushUpState.NEUTRAL
    }
    
    /**
     * Reset the rep counter
     */
    fun resetCounter() {
        repCount = 0
        totalReps = 0
        resetToNeutral()
        _repCountFlow.value = 0
        _lastMovementFlow.value = null
        minElbowAngleInMovement = Float.MAX_VALUE
        maxElbowAngleInMovement = Float.MIN_VALUE
    }
    
    /**
     * Get current rep count
     */
    fun getCurrentRepCount(): Int = repCount
    
    /**
     * Get total reps across all sessions
     */
    fun getTotalReps(): Int = totalReps
    
    /**
     * Get current elbow angle for debugging
     */
    fun getCurrentElbowAngle(): Float? = currentElbowAngle
}
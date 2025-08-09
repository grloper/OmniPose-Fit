package com.grloepr.pushtrack.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State machine for counting push-ups based on elbow angle thresholds
 * Optimized for fast response with reduced smoothing lag
 */
class PushUpCounter(
    private val downThreshold: Float = 70f,  // Angle threshold for "down" position
    private val upThreshold: Float = 160f,   // Angle threshold for "up" position
    private val debounceMs: Long = 150L      // Reduced debounce for faster response (was 250ms)
) {
    
    /**
     * Enum representing push-up phases
     */
    enum class Phase {
        UP,    // Arms extended (high angle)
        DOWN   // Arms bent (low angle)
    }
    
    /**
     * Data class representing the counter state
     */
    data class CounterState(
        val count: Int = 0,
        val phase: Phase = Phase.UP,
        val lastAngle: Float? = null,
        val isTracking: Boolean = false
    )
    
    private val _state = MutableStateFlow(CounterState())
    val state: StateFlow<CounterState> = _state.asStateFlow()
    
    private var lastPhaseChangeTime = 0L
    private val angleHistory = ArrayDeque<Float>(3) // Reduced from 5 to 3 for faster response
    private var lastProcessedAngle = 0f
    private val angleChangeThreshold = 5f // Skip processing if angle change is minimal
    
    /**
     * Process a new elbow angle measurement with frame skipping for performance
     * @param angle The elbow angle in degrees
     */
    fun processAngle(angle: Float) {
        // Skip processing if angle change is minimal (frame skipping optimization)
        if (angleHistory.isNotEmpty() && kotlin.math.abs(angle - lastProcessedAngle) < angleChangeThreshold) {
            return
        }
        
        val currentTime = System.currentTimeMillis()
        lastProcessedAngle = angle
        
        // Add angle to history for smoothing
        angleHistory.addLast(angle)
        if (angleHistory.size > 3) { // Reduced window size
            angleHistory.removeFirst()
        }
        
        // Calculate smoothed angle using faster EMA instead of median
        val smoothedAngle = calculateSmoothedAngle()
        
        val currentState = _state.value
        val newPhase = determinePhase(smoothedAngle, currentState.phase)
        
        // Check for state change with debounce
        if (newPhase != currentState.phase && 
            currentTime - lastPhaseChangeTime >= debounceMs) {
            
            lastPhaseChangeTime = currentTime
            
            // Increment count when transitioning from DOWN to UP (completing a push-up)
            val newCount = if (currentState.phase == Phase.DOWN && newPhase == Phase.UP) {
                currentState.count + 1
            } else {
                currentState.count
            }
            
            _state.value = currentState.copy(
                count = newCount,
                phase = newPhase,
                lastAngle = smoothedAngle,
                isTracking = true
            )
        } else {
            // Update angle without changing phase
            _state.value = currentState.copy(
                lastAngle = smoothedAngle,
                isTracking = true
            )
        }
    }
    
    /**
     * Calculate smoothed angle using Exponential Moving Average for faster response
     */
    private fun calculateSmoothedAngle(): Float {
        if (angleHistory.isEmpty()) return 0f
        if (angleHistory.size == 1) return angleHistory.first()
        
        // Use EMA (Exponential Moving Average) for faster response than median
        // Gives more weight to recent values
        val alpha = 0.7f // High alpha for responsiveness 
        var ema = angleHistory.first()
        for (i in 1 until angleHistory.size) {
            ema = alpha * angleHistory[i] + (1 - alpha) * ema
        }
        return ema
    }
    
    /**
     * Determine phase based on current angle and previous phase
     * Uses hysteresis to prevent rapid phase changes
     */
    private fun determinePhase(angle: Float, currentPhase: Phase): Phase {
        return when (currentPhase) {
            Phase.UP -> {
                // Currently in UP phase, check if we should transition to DOWN
                if (angle <= downThreshold) Phase.DOWN else Phase.UP
            }
            Phase.DOWN -> {
                // Currently in DOWN phase, check if we should transition to UP
                if (angle >= upThreshold) Phase.UP else Phase.DOWN
            }
        }
    }
    
    /**
     * Reset the counter to initial state
     */
    fun reset() {
        _state.value = CounterState()
        angleHistory.clear()
        lastPhaseChangeTime = 0L
        lastProcessedAngle = 0f
    }
    
    /**
     * Stop tracking (when pose is lost)
     */
    fun stopTracking() {
        _state.value = _state.value.copy(
            isTracking = false,
            lastAngle = null
        )
        angleHistory.clear()
        lastProcessedAngle = 0f
    }
    
    /**
     * Get current rep count
     */
    fun getCurrentCount(): Int = _state.value.count
    
    /**
     * Get current phase
     */
    fun getCurrentPhase(): Phase = _state.value.phase
    
    /**
     * Check if currently tracking pose
     */
    fun isTracking(): Boolean = _state.value.isTracking
}
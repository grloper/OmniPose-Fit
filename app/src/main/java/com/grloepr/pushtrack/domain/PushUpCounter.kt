package com.grloepr.pushtrack.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simplified and optimized push-up counter focused on reliable elbow angle tracking
 */
class PushUpCounter {
    enum class Phase { UP, DOWN }
    
    data class CounterState(
        val count: Int = 0,
        val phase: Phase = Phase.UP,
        val lastAngle: Float? = null,
        val isTracking: Boolean = false
    )
    
    // Optimized thresholds for reliable push-up detection
    private val downThreshold = 90f   // Angle below which we consider DOWN phase
    private val upThreshold = 150f    // Angle above which we consider UP phase
    
    // Debounce and smoothing
    private val debounceTimeMs = 200L // Slightly faster debounce for better responsiveness
    private var lastStateChangeTime = 0L
    private val angleHistory = ArrayDeque<Float>(3) // Smaller window for faster response
    
    // State management
    private val _state = MutableStateFlow(CounterState())
    val state: StateFlow<CounterState> = _state.asStateFlow()
    
    // Tracking variables
    private var repCount = 0
    private var currentPhase = Phase.UP
    private var isCurrentlyTracking = false
    private var lastCalculatedAngle: Float? = null
    
    /**
     * Process an angle measurement with optimized smoothing
     * @param angle The angle in degrees
     */
    fun processAngle(angle: Float) {
        // Add angle to history for smoothing
        angleHistory.addLast(angle)
        if (angleHistory.size > 3) { // Using smaller window for faster response
            angleHistory.removeFirst()
        }
        
        // Apply exponential moving average for smoother tracking
        val smoothedAngle = if (angleHistory.size >= 3) {
            // Weight recent angles more heavily (50% current, 30% previous, 20% oldest)
            angle * 0.5f + 
            angleHistory[angleHistory.size - 2] * 0.3f + 
            angleHistory[angleHistory.size - 3] * 0.2f
        } else {
            angle
        }
        
        lastCalculatedAngle = smoothedAngle
        isCurrentlyTracking = true
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastStateChangeTime < debounceTimeMs) {
            // Debounce period not elapsed, don't change state yet
            updateState()
            return
        }
        
        // Check for phase transitions with more reliable thresholds
        when (currentPhase) {
            Phase.UP -> {
                if (smoothedAngle < downThreshold) {
                    currentPhase = Phase.DOWN
                    lastStateChangeTime = currentTime
                }
            }
            Phase.DOWN -> {
                if (smoothedAngle > upThreshold) {
                    currentPhase = Phase.UP
                    repCount++
                    lastStateChangeTime = currentTime
                }
            }
        }
        
        updateState()
    }
    
    /**
     * Stop tracking due to missing landmarks
     */
    fun stopTracking() {
        isCurrentlyTracking = false
        lastCalculatedAngle = null
        updateState()
    }
    
    /**
     * Reset the counter
     */
    fun reset() {
        repCount = 0
        currentPhase = Phase.UP
        lastCalculatedAngle = null
        angleHistory.clear()
        updateState()
    }
    
    /**
     * Update the state flow with current values
     */
    private fun updateState() {
        _state.value = CounterState(
            count = repCount,
            phase = currentPhase,
            lastAngle = lastCalculatedAngle,
            isTracking = isCurrentlyTracking
        )
    }
    
    /**
     * Get current rep count
     */
    fun getCurrentCount() = repCount
    
    /**
     * Get current phase
     */
    fun getCurrentPhase() = currentPhase
}
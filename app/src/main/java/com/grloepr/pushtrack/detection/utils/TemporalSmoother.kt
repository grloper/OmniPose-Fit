package com.grloepr.pushtrack.detection.utils

import com.grloepr.pushtrack.detection.ExercisePhase

/**
 * Debounced state machine for exercise phase detection
 * Prevents rapid state changes and adds hysteresis
 */
class DebouncedStateMachine(
    private val confirmationThreshold: Int = 3,
    private val releaseTreshold: Int = 2
) {
    private var currentState: ExercisePhase = ExercisePhase.UP
    private var targetState: ExercisePhase = ExercisePhase.UP
    private var confirmationCount = 0
    private var releaseCount = 0
    
    /**
     * Update state machine with new detection
     * Returns confirmed state only after sufficient confirmations
     */
    fun updateState(detectedPhase: ExercisePhase): ExercisePhase {
        when {
            detectedPhase == currentState -> {
                // Same state detected, reset counters
                confirmationCount = 0
                releaseCount = 0
            }
            detectedPhase == targetState -> {
                // Moving toward target state
                confirmationCount++
                releaseCount = 0
                
                if (confirmationCount >= confirmationThreshold) {
                    // Confirmed state change
                    currentState = targetState
                    confirmationCount = 0
                }
            }
            detectedPhase != targetState -> {
                // New target state
                if (detectedPhase != currentState) {
                    targetState = detectedPhase
                    confirmationCount = 1
                    releaseCount = 0
                } else {
                    // Going back to current state
                    releaseCount++
                    if (releaseCount >= releaseTreshold) {
                        targetState = currentState
                        confirmationCount = 0
                        releaseCount = 0
                    }
                }
            }
        }
        
        return currentState
    }
    
    /**
     * Get current confirmed state
     */
    fun getCurrentState(): ExercisePhase = currentState
    
    /**
     * Reset state machine
     */
    fun reset(initialState: ExercisePhase = ExercisePhase.UP) {
        currentState = initialState
        targetState = initialState
        confirmationCount = 0
        releaseCount = 0
    }
    
    /**
     * Check if state is transitioning
     */
    fun isTransitioning(): Boolean = targetState != currentState
    
    /**
     * Get transition progress (0.0 to 1.0)
     */
    fun getTransitionProgress(): Float {
        return if (isTransitioning()) {
            confirmationCount.toFloat() / confirmationThreshold.toFloat()
        } else {
            0f
        }
    }
}
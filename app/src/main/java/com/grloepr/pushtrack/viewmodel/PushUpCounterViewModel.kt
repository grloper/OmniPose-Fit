package com.grloepr.pushtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * ViewModel for managing push-up counter state
 */
class PushUpCounterViewModel : ViewModel() {
    
    // Current rep count
    var repCount by mutableStateOf(0)
        private set
    
    // Whether counting is active
    var isCountingActive by mutableStateOf(false)
        private set
    
    /**
     * Increments the rep count
     */
    fun incrementRep() {
        repCount++
    }
    
    /**
     * Resets the rep count to zero
     */
    fun resetCount() {
        repCount = 0
    }
    
    /**
     * Starts the counting session
     */
    fun startCounting() {
        isCountingActive = true
    }
    
    /**
     * Stops the counting session
     */
    fun stopCounting() {
        isCountingActive = false
    }
    
    /**
     * Toggles the counting state
     */
    fun toggleCounting() {
        isCountingActive = !isCountingActive
    }
}
package com.grloepr.pushtrack.detection.utils

import com.grloepr.pushtrack.detection.ExercisePhase
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Advanced temporal smoothing for pose detection
 * Combines Exponential Moving Average and Kalman filtering for robust signal processing
 */
class TemporalSmoother(
    private val emaAlpha: Float = 0.3f,
    private val kalmanProcessNoise: Float = 0.1f,
    private val kalmanMeasurementNoise: Float = 0.5f
) {
    private var emaValue: Float? = null
    private var kalmanFilter: SimpleKalmanFilter? = null
    private var lastTimestamp = 0L
    private var outlierCount = 0
    private val maxOutliers = 3
    
    /**
     * Add a new sample and get smoothed value
     */
    fun addSample(value: Float, timestamp: Long = System.currentTimeMillis()): Float {
        // Initialize on first sample
        if (emaValue == null) {
            emaValue = value
            kalmanFilter = SimpleKalmanFilter(value, kalmanProcessNoise, kalmanMeasurementNoise)
            lastTimestamp = timestamp
            return value
        }
        
        // Calculate time delta for adaptive filtering
        val deltaTime = (timestamp - lastTimestamp).coerceAtLeast(1L) / 1000f // Convert to seconds
        lastTimestamp = timestamp
        
        // Detect outliers
        val emaSmoothed = updateEMA(value)
        val isOutlier = abs(value - emaSmoothed) > (emaSmoothed * 0.3f) // 30% deviation threshold
        
        if (isOutlier) {
            outlierCount++
            if (outlierCount >= maxOutliers) {
                // Reset EMA on too many outliers (likely pose change)
                emaValue = value
                kalmanFilter!!.reset(value)
                outlierCount = 0
                return value
            } else {
                // Use EMA for outlier handling
                return emaSmoothed
            }
        } else {
            outlierCount = 0
        }
        
        // Apply Kalman filter for final smoothing
        return kalmanFilter!!.update(value, deltaTime)
    }
    
    /**
     * Get current smoothed value without adding new sample
     */
    fun getCurrentValue(): Float? = kalmanFilter?.getCurrentEstimate()
    
    /**
     * Reset the smoother
     */
    fun reset() {
        emaValue = null
        kalmanFilter = null
        outlierCount = 0
    }
    
    /**
     * Update Exponential Moving Average
     */
    private fun updateEMA(newValue: Float): Float {
        emaValue = emaValue!! * (1 - emaAlpha) + newValue * emaAlpha
        return emaValue!!
    }
    
    /**
     * Simple Kalman filter for 1D signal smoothing
     */
    private class SimpleKalmanFilter(
        initialValue: Float,
        private val processNoise: Float,
        private val measurementNoise: Float
    ) {
        private var estimate = initialValue
        private var errorCovariance = 1f
        
        fun update(measurement: Float, deltaTime: Float): Float {
            // Prediction step
            val predictedEstimate = estimate
            val predictedErrorCovariance = errorCovariance + processNoise * deltaTime
            
            // Update step
            val kalmanGain = predictedErrorCovariance / (predictedErrorCovariance + measurementNoise)
            estimate = predictedEstimate + kalmanGain * (measurement - predictedEstimate)
            errorCovariance = (1 - kalmanGain) * predictedErrorCovariance
            
            return estimate
        }
        
        fun getCurrentEstimate(): Float = estimate
        
        fun reset(newValue: Float) {
            estimate = newValue
            errorCovariance = 1f
        }
    }
}

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
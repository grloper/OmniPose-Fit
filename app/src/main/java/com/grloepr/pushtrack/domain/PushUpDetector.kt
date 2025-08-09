package com.grloepr.pushtrack.domain

import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.domain.detection.ElbowAngleStrategy
import com.grloepr.pushtrack.domain.detection.VerticalMovementStrategy
import com.grloepr.pushtrack.domain.detection.FrontFacingStrategy
import com.grloepr.pushtrack.domain.detection.PushUpDetectionStrategy
import com.grloepr.pushtrack.domain.model.PushUpState
import com.grloepr.pushtrack.domain.model.PushUpPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main push-up detector that orchestrates multiple detection strategies
 * Uses the best available strategy based on visible body parts
 */
class PushUpDetector(
    private val debounceMs: Long = 150L
) {
    // Push-up counter state
    data class CounterState(
        val count: Int = 0,
        val phase: PushUpPhase = PushUpPhase.UP,
        val lastAngle: Float? = null,
        val isTracking: Boolean = false,
        val activeStrategy: String = "None",
        val confidence: Float = 0f
    )
    
    private val _state = MutableStateFlow(CounterState())
    val state: StateFlow<CounterState> = _state.asStateFlow()
    
    // Detection strategies in priority order
    private val strategies: List<PushUpDetectionStrategy> = listOf(
        ElbowAngleStrategy(),
        FrontFacingStrategy(),
        VerticalMovementStrategy()
    )
    
    private var lastPhaseChangeTime = 0L
    private var lastPushUpState: PushUpState? = null
    
    /**
     * Process new pose data to detect push-up state
     * @param pose The detected pose
     */
    fun processPose(pose: Pose) {
        if (!isValidPose(pose)) {
            stopTracking()
            return
        }
        
        // Find applicable strategies
        val applicableStrategies = strategies.filter { it.isApplicable(pose) }
        
        if (applicableStrategies.isEmpty()) {
            stopTracking()
            return
        }
        
        // Try each strategy and get the best result
        var bestResult: PushUpState? = null
        var bestConfidence = 0f
        var bestStrategy = ""
        
        for (strategy in applicableStrategies) {
            val result = strategy.detectPushUpState(pose, lastPushUpState)
            if (result != null && result.confidence > bestConfidence) {
                bestResult = result
                bestConfidence = result.confidence
                bestStrategy = strategy.name
            }
        }
        
        if (bestResult == null) {
            return
        }
        
        // Store the best result for next iteration
        lastPushUpState = bestResult
        
        // Update counter state
        updateCounterState(bestResult, bestStrategy)
    }
    
    /**
     * Update counter state based on detected push-up state
     */
    private fun updateCounterState(pushUpState: PushUpState, strategyName: String) {
        val currentTime = System.currentTimeMillis()
        val currentState = _state.value
        
        // Check if phase has changed
        if (pushUpState.phase != currentState.phase && 
            currentTime - lastPhaseChangeTime >= debounceMs) {
            
            lastPhaseChangeTime = currentTime
            
            // Increment count when transitioning from DOWN to UP or TRANSITIONING_UP
            val newCount = if (currentState.phase == PushUpPhase.DOWN && 
                              (pushUpState.phase == PushUpPhase.UP || pushUpState.phase == PushUpPhase.TRANSITIONING_UP)) {
                currentState.count + 1
            } else {
                currentState.count
            }
            
            _state.value = currentState.copy(
                count = newCount,
                phase = pushUpState.phase,
                lastAngle = pushUpState.lastAngle,
                isTracking = true,
                activeStrategy = strategyName,
                confidence = pushUpState.confidence
            )
        } else {
            // Update tracking state without changing phase
            _state.value = currentState.copy(
                lastAngle = pushUpState.lastAngle,
                isTracking = true,
                activeStrategy = strategyName,
                confidence = pushUpState.confidence
            )
        }
    }
    
    /**
     * Reset the counter to initial state
     */
    fun reset() {
        _state.value = CounterState()
        lastPushUpState = null
        lastPhaseChangeTime = 0L
        
        // Reset any strategies with state
        strategies.forEach {
            if (it is VerticalMovementStrategy) it.reset()
            if (it is FrontFacingStrategy) it.reset()
        }
    }
    
    /**
     * Stop tracking (when pose is lost)
     */
    private fun stopTracking() {
        _state.value = _state.value.copy(
            isTracking = false,
            lastAngle = null,
            activeStrategy = "None",
            confidence = 0f
        )
        lastPushUpState = null
    }
    
    /**
     * Check if pose has enough visible landmarks to be processed
     */
    private fun isValidPose(pose: Pose): Boolean {
        // At minimum, we need some upper body landmarks
        return pose.allPoseLandmarks.isNotEmpty() && 
               pose.allPoseLandmarks.any { it.inFrameLikelihood > 0.5f }
    }
    
    /**
     * Get current rep count
     */
    fun getCurrentCount(): Int = _state.value.count
    
    /**
     * Get current phase
     */
    fun getCurrentPhase(): PushUpPhase = _state.value.phase
    
    /**
     * Check if currently tracking pose
     */
    fun isTracking(): Boolean = _state.value.isTracking
    
    /**
     * Get active detection strategy
     */
    fun getActiveStrategy(): String = _state.value.activeStrategy
}

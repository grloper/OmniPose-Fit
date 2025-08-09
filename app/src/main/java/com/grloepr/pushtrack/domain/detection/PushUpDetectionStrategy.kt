package com.grloepr.pushtrack.domain.detection

import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.domain.model.PushUpState

/**
 * Interface for different push-up detection strategies
 * Allows the system to switch between detection methods based on visible body parts
 */
interface PushUpDetectionStrategy {
    /**
     * Name of the strategy for logging and debugging
     */
    val name: String
    
    /**
     * Check if this strategy can be applied with the current pose data
     * @param pose The current detected pose
     * @return true if this strategy can be used
     */
    fun isApplicable(pose: Pose): Boolean
    
    /**
     * Process the current pose to detect push-up state
     * @param pose The current detected pose
     * @param previousState The previous push-up state for context
     * @return The updated push-up state or null if detection failed
     */
    fun detectPushUpState(pose: Pose, previousState: PushUpState?): PushUpState?
    
    /**
     * Get confidence level of this detection (0.0-1.0)
     * Used when multiple strategies are applicable
     */
    fun getConfidence(): Float
}

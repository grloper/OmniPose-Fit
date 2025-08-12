package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import kotlinx.coroutines.flow.StateFlow

/**
 * Common interface for all exercise detectors
 * Enables modular, strategy-based exercise detection
 */
interface ExerciseDetector {
    /**
     * The type of exercise this detector handles
     */
    val exerciseType: ExerciseType
    
    /**
     * Current state of the exercise detection
     */
    val state: StateFlow<ExerciseState>
    
    /**
     * Process a pose frame for exercise detection
     * Must run in O(1) time complexity
     * @param pose The detected pose from ML Kit
     */
    fun processPose(pose: Pose)
    
    /**
     * Reset the detector state
     */
    fun reset()
    
    /**
     * Get current rep count
     */
    fun getRepCount(): Int
    
    /**
     * Get current exercise phase
     */
    fun getCurrentPhase(): ExercisePhase

    /**
     * Adjust detection sensitivity (1.0 = default). Lower -> stricter, Higher -> more permissive.
     */
    fun setSensitivity(factor: Float)
}

/**
 * Types of supported exercises
 */
enum class ExerciseType {
    PUSH_UP,
    PULL_UP,
    SQUAT
}

/**
 * Common exercise phases
 */
enum class ExercisePhase {
    UP,           // Extended/starting position
    DOWN,         // Contracted/bottom position  
    TRANSITIONING // Moving between positions
}

/**
 * Common state data for exercise detection
 */
data class ExerciseState(
    val count: Int = 0,
    val phase: ExercisePhase = ExercisePhase.UP,
    val primaryAngle: Float? = null,
    val confidence: Float = 0f,
    val isTracking: Boolean = false,
    val detectionMethod: String = "none"
)
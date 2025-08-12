package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.detection.utils.VelocityTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Abstract base class for exercise detectors
 * Provides common functionality and ensures O(1) complexity
 */
abstract class BaseExerciseDetector(
    override val exerciseType: ExerciseType
) : ExerciseDetector {
    
    // State management
    protected val _state = MutableStateFlow(ExerciseState())
    override val state: StateFlow<ExerciseState> = _state.asStateFlow()
    
    // O(1) position tracking - no growing lists
    protected var lastPhase = ExercisePhase.UP
    protected var upPositionCount = 0
    protected var downPositionCount = 0
    
    // O(1) velocity tracking with fixed buffer
    protected val velocityTracker = VelocityTracker(bufferSize = 5)
    
    // Fixed-size state tracking for form analysis
    protected var lastPrimaryAngle: Float? = null
    
    /**
     * Template method for pose processing
     * Ensures consistent O(1) behavior across all detectors
     */
    final override fun processPose(pose: Pose) {
        // Calculate primary angle for this exercise type
        val primaryAngle = calculatePrimaryAngle(pose)
        
        // Track velocity if we have angle data
        primaryAngle?.let { angle ->
            velocityTracker.addSample(angle)
            lastPrimaryAngle = angle
        }
        
        // Determine exercise phase based on angle and exercise-specific logic
        val detectedPhase = determinePhase(pose, primaryAngle)
        val confidence = calculateConfidence(pose, primaryAngle)
        val detectionMethod = getDetectionMethod()
        
        // Update position counts for state confirmation
        updatePositionCounts(detectedPhase)
        
        // Get adaptive threshold based on movement speed
        val confirmationThreshold = velocityTracker.getAdaptiveThreshold()
        
        // Determine confirmed phase
        val confirmedPhase = getConfirmedPhase(confirmationThreshold)
        
        // Count rep if transitioning from DOWN to UP
        var newCount = _state.value.count
        if (lastPhase == ExercisePhase.DOWN && confirmedPhase == ExercisePhase.UP) {
            newCount++
        }
        
        // Update last phase
        lastPhase = confirmedPhase
        
        // Update state
        _state.value = ExerciseState(
            count = newCount,
            phase = confirmedPhase,
            primaryAngle = primaryAngle,
            confidence = confidence,
            isTracking = primaryAngle != null,
            detectionMethod = detectionMethod
        )
    }
    
    /**
     * Reset detector to initial state
     */
    override fun reset() {
        lastPhase = ExercisePhase.UP
        upPositionCount = 0
        downPositionCount = 0
        velocityTracker.reset()
        lastPrimaryAngle = null
        _state.value = ExerciseState()
    }
    
    /**
     * Get current rep count
     */
    override fun getRepCount(): Int = _state.value.count
    
    /**
     * Get current exercise phase
     */
    override fun getCurrentPhase(): ExercisePhase = _state.value.phase
    
    /**
     * Update position counts based on detected phase
     * Maintains O(1) complexity with simple counters
     */
    private fun updatePositionCounts(detectedPhase: ExercisePhase) {
        when (detectedPhase) {
            ExercisePhase.UP -> {
                upPositionCount++
                downPositionCount = 0
            }
            ExercisePhase.DOWN -> {
                downPositionCount++
                upPositionCount = 0
            }
            ExercisePhase.TRANSITIONING -> {
                // Keep existing counts
            }
        }
    }
    
    /**
     * Get confirmed phase based on position counts and adaptive threshold
     */
    private fun getConfirmedPhase(confirmationThreshold: Int): ExercisePhase {
        return when {
            upPositionCount >= confirmationThreshold -> ExercisePhase.UP
            downPositionCount >= confirmationThreshold -> ExercisePhase.DOWN
            else -> lastPhase // Keep current phase if not enough confirmations
        }
    }
    
    // Abstract methods to be implemented by specific exercise detectors
    
    /**
     * Calculate the primary angle for this exercise type
     * @param pose The detected pose
     * @return Primary angle in degrees, or null if not detectable
     */
    abstract fun calculatePrimaryAngle(pose: Pose): Float?
    
    /**
     * Determine exercise phase based on pose and angle
     * @param pose The detected pose
     * @param primaryAngle The calculated primary angle
     * @return Detected exercise phase
     */
    abstract fun determinePhase(pose: Pose, primaryAngle: Float?): ExercisePhase
    
    /**
     * Calculate confidence score for current detection
     * @param pose The detected pose
     * @param primaryAngle The calculated primary angle
     * @return Confidence score (0.0 to 1.0)
     */
    abstract fun calculateConfidence(pose: Pose, primaryAngle: Float?): Float
    
    /**
     * Get description of detection method being used
     * @return String describing the detection method
     */
    abstract fun getDetectionMethod(): String
}
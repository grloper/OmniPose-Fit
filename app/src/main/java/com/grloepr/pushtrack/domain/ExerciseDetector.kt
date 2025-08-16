package com.grloepr.pushtrack.domain

import com.google.mlkit.vision.pose.Pose

/**
 * Generic interface for exercise detection
 */
interface ExerciseDetector {
    
    /**
     * The type of exercise this detector handles
     */
    val exerciseType: ExerciseType
    
    /**
     * Process a pose and update rep count with form analysis
     * @param pose The detected pose
     * @return DetectionResult with count, state, and analysis
     */
    fun processPose(pose: Pose): DetectionResult
    
    /**
     * Get current rep count
     */
    fun getRepCount(): Int
    
    /**
     * Get current exercise state
     */
    fun getCurrentState(): ExerciseState
    
    /**
     * Reset the detector state
     */
    fun reset()
    
    /**
     * Perform calibration if needed for this exercise type
     * @param pose Calibration pose
     * @return true if calibration was successful
     */
    fun calibrate(pose: Pose): Boolean = true
    
    /**
     * Check if calibration is required for this exercise
     */
    fun requiresCalibration(): Boolean = false
    
    /**
     * Check if detector is calibrated and ready
     */
    fun isCalibrated(): Boolean = true
}
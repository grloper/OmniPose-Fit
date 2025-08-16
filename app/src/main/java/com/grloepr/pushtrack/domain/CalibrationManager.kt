package com.grloepr.pushtrack.domain

import com.google.mlkit.vision.pose.Pose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Calibration state for exercises
 */
data class CalibrationState(
    val isCalibrating: Boolean = false,
    val countdown: Int = 0,
    val instruction: String = "",
    val isComplete: Boolean = false,
    val isSuccessful: Boolean = false
)

/**
 * Manages calibration flow for exercises that require it
 */
class CalibrationManager {
    
    private val _calibrationState = MutableStateFlow(CalibrationState())
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()
    
    private var calibrationCountdown = 0
    private var currentDetector: ExerciseDetector? = null
    
    /**
     * Start calibration for the given detector
     */
    fun startCalibration(detector: ExerciseDetector) {
        if (!detector.requiresCalibration()) {
            _calibrationState.value = CalibrationState(isComplete = true, isSuccessful = true)
            return
        }
        
        currentDetector = detector
        calibrationCountdown = 3 // 3 second countdown
        
        val instruction = when (detector.exerciseType) {
            ExerciseType.PULL_UP -> "Hang from the bar with arms extended. Hold still."
            ExerciseType.SQUAT -> "Stand upright with feet shoulder-width apart. Hold still."
            ExerciseType.PUSH_UP -> "Position yourself for push-ups. Hold still."
        }
        
        _calibrationState.value = CalibrationState(
            isCalibrating = true,
            countdown = calibrationCountdown,
            instruction = instruction
        )
    }
    
    /**
     * Process a pose during calibration
     * Call this every frame during calibration
     */
    fun processPose(pose: Pose) {
        val currentState = _calibrationState.value
        if (!currentState.isCalibrating || currentDetector == null) return
        
        // Decrement countdown
        if (calibrationCountdown > 0) {
            calibrationCountdown--
            _calibrationState.value = currentState.copy(countdown = calibrationCountdown)
            
            if (calibrationCountdown == 0) {
                // Attempt calibration
                val success = currentDetector!!.calibrate(pose)
                _calibrationState.value = CalibrationState(
                    isComplete = true,
                    isSuccessful = success,
                    instruction = if (success) "Calibration successful!" else "Calibration failed. Try again."
                )
            }
        }
    }
    
    /**
     * Reset calibration state
     */
    fun reset() {
        _calibrationState.value = CalibrationState()
        calibrationCountdown = 0
        currentDetector = null
    }
    
    /**
     * Check if calibration is needed for the detector
     */
    fun isCalibrationNeeded(detector: ExerciseDetector): Boolean {
        return detector.requiresCalibration() && !detector.isCalibrated()
    }
}
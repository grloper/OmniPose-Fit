package com.grloepr.pushtrack.viewmodel

import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import com.grloepr.pushtrack.analysis.PoseFrameResult
import com.grloepr.pushtrack.analysis.CombinedDetectionResult
import com.grloepr.pushtrack.domain.AngleUtils
import com.grloepr.pushtrack.domain.PushUpCounter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Use enhanced detection results for maximum accuracy

/**
 * Ultra-Enhanced UI state for the push-up counter screen with face detection support
 */
data class PushUpCounterUiState(
    val repCount: Int = 0,
    val phase: PushUpCounter.Phase = PushUpCounter.Phase.UP,
    val lastAngle: Float? = null,
    val isTracking: Boolean = false,
    val currentPoseFrame: PoseFrameResult? = null,
    val currentCombinedResult: CombinedDetectionResult? = null, // Enhanced with face detection
    val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
)

/**
 * Ultra-Performance ViewModel for managing push-up counter state with enhanced detection
 */
class PushUpCounterViewModel : ViewModel() {
    
    private val pushUpCounter = PushUpCounter() // Ultra-performance counter with motion prediction
    
    private val _cameraSelector = MutableStateFlow(CameraSelector.DEFAULT_BACK_CAMERA)
    private val _currentPoseFrame = MutableStateFlow<PoseFrameResult?>(null)
    private val _currentCombinedResult = MutableStateFlow<CombinedDetectionResult?>(null)
    
    val cameraSelector: StateFlow<CameraSelector> = _cameraSelector.asStateFlow()
    val currentPoseFrame: StateFlow<PoseFrameResult?> = _currentPoseFrame.asStateFlow()
    val currentCombinedResult: StateFlow<CombinedDetectionResult?> = _currentCombinedResult.asStateFlow()
    
    // Ultra-Enhanced UI state combining counter, camera, and detection results
    val uiState: StateFlow<PushUpCounterUiState> = combine(
        pushUpCounter.state,
        _cameraSelector,
        _currentPoseFrame,
        _currentCombinedResult
    ) { counterState: PushUpCounter.CounterState,
        selector: CameraSelector,
        poseFrame: PoseFrameResult?,
        combinedResult: CombinedDetectionResult? ->
        PushUpCounterUiState(
            repCount = counterState.count,
            phase = counterState.phase,
            lastAngle = counterState.lastAngle,
            isTracking = counterState.isTracking,
            currentPoseFrame = poseFrame,
            currentCombinedResult = combinedResult,
            cameraSelector = selector
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PushUpCounterUiState()
    )
    
    /**
     * Ultra-Performance: Process combined detection result from camera analyzer
     * @param combinedResult The combined pose and face detection result
     */
    fun processCombinedDetectionResult(combinedResult: CombinedDetectionResult) {
        viewModelScope.launch {
            _currentCombinedResult.value = combinedResult
            
            // Process pose if available
            combinedResult.pose?.let { pose ->
                // Create pose frame for compatibility
                val poseFrame = PoseFrameResult(
                    pose = pose,
                    imageWidth = combinedResult.imageWidth,
                    imageHeight = combinedResult.imageHeight,
                    rotationDegrees = combinedResult.rotationDegrees,
                    isFrontCamera = combinedResult.isFrontCamera
                )
                _currentPoseFrame.value = poseFrame
                
                // Calculate elbow angle and update counter with ultra-performance
                val angleResult = AngleUtils.getBestElbowAngle(pose)
                if (angleResult != null) {
                    val (angle, _) = angleResult
                    pushUpCounter.processAngle(angle)
                } else {
                    // No arm detected, stop tracking
                    pushUpCounter.stopTracking()
                }
            } ?: run {
                // No pose detected, stop tracking
                pushUpCounter.stopTracking()
            }
        }
    }
    
    /**
     * Legacy method for backward compatibility: Process pose detection result from camera analyzer
     * @param poseResult The pose detection result with image dimensions
     * @param rotationDegrees Camera rotation in degrees (0, 90, 180, 270)
     */
    fun processPoseResult(poseResult: PoseDetectionResult, rotationDegrees: Int = 0) {
        viewModelScope.launch {
            val isFrontCamera = _cameraSelector.value == CameraSelector.DEFAULT_FRONT_CAMERA
            
            // Create enhanced pose frame result
            val poseFrame = PoseFrameResult(
                pose = poseResult.pose,
                imageWidth = poseResult.imageWidth,
                imageHeight = poseResult.imageHeight,
                rotationDegrees = rotationDegrees,
                isFrontCamera = isFrontCamera
            )
            
            _currentPoseFrame.value = poseFrame
            
            // Calculate elbow angle and update counter
            val angleResult = AngleUtils.getBestElbowAngle(poseResult.pose)
            if (angleResult != null) {
                val (angle, _) = angleResult
                pushUpCounter.processAngle(angle)
            } else {
                // No arm detected, stop tracking
                pushUpCounter.stopTracking()
            }
        }
    }
    
    /**
     * Toggle between front and back camera
     */
    fun toggleCamera() {
        viewModelScope.launch {
            _cameraSelector.value = if (_cameraSelector.value == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
        }
    }
    
    /**
     * Reset the push-up counter
     */
    fun reset() {
        viewModelScope.launch {
            pushUpCounter.reset()
        }
    }
    
    /**
     * Get current rep count
     */
    fun getCurrentCount(): Int = pushUpCounter.getCurrentCount()
    
    /**
     * Get current phase as string for display
     */
    fun getCurrentPhaseString(): String {
        return when (pushUpCounter.getCurrentPhase()) {
            PushUpCounter.Phase.UP -> "Up"
            PushUpCounter.Phase.DOWN -> "Down"
        }
    }
    
    /**
     * Check if front camera is currently selected
     */
    fun isFrontCamera(): Boolean = _cameraSelector.value == CameraSelector.DEFAULT_FRONT_CAMERA
}
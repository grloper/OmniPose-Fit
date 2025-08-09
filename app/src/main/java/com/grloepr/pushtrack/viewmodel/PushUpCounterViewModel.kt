package com.grloepr.pushtrack.viewmodel

import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import com.grloepr.pushtrack.analysis.PoseFrameResult
import com.grloepr.pushtrack.domain.AngleUtils
import com.grloepr.pushtrack.domain.PushUpCounter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Simplified UI state for the push-up counter screen
 */
data class PushUpCounterUiState(
    val repCount: Int = 0,
    val phase: PushUpCounter.Phase = PushUpCounter.Phase.UP,
    val lastAngle: Float? = null,
    val isTracking: Boolean = false,
    val currentPoseFrame: PoseFrameResult? = null,
    val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
)

/**
 * Optimized ViewModel for managing push-up counter state
 */
class PushUpCounterViewModel : ViewModel() {
    
    private val pushUpCounter = PushUpCounter()
    
    private val _cameraSelector = MutableStateFlow(CameraSelector.DEFAULT_BACK_CAMERA)
    private val _currentPoseFrame = MutableStateFlow<PoseFrameResult?>(null)
    
    val cameraSelector: StateFlow<CameraSelector> = _cameraSelector.asStateFlow()
    val currentPoseFrame: StateFlow<PoseFrameResult?> = _currentPoseFrame.asStateFlow()
    
    // Optimized UI state
    val uiState: StateFlow<PushUpCounterUiState> = combine(
        pushUpCounter.state,
        _cameraSelector,
        _currentPoseFrame
    ) { counterState, selector, poseFrame ->
        PushUpCounterUiState(
            repCount = counterState.count,
            phase = counterState.phase,
            lastAngle = counterState.lastAngle,
            isTracking = counterState.isTracking,
            currentPoseFrame = poseFrame,
            cameraSelector = selector
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PushUpCounterUiState()
    )
    
    /**
     * Process pose frame result with optimized angle detection
     */
    fun processPoseFrame(poseFrame: PoseFrameResult) {
        viewModelScope.launch {
            _currentPoseFrame.value = poseFrame
            
            // Calculate elbow angle and update counter
            val angleResult = AngleUtils.getBestElbowAngle(poseFrame.pose)
            if (angleResult != null) {
                val (angle, _) = angleResult
                pushUpCounter.processAngle(angle)
            } else {
                pushUpCounter.stopTracking()
            }
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    fun processPoseResult(poseResult: PoseDetectionResult, rotationDegrees: Int = 0) {
        viewModelScope.launch {
            val isFrontCamera = _cameraSelector.value == CameraSelector.DEFAULT_FRONT_CAMERA
            
            val poseFrame = PoseFrameResult(
                pose = poseResult.pose,
                imageWidth = poseResult.imageWidth,
                imageHeight = poseResult.imageHeight,
                rotationDegrees = rotationDegrees,
                isFrontCamera = isFrontCamera
            )
            
            processPoseFrame(poseFrame)
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
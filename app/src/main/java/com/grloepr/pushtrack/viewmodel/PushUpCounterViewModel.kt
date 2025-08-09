package com.grloepr.pushtrack.viewmodel

import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import com.grloepr.pushtrack.domain.AngleUtils
import com.grloepr.pushtrack.domain.PushUpCounter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Enhanced data class to hold pose detection results with frame metadata
 */
data class PoseFrameResult(
    val pose: Pose,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val isFrontCamera: Boolean
)

/**
 * UI state for the push-up counter screen
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
 * ViewModel for managing push-up counter state and camera functionality
 */
class PushUpCounterViewModel : ViewModel() {
    
    private val pushUpCounter = PushUpCounter()
    
    private val _cameraSelector = MutableStateFlow(CameraSelector.DEFAULT_BACK_CAMERA)
    private val _currentPoseFrame = MutableStateFlow<PoseFrameResult?>(null)
    
    val cameraSelector: StateFlow<CameraSelector> = _cameraSelector.asStateFlow()
    val currentPoseFrame: StateFlow<PoseFrameResult?> = _currentPoseFrame.asStateFlow()
    
    // Combine counter state with camera state for UI
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
    }
    
    /**
     * Process pose detection result from camera analyzer
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
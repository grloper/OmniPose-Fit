package com.grloepr.pushtrack.analysis

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.pose.PoseDetectorClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Data class to hold pose detection results with image dimensions and metadata
 */
data class PoseDetectionResult(
    val pose: Pose,
    val imageWidth: Int,
    val imageHeight: Int
)

/**
 * Enhanced data class to hold pose detection results with frame metadata for overlay alignment
 */
data class PoseFrameResult(
    val pose: Pose,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val isFrontCamera: Boolean
)

/**
 * ImageAnalyzer that processes camera frames for pose detection
 * Implements frame throttling to target ~30 FPS for fast push-up detection and runs detection off main thread
 */
class ImageAnalyzer(
    private val poseDetectorClient: PoseDetectorClient
) : ImageAnalysis.Analyzer {
    
    private val analysisScope = CoroutineScope(Dispatchers.Default)
    private var lastAnalysisTime = 0L
    private var isProcessing = false
    
    // Legacy pose results for backward compatibility
    private val _poseResults = MutableSharedFlow<PoseDetectionResult>(replay = 1)
    val poseResults: SharedFlow<PoseDetectionResult> = _poseResults.asSharedFlow()
    
    // Enhanced pose results with frame metadata
    private val _poseFrameResults = MutableSharedFlow<PoseFrameResult>(replay = 1)
    val poseFrameResults: SharedFlow<PoseFrameResult> = _poseFrameResults.asSharedFlow()
    
    // Camera facing direction - will be set by the camera binding
    private var isFrontCamera: Boolean = false
    
    // Performance optimization: track frame skipping
    private var frameSkipCounter = 0
    private val skipFramesWhenIdle = 2 // Skip 2 frames when no significant pose changes
    private var currentTargetFps = 30 // Start with 30 FPS, can be adjusted dynamically
    
    /**
     * Dynamically adjust target FPS for performance optimization
     * @param fps Target frames per second (15-60)
     */
    fun setTargetFps(fps: Int) {
        currentTargetFps = fps.coerceIn(15, 60)
        // Update interval based on new FPS
        val newInterval = 1000L / currentTargetFps
        // Only update if not currently processing to avoid race conditions
        if (!isProcessing) {
            // targetAnalysisInterval will be calculated dynamically
        }
    }
    
    /**
     * Get current analysis interval based on target FPS
     */
    private fun getCurrentAnalysisInterval(): Long = 1000L / currentTargetFps
    
    /**
     * Set the camera facing direction for proper overlay transformation
     * @param frontCamera true if front camera is being used
     */
    fun setCameraFacing(frontCamera: Boolean) {
        isFrontCamera = frontCamera
    }
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        // Throttle analysis to target FPS and skip if already processing
        if (currentTime - lastAnalysisTime < getCurrentAnalysisInterval() || isProcessing) {
            imageProxy.close()
            return
        }
        
        // Additional frame skipping for performance when processing is stable
        frameSkipCounter++
        if (frameSkipCounter <= skipFramesWhenIdle && isProcessing) {
            imageProxy.close()
            return
        }
        frameSkipCounter = 0
        
        lastAnalysisTime = currentTime
        isProcessing = true
        
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // Convert ImageProxy to InputImage with proper rotation
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            
            // Store image dimensions and metadata for coordinate transformation
            val imageWidth = inputImage.width
            val imageHeight = inputImage.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            
            // Process pose detection on background thread
            poseDetectorClient.detectPose(
                image = inputImage,
                onSuccess = { pose ->
                    // Emit pose results with image dimensions to collectors on background thread
                    analysisScope.launch {
                        // Legacy result for backward compatibility
                        _poseResults.tryEmit(PoseDetectionResult(pose, imageWidth, imageHeight))
                        
                        // Enhanced result with frame metadata
                        _poseFrameResults.tryEmit(
                            PoseFrameResult(
                                pose = pose,
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                rotationDegrees = rotationDegrees,
                                isFrontCamera = isFrontCamera
                            )
                        )
                        
                        isProcessing = false
                        imageProxy.close()
                    }
                },
                onFailure = { exception ->
                    // Log error but continue processing
                    analysisScope.launch {
                        println("Pose detection failed: ${exception.message}")
                        isProcessing = false
                        imageProxy.close()
                    }
                }
            )
        } else {
            isProcessing = false
            imageProxy.close()
        }
    }
}
package com.grloepr.pushtrack.analysis

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.face.Face
import com.grloepr.pushtrack.pose.PoseDetectorClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Data classes to hold pose detection results
 */
data class PoseDetectionResult(
    val pose: Pose,
    val imageWidth: Int,
    val imageHeight: Int
)

data class PoseFrameResult(
    val pose: Pose,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val isFrontCamera: Boolean
)

/**
 * Combined result containing both pose and face detection data
 */
data class CombinedDetectionResult(
    val pose: Pose?,
    val faces: List<Face>,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val isFrontCamera: Boolean
)

/**
 * High-performance ImageAnalyzer with improved visibility for debugging
 */
class ImageAnalyzer(
    private val poseDetectorClient: PoseDetectorClient
) : ImageAnalysis.Analyzer {
    
    private val TAG = "ImageAnalyzer"
    
    private val analysisScope = CoroutineScope(Dispatchers.Default)
    private var lastAnalysisTime = 0L
    private var isProcessing = false
    
    // Shared flows for pose detection results
    private val _poseResults = MutableSharedFlow<PoseDetectionResult>(replay = 1)
    val poseResults: SharedFlow<PoseDetectionResult> = _poseResults.asSharedFlow()
    
    private val _poseFrameResults = MutableSharedFlow<PoseFrameResult>(replay = 1)
    val poseFrameResults: SharedFlow<PoseFrameResult> = _poseFrameResults.asSharedFlow()
    
    // Camera facing direction
    private var isFrontCamera = false
    
    // Performance optimization settings
    private var targetFps = 30 // Default target FPS
    private var frameSkipCount = 0
    private var dynamicFrameSkip = 2 // Skip every Nth frame by default
    private val angleChangeThreshold = 3.0f // Skip processing for small angle changes
    
    // Last processed pose data for change detection
    private var lastProcessedPose: Pose? = null
    
    // Performance monitoring
    private var processingTimeTotal = 0L
    private var frameCount = 0
    private var lastPerformanceAdjustTime = 0L
    private var performanceMode = PerformanceMode.BALANCED


    // Performance modes
    enum class PerformanceMode {
        HIGH_QUALITY, // Process more frames, better detection but higher CPU/battery usage
        BALANCED,     // Default balance between performance and quality
        HIGH_SPEED    // Process fewer frames, prioritize UI smoothness
    }
    
    /**
     * Set performance mode for camera analysis
     */
    fun setPerformanceMode(mode: PerformanceMode) {
        performanceMode = mode
        
        // Adjust frame skipping based on performance mode
        dynamicFrameSkip = when (mode) {
            PerformanceMode.HIGH_QUALITY -> 1  // Process almost every frame
            PerformanceMode.BALANCED -> 2      // Process every other frame
            PerformanceMode.HIGH_SPEED -> 4    // Process every fourth frame for maximum smoothness
        }
        
        // Adjust target FPS based on performance mode
        targetFps = when (mode) {
            PerformanceMode.HIGH_QUALITY -> 15
            PerformanceMode.BALANCED -> 24
            PerformanceMode.HIGH_SPEED -> 60   // Very high target FPS for camera preview smoothness
        }
        
        Log.d(TAG, "Performance mode set to $mode: FPS=$targetFps, Skip=$dynamicFrameSkip")
    }
    
    /**
     * Set camera facing direction for proper coordinate transformation
     */
    fun setCameraFacing(frontCamera: Boolean) {
        isFrontCamera = frontCamera
    }
    
    /**
     * Calculate the minimum time between frame processing based on target FPS
     */
    private fun getMinProcessingInterval(): Long {
        return 1000L / targetFps
    }
    
    /**
     * Check if significant pose change has occurred since last processed frame
     */
    private fun hasSignificantPoseChange(newPose: Pose): Boolean {
        val lastPose = lastProcessedPose ?: return true // Process if no previous pose
        
        // Get key landmarks to compare
        val leftShoulder = newPose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = newPose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = newPose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_ELBOW)
        val rightElbow = newPose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_ELBOW)
        
        // Previous landmarks
        val prevLeftShoulder = lastPose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER)
        val prevRightShoulder = lastPose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER)
        val prevLeftElbow = lastPose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_ELBOW)
        val prevRightElbow = lastPose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_ELBOW)
        
        // Check for significant changes in key landmarks
        if (leftElbow != null && prevLeftElbow != null) {
            val changeX = abs(leftElbow.position.x - prevLeftElbow.position.x)
            val changeY = abs(leftElbow.position.y - prevLeftElbow.position.y)
            if (changeX > 5f || changeY > 5f) return true
        }
        
        if (rightElbow != null && prevRightElbow != null) {
            val changeX = abs(rightElbow.position.x - prevRightElbow.position.x)
            val changeY = abs(rightElbow.position.y - prevRightElbow.position.y)
            if (changeX > 5f || changeY > 5f) return true
        }
        
        return false // No significant change detected
    }
    
    /**
     * Adjust performance settings based on processing time
     */
    private fun adjustPerformanceSettings() {
        val currentTime = System.currentTimeMillis()
        
        // Only adjust every 2 seconds
        if (currentTime - lastPerformanceAdjustTime < 2000 || frameCount < 5) {
            return
        }
        
        // Calculate average processing time
        val avgProcessingTime = processingTimeTotal / frameCount.toFloat()
        
        // If processing is taking too long (> 33ms per frame), increase frame skipping
        if (avgProcessingTime > 33 && dynamicFrameSkip < 4) {
            dynamicFrameSkip++
        } 
        // If processing is very fast (< 15ms per frame), decrease frame skipping
        else if (avgProcessingTime < 15 && dynamicFrameSkip > 1) {
            dynamicFrameSkip--
        }
        
        // Reset counters
        processingTimeTotal = 0
        frameCount = 0
        lastPerformanceAdjustTime = currentTime
    }
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val minInterval = getMinProcessingInterval()
        
        // Skip frames but not too aggressively for better detection
        if (currentTime - lastAnalysisTime < minInterval) {
            imageProxy.close()
            return
        }
        
        if (isProcessing) {
            imageProxy.close()
            return
        }
        
        // Use less aggressive frame skipping for better detection
        frameSkipCount++
        if (frameSkipCount < dynamicFrameSkip) {
            imageProxy.close()
            return
        }
        frameSkipCount = 0
        
        val processingStartTime = System.currentTimeMillis()
        lastAnalysisTime = currentTime
        isProcessing = true
        
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing = false
            imageProxy.close()
            return
        }
        
        try {
            // Convert ImageProxy to InputImage
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            
            val imageWidth = inputImage.width
            val imageHeight = inputImage.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            
            // Process pose detection with improved error handling
            poseDetectorClient.detectPose(
                image = inputImage,
                onSuccess = { pose ->
                    // Always process pose for better visibility when debugging
                    lastProcessedPose = pose
                    
                    // Log landmark count for debugging
                    val visibleLandmarks = pose.allPoseLandmarks.count { it.inFrameLikelihood > 0.5f }
                    if (visibleLandmarks > 0) {
                        Log.d(TAG, "Processing pose with $visibleLandmarks visible landmarks")
                    }
                    
                    analysisScope.launch {
                        try {
                            // Emit both result types for maximum compatibility
                            _poseResults.emit(PoseDetectionResult(pose, imageWidth, imageHeight))
                            
                            _poseFrameResults.emit(
                                PoseFrameResult(
                                    pose = pose,
                                    imageWidth = imageWidth,
                                    imageHeight = imageHeight,
                                    rotationDegrees = rotationDegrees,
                                    isFrontCamera = isFrontCamera
                                )
                            )
                        } finally {
                            isProcessing = false
                        }
                    }
                    
                    val processingTime = System.currentTimeMillis() - processingStartTime
                    processingTimeTotal += processingTime
                    frameCount++
                    
                    adjustPerformanceSettings()
                    imageProxy.close()
                },
                onFailure = { exception ->
                    Log.e(TAG, "Pose detection failed: ${exception.message}")
                    isProcessing = false
                    imageProxy.close()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image: ${e.message}")
            isProcessing = false
            imageProxy.close()
        }
    }
}
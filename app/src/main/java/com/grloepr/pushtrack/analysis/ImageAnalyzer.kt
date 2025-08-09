package com.grloepr.pushtrack.analysis

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.face.Face
import com.grloepr.pushtrack.pose.PoseDetectorClient
import com.grloepr.pushtrack.face.FaceDetectorClient
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
 * Ultra-Enhanced data class combining pose and face detection for maximum accuracy
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
 * Ultra-Performance ImageAnalyzer for real-time pose and face detection
 * Implements adaptive 60+ FPS processing with intelligent frame management for 100% smooth tracking
 */
class ImageAnalyzer(
    private val poseDetectorClient: PoseDetectorClient,
    private val faceDetectorClient: FaceDetectorClient? = null // Optional face detection for enhanced accuracy
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
    
    // Ultra-Enhanced combined detection results
    private val _combinedDetectionResults = MutableSharedFlow<CombinedDetectionResult>(replay = 1)
    val combinedDetectionResults: SharedFlow<CombinedDetectionResult> = _combinedDetectionResults.asSharedFlow()
    
    // Camera facing direction - will be set by the camera binding
    private var isFrontCamera: Boolean = false
    
    // Ultra-Performance optimization: adaptive frame management
    private var frameSkipCounter = 0
    private var currentTargetFps = 60 // Start with 60 FPS for ultra-smooth tracking
    private var adaptiveSkipFrames = 0 // Dynamic skip count based on movement intensity
    
    // Motion-based performance optimization
    private var lastPosePosition: Pair<Float, Float>? = null // Track movement for adaptive FPS
    private var movementIntensity = 0f // 0.0 = static, 1.0 = high movement
    private val movementHistory = ArrayDeque<Float>(5) // Track movement over time
    
    // Frame quality assessment
    private var processedFrameCount = 0
    private var skippedFrameCount = 0
    private var lastPerformanceCheck = 0L
    
    /**
     * Ultra-Performance: Dynamically adjust target FPS with intelligent adaptation
     * @param fps Target frames per second (15-120 for extreme performance)
     */
    fun setTargetFps(fps: Int) {
        currentTargetFps = fps.coerceIn(15, 120) // Allow up to 120 FPS for extreme responsiveness
        updateAdaptiveSkipping()
    }
    
    /**
     * Get current analysis interval based on target FPS and movement intensity
     */
    private fun getCurrentAnalysisInterval(): Long {
        // Adaptive interval based on movement: faster processing during movement
        val adaptiveFps = when {
            movementIntensity > 0.7f -> currentTargetFps * 1.2f // Boost FPS during high movement
            movementIntensity > 0.4f -> currentTargetFps.toFloat() // Normal FPS during moderate movement  
            else -> currentTargetFps * 0.8f // Reduce FPS during static periods to save battery
        }
        return (1000L / adaptiveFps.coerceAtMost(120f)).toLong()
    }
    
    /**
     * Ultra-Performance: Update adaptive frame skipping based on performance metrics
     */
    private fun updateAdaptiveSkipping() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPerformanceCheck > 1000) { // Check every second
            val totalFrames = processedFrameCount + skippedFrameCount
            val processingRatio = if (totalFrames > 0) processedFrameCount.toFloat() / totalFrames else 1f
            
            // Adjust skipping based on processing capability
            adaptiveSkipFrames = when {
                processingRatio > 0.9f && movementIntensity < 0.3f -> 3 // Skip more during static periods
                processingRatio > 0.7f && movementIntensity < 0.5f -> 2 // Moderate skipping
                movementIntensity > 0.7f -> 0 // No skipping during high movement
                else -> 1 // Default minimal skipping
            }
            
            // Reset counters
            processedFrameCount = 0
            skippedFrameCount = 0
            lastPerformanceCheck = currentTime
        }
    }
    
    /**
     * Calculate movement intensity based on pose position changes
     */
    private fun calculateMovementIntensity(poseFrame: PoseFrameResult) {
        // Calculate center of upper body for movement tracking
        val pose = poseFrame.pose
        val leftShoulder = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER)
        
        if (leftShoulder != null && rightShoulder != null) {
            val centerX = (leftShoulder.position.x + rightShoulder.position.x) / 2f
            val centerY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
            val currentPosition = Pair(centerX, centerY)
            
            lastPosePosition?.let { lastPos ->
                val deltaX = currentPosition.first - lastPos.first
                val deltaY = currentPosition.second - lastPos.second
                val movement = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
                
                // Normalize movement to 0-1 scale (adjust multiplier based on typical movements)
                val normalizedMovement = (movement / 50f).coerceAtMost(1f)
                
                // Add to movement history
                movementHistory.addLast(normalizedMovement)
                if (movementHistory.size > 5) {
                    movementHistory.removeFirst()
                }
                
                // Calculate average movement intensity
                movementIntensity = if (movementHistory.isEmpty()) 0f else movementHistory.average().toFloat()
            }
            
            lastPosePosition = currentPosition
        }
    }
    
    /**
     * Ultra-Performance: Process pose and face detection in parallel for enhanced accuracy
     */
    private fun processWithCombinedDetection(
        inputImage: InputImage,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
        imageProxy: ImageProxy
    ) {
        var poseResult: Pose? = null
        var faceResults: List<Face> = emptyList()
        var completedTasks = 0
        val totalTasks = 2
        
        fun onTaskCompleted() {
            completedTasks++
            if (completedTasks == totalTasks) {
                // Both detection tasks completed, emit results
                analysisScope.launch {
                    try {
                        // Legacy pose result for backward compatibility
                        poseResult?.let { pose ->
                            _poseResults.tryEmit(PoseDetectionResult(pose, imageWidth, imageHeight))
                            
                            // Enhanced pose result with frame metadata
                            val poseFrame = PoseFrameResult(
                                pose = pose,
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                rotationDegrees = rotationDegrees,
                                isFrontCamera = isFrontCamera
                            )
                            _poseFrameResults.tryEmit(poseFrame)
                            
                            // Update movement tracking for adaptive FPS
                            calculateMovementIntensity(poseFrame)
                        }
                        
                        // Ultra-Enhanced combined result
                        val combinedResult = CombinedDetectionResult(
                            pose = poseResult,
                            faces = faceResults,
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            rotationDegrees = rotationDegrees,
                            isFrontCamera = isFrontCamera
                        )
                        _combinedDetectionResults.tryEmit(combinedResult)
                        
                        updateAdaptiveSkipping()
                        
                    } finally {
                        isProcessing = false
                        imageProxy.close()
                    }
                }
            }
        }
        
        // Start pose detection
        poseDetectorClient.detectPose(
            image = inputImage,
            onSuccess = { pose ->
                poseResult = pose
                onTaskCompleted()
            },
            onFailure = { exception ->
                println("Pose detection failed: ${exception.message}")
                onTaskCompleted()
            }
        )
        
        // Start face detection in parallel
        faceDetectorClient?.detectFaces(
            image = inputImage,
            onSuccess = { faces ->
                faceResults = faces
                onTaskCompleted()
            },
            onFailure = { exception ->
                println("Face detection failed: ${exception.message}")
                onTaskCompleted()
            }
        )
    }
    
    /**
     * Process pose detection only (for compatibility)
     */
    private fun processPoseOnly(
        inputImage: InputImage,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
        imageProxy: ImageProxy
    ) {
        poseDetectorClient.detectPose(
            image = inputImage,
            onSuccess = { pose ->
                analysisScope.launch {
                    try {
                        // Legacy result for backward compatibility
                        _poseResults.tryEmit(PoseDetectionResult(pose, imageWidth, imageHeight))
                        
                        // Enhanced result with frame metadata
                        val poseFrame = PoseFrameResult(
                            pose = pose,
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            rotationDegrees = rotationDegrees,
                            isFrontCamera = isFrontCamera
                        )
                        
                        _poseFrameResults.tryEmit(poseFrame)
                        
                        // Update movement tracking for adaptive FPS
                        calculateMovementIntensity(poseFrame)
                        updateAdaptiveSkipping()
                        
                    } finally {
                        isProcessing = false
                        imageProxy.close()
                    }
                }
            },
            onFailure = { exception ->
                analysisScope.launch {
                    try {
                        println("Pose detection failed: ${exception.message}")
                    } finally {
                        isProcessing = false
                        imageProxy.close()
                    }
                }
            }
        )
    }
    
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
        
        // Ultra-Performance: Smart throttling with adaptive intervals
        if (currentTime - lastAnalysisTime < getCurrentAnalysisInterval()) {
            imageProxy.close()
            skippedFrameCount++
            return
        }
        
        // Skip processing if already processing (prevent queue buildup)
        if (isProcessing) {
            imageProxy.close()
            skippedFrameCount++
            return
        }
        
        // Ultra-Performance: Intelligent frame skipping based on movement
        frameSkipCounter++
        if (frameSkipCounter <= adaptiveSkipFrames && movementIntensity < 0.5f) {
            imageProxy.close()
            skippedFrameCount++
            return
        }
        frameSkipCounter = 0
        
        lastAnalysisTime = currentTime
        isProcessing = true
        processedFrameCount++
        
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
            
            // Ultra-Performance: Process pose and face detection in parallel for maximum accuracy
            if (faceDetectorClient != null) {
                // Combined detection for enhanced accuracy
                processWithCombinedDetection(inputImage, imageWidth, imageHeight, rotationDegrees, imageProxy)
            } else {
                // Pose-only detection for compatibility
                processPoseOnly(inputImage, imageWidth, imageHeight, rotationDegrees, imageProxy)
            }
        } else {
            isProcessing = false
            imageProxy.close()
        }
    }
}
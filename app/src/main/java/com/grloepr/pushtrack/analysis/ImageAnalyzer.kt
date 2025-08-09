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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Data class to hold pose detection results with image dimensions
 */
data class PoseDetectionResult(
    val pose: Pose,
    val imageWidth: Int,
    val imageHeight: Int
)

/**
 * Enhanced data class for pose frame results with camera metadata
 */
data class PoseFrameResult(
    val pose: Pose,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val isFrontCamera: Boolean
)

/**
 * Combined detection result with both pose and face data
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
 * Optimized ImageAnalyzer focused on reliable pose detection with better performance
 */
class ImageAnalyzer(
    private val poseDetectorClient: PoseDetectorClient,
    private val faceDetectorClient: FaceDetectorClient? = null
) : ImageAnalysis.Analyzer {
    
    private val analysisScope = CoroutineScope(Dispatchers.Default)
    private var lastAnalysisTime = 0L
    private val processingLock = AtomicBoolean(false)
    
    // Fixed analysis interval for stable performance (25 FPS = 40ms)
    private val targetAnalysisIntervalMs = 40L
    
    // Pose detection results
    private val _poseResults = MutableSharedFlow<PoseDetectionResult>(replay = 1)
    val poseResults: SharedFlow<PoseDetectionResult> = _poseResults.asSharedFlow()
    
    // Enhanced pose frame results
    private val _poseFrameResults = MutableSharedFlow<PoseFrameResult>(replay = 1)
    val poseFrameResults: SharedFlow<PoseFrameResult> = _poseFrameResults.asSharedFlow()
    
    // Combined detection results
    private val _combinedDetectionResults = MutableSharedFlow<CombinedDetectionResult>(replay = 1)
    val combinedDetectionResults: SharedFlow<CombinedDetectionResult> = _combinedDetectionResults.asSharedFlow()
    
    // Camera facing direction
    private var isFrontCamera: Boolean = false
    
    /**
     * Set the camera facing direction for proper overlay transformation
     */
    fun setCameraFacing(frontCamera: Boolean) {
        isFrontCamera = frontCamera
    }
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        // Skip if not enough time has passed since last analysis (frame rate control)
        if (currentTime - lastAnalysisTime < targetAnalysisIntervalMs) {
            imageProxy.close()
            return
        }
        
        // Skip if already processing to prevent backpressure
        if (!processingLock.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        
        lastAnalysisTime = currentTime
        
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            
            val imageWidth = inputImage.width
            val imageHeight = inputImage.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            
            // If we have a face detector, use combined detection
            if (faceDetectorClient != null) {
                processCombinedDetection(inputImage, imageWidth, imageHeight, rotationDegrees, imageProxy)
            } else {
                // Otherwise just do pose detection
                processPoseOnly(inputImage, imageWidth, imageHeight, rotationDegrees, imageProxy)
            }
        } else {
            processingLock.set(false)
            imageProxy.close()
        }
    }
    
    /**
     * Process combined pose and face detection
     */
    private fun processCombinedDetection(
        inputImage: InputImage,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
        imageProxy: ImageProxy
    ) {
        var poseResult: Pose? = null
        var faceResults = listOf<Face>()
        var completedTasks = 0
        val totalTasks = 2
        
        fun onTaskCompleted() {
            completedTasks++
            if (completedTasks == totalTasks) {
                // Both detection tasks completed, emit results
                analysisScope.launch {
                    try {
                        // If we have a pose, emit pose-specific results
                        poseResult?.let { pose ->
                            // Emit basic pose result
                            _poseResults.emit(PoseDetectionResult(pose, imageWidth, imageHeight))
                            
                            // Emit enhanced pose frame
                            _poseFrameResults.emit(
                                PoseFrameResult(
                                    pose = pose,
                                    imageWidth = imageWidth,
                                    imageHeight = imageHeight,
                                    rotationDegrees = rotationDegrees,
                                    isFrontCamera = isFrontCamera
                                )
                            )
                        }
                        
                        // Emit combined result
                        _combinedDetectionResults.emit(
                            CombinedDetectionResult(
                                pose = poseResult,
                                faces = faceResults,
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                rotationDegrees = rotationDegrees,
                                isFrontCamera = isFrontCamera
                            )
                        )
                    } finally {
                        processingLock.set(false)
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
                onTaskCompleted()
            }
        )
        
        // Start face detection
        faceDetectorClient?.detectFaces(
            image = inputImage,
            onSuccess = { faces ->
                faceResults = faces
                onTaskCompleted()
            },
            onFailure = { exception ->
                onTaskCompleted()
            }
        ) ?: onTaskCompleted() // If no face detector, complete this task immediately
    }
    
    /**
     * Process pose detection only
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
                        // Emit basic pose result
                        _poseResults.emit(PoseDetectionResult(pose, imageWidth, imageHeight))
                        
                        // Emit enhanced pose frame with metadata
                        _poseFrameResults.emit(
                            PoseFrameResult(
                                pose = pose,
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                rotationDegrees = rotationDegrees,
                                isFrontCamera = isFrontCamera
                            )
                        )
                        
                        // Also emit as a combined result with no faces
                        _combinedDetectionResults.emit(
                            CombinedDetectionResult(
                                pose = pose,
                                faces = emptyList(),
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                rotationDegrees = rotationDegrees,
                                isFrontCamera = isFrontCamera
                            )
                        )
                    } finally {
                        processingLock.set(false)
                        imageProxy.close()
                    }
                }
            },
            onFailure = { exception ->
                processingLock.set(false)
                imageProxy.close()
            }
        )
    }
}
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
 * Data class to hold pose detection results with image dimensions
 */
data class PoseDetectionResult(
    val pose: Pose,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int
)

/**
 * ImageAnalyzer that processes camera frames for pose detection
 * Implements frame throttling to target ~15 FPS and runs detection off main thread
 */
class ImageAnalyzer(
    private val poseDetectorClient: PoseDetectorClient
) : ImageAnalysis.Analyzer {
    
    private val analysisScope = CoroutineScope(Dispatchers.Default)
    private var lastAnalysisTime = 0L
    private val targetAnalysisInterval = 1000L / 15L // ~15 FPS (66ms between frames)
    private var isProcessing = false
    
    private val _poseResults = MutableSharedFlow<PoseDetectionResult>(replay = 1)
    val poseResults: SharedFlow<PoseDetectionResult> = _poseResults.asSharedFlow()
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        // Throttle analysis to target FPS and skip if already processing
        if (currentTime - lastAnalysisTime < targetAnalysisInterval || isProcessing) {
            imageProxy.close()
            return
        }
        
        lastAnalysisTime = currentTime
        isProcessing = true
        
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // Get the ACTUAL media image dimensions (before rotation)
            val mediaWidth = mediaImage.width
            val mediaHeight = mediaImage.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            
            // Convert ImageProxy to InputImage with proper rotation
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                rotationDegrees
            )
            
            // Debug logging (run first time only to avoid spam)
            if (lastAnalysisTime == currentTime - targetAnalysisInterval) {
                println("ImageAnalyzer: media=${mediaWidth}×${mediaHeight}, inputImage=${inputImage.width}×${inputImage.height}, rotation=$rotationDegrees°")
            }
            
            // Use the media dimensions, not the InputImage dimensions
            // InputImage dimensions are already rotated, but we need original for proper coordinate mapping
            val imageWidth = mediaWidth
            val imageHeight = mediaHeight
            
            // Process pose detection on background thread
            poseDetectorClient.detectPose(
                image = inputImage,
                onSuccess = { pose ->
                    // Emit pose results with image dimensions to collectors on background thread
                    analysisScope.launch {
                        _poseResults.tryEmit(
                            PoseDetectionResult(
                                pose = pose,
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                rotationDegrees = rotationDegrees
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
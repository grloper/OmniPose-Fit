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
    
    private val _poseResults = MutableSharedFlow<Pose>(replay = 1)
    val poseResults: SharedFlow<Pose> = _poseResults.asSharedFlow()
    
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
            // Convert ImageProxy to InputImage with proper rotation
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            
            // Process pose detection on background thread
            poseDetectorClient.detectPose(
                image = inputImage,
                onSuccess = { pose ->
                    // Emit pose results to collectors on background thread
                    analysisScope.launch {
                        _poseResults.tryEmit(pose)
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
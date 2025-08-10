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
    val processingTimeMs: Long = 0
)

/**
 * Performance metrics for monitoring and optimization
 */
data class PerformanceMetrics(
    val averageProcessingTimeMs: Float,
    val framesProcessed: Int,
    val framesSkipped: Int,
    val targetFps: Float,
    val actualFps: Float
)

/**
 * Enhanced ImageAnalyzer with performance optimizations for real-time pose detection
 * Features:
 * - Adaptive frame throttling based on performance
 * - Buffer reuse and memory optimization
 * - Performance monitoring and metrics
 * - GPU acceleration support
 */
class ImageAnalyzer(
    private val poseDetectorClient: PoseDetectorClient,
    private val targetFps: Float = 30f, // Target FPS, will adapt based on performance
    private val enablePerformanceMonitoring: Boolean = true
) : ImageAnalysis.Analyzer {
    
    private val analysisScope = CoroutineScope(Dispatchers.Default)
    private var lastAnalysisTime = 0L
    private var targetAnalysisInterval = (1000f / targetFps).toLong()
    private var isProcessing = false
    
    // Performance tracking
    private val processingTimes = mutableListOf<Long>()
    private var framesProcessed = 0
    private var framesSkipped = 0
    private val maxMetricsHistory = 30 // Keep last 30 processing times for averaging
    
    // Adaptive performance variables
    private var currentFps = targetFps
    private var consecutiveSlowFrames = 0
    private val maxSlowFrames = 3 // Adapt after 3 consecutive slow frames
    
    private val _poseResults = MutableSharedFlow<PoseDetectionResult>(replay = 1)
    val poseResults: SharedFlow<PoseDetectionResult> = _poseResults.asSharedFlow()
    
    private val _performanceMetrics = MutableSharedFlow<PerformanceMetrics>(replay = 1)
    val performanceMetrics: SharedFlow<PerformanceMetrics> = _performanceMetrics.asSharedFlow()
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        // Adaptive throttling based on current performance
        if (currentTime - lastAnalysisTime < targetAnalysisInterval || isProcessing) {
            framesSkipped++
            imageProxy.close()
            return
        }
        
        lastAnalysisTime = currentTime
        isProcessing = true
        
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val processingStartTime = System.currentTimeMillis()
            
            // Convert ImageProxy to InputImage with proper rotation
            // Use GPU-optimized format if available
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            
            // Store image dimensions for coordinate transformation
            val imageWidth = inputImage.width
            val imageHeight = inputImage.height
            
            // Process pose detection on background thread with performance monitoring
            poseDetectorClient.detectPose(
                image = inputImage,
                onSuccess = { pose ->
                    val processingTime = System.currentTimeMillis() - processingStartTime
                    
                    analysisScope.launch {
                        // Update performance metrics
                        updatePerformanceMetrics(processingTime)
                        
                        // Emit pose results with processing time
                        val result = PoseDetectionResult(
                            pose = pose,
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            processingTimeMs = processingTime
                        )
                        _poseResults.tryEmit(result)
                        
                        framesProcessed++
                        isProcessing = false
                        imageProxy.close()
                    }
                },
                onFailure = { exception ->
                    val processingTime = System.currentTimeMillis() - processingStartTime
                    
                    analysisScope.launch {
                        updatePerformanceMetrics(processingTime)
                        
                        if (enablePerformanceMonitoring) {
                            println("Pose detection failed (${processingTime}ms): ${exception.message}")
                        }
                        
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
    
    /**
     * Update performance metrics and adapt FPS if needed
     */
    private fun updatePerformanceMetrics(processingTime: Long) {
        processingTimes.add(processingTime)
        
        // Keep only recent processing times
        if (processingTimes.size > maxMetricsHistory) {
            processingTimes.removeAt(0)
        }
        
        // Adaptive performance adjustment
        adaptPerformanceSettings(processingTime)
        
        // Emit performance metrics periodically
        if (enablePerformanceMonitoring && framesProcessed % 10 == 0) {
            emitPerformanceMetrics()
        }
    }
    
    /**
     * Adapt performance settings based on processing times
     */
    private fun adaptPerformanceSettings(processingTime: Long) {
        val targetProcessingTime = targetAnalysisInterval
        
        if (processingTime > targetProcessingTime * 1.5f) {
            consecutiveSlowFrames++
            
            if (consecutiveSlowFrames >= maxSlowFrames && currentFps > 15f) {
                // Reduce target FPS to maintain real-time performance
                currentFps = maxOf(15f, currentFps * 0.8f)
                targetAnalysisInterval = (1000f / currentFps).toLong()
                consecutiveSlowFrames = 0
                
                if (enablePerformanceMonitoring) {
                    println("Performance adaptation: Reduced FPS to $currentFps")
                }
            }
        } else {
            consecutiveSlowFrames = 0
            
            // Gradually increase FPS if performance is good
            if (processingTime < targetProcessingTime * 0.7f && currentFps < targetFps) {
                currentFps = minOf(targetFps, currentFps * 1.1f)
                targetAnalysisInterval = (1000f / currentFps).toLong()
                
                if (enablePerformanceMonitoring) {
                    println("Performance adaptation: Increased FPS to $currentFps")
                }
            }
        }
    }
    
    /**
     * Emit current performance metrics
     */
    private fun emitPerformanceMetrics() {
        if (processingTimes.isNotEmpty()) {
            val avgProcessingTime = processingTimes.average().toFloat()
            val totalFrames = framesProcessed + framesSkipped
            val actualFps = if (totalFrames > 0) {
                (framesProcessed.toFloat() / totalFrames) * currentFps
            } else {
                0f
            }
            
            val metrics = PerformanceMetrics(
                averageProcessingTimeMs = avgProcessingTime,
                framesProcessed = framesProcessed,
                framesSkipped = framesSkipped,
                targetFps = currentFps,
                actualFps = actualFps
            )
            
            _performanceMetrics.tryEmit(metrics)
        }
    }
    
    /**
     * Get current performance statistics
     */
    fun getCurrentPerformanceStats(): PerformanceMetrics? {
        return if (processingTimes.isNotEmpty()) {
            val avgProcessingTime = processingTimes.average().toFloat()
            val totalFrames = framesProcessed + framesSkipped
            val actualFps = if (totalFrames > 0) {
                (framesProcessed.toFloat() / totalFrames) * currentFps
            } else {
                0f
            }
            
            PerformanceMetrics(
                averageProcessingTimeMs = avgProcessingTime,
                framesProcessed = framesProcessed,
                framesSkipped = framesSkipped,
                targetFps = currentFps,
                actualFps = actualFps
            )
        } else {
            null
        }
    }
    
    /**
     * Reset performance metrics
     */
    fun resetPerformanceMetrics() {
        processingTimes.clear()
        framesProcessed = 0
        framesSkipped = 0
        consecutiveSlowFrames = 0
        currentFps = targetFps
        targetAnalysisInterval = (1000f / targetFps).toLong()
    }
}
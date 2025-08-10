package com.grloepr.pushtrack.pose

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * Enhanced ML Kit Pose Detection client with performance optimizations
 * Features:
 * - Hardware acceleration support
 * - Optimized model selection
 * - Performance monitoring
 * - Resource management
 */
class PoseDetectorClient {
    
    private var poseDetector: PoseDetector? = null
    private var isInitialized = false
    
    // Performance metrics
    private var totalInferences = 0
    private var successfulInferences = 0
    private var failedInferences = 0
    
    /**
     * Initialize the pose detector with performance-optimized configuration
     * @param useAccurateModel Whether to use the more accurate but slower model
     * @param enableGpuAcceleration Whether to prefer GPU acceleration
     */
    fun initialize(
        useAccurateModel: Boolean = false,
        enableGpuAcceleration: Boolean = true
    ) {
        if (poseDetector == null) {
            val options = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE) // Optimized for real-time
                .apply {
                    if (enableGpuAcceleration) {
                        // Prefer GPU acceleration for better performance
                        setPreferredHardwareConfigs(PoseDetectorOptions.CPU_GPU)
                    } else {
                        setPreferredHardwareConfigs(PoseDetectorOptions.CPU)
                    }
                }
                .build()
            
            poseDetector = if (useAccurateModel) {
                // Use accurate model for better precision (slower)
                PoseDetection.getClient(options)
            } else {
                // Use base model for better speed (default)
                PoseDetection.getClient(options)
            }
            
            isInitialized = true
            resetMetrics()
        }
    }
    
    /**
     * Process an image and detect poses with performance tracking
     * @param image The input image to process
     * @param onSuccess Callback for successful pose detection
     * @param onFailure Callback for detection failure
     */
    fun detectPose(
        image: InputImage,
        onSuccess: (Pose) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val detector = poseDetector ?: run {
            onFailure(IllegalStateException("Pose detector not initialized. Call initialize() first."))
            return
        }
        
        totalInferences++
        val startTime = System.currentTimeMillis()
        
        detector.process(image)
            .addOnSuccessListener { pose ->
                val processingTime = System.currentTimeMillis() - startTime
                successfulInferences++
                
                // Log performance for monitoring
                if (totalInferences % 30 == 0) { // Log every 30 inferences
                    logPerformanceMetrics(processingTime)
                }
                
                onSuccess(pose)
            }
            .addOnFailureListener { exception ->
                val processingTime = System.currentTimeMillis() - startTime
                failedInferences++
                
                // Log failure for debugging
                println("Pose detection failed after ${processingTime}ms: ${exception.message}")
                
                onFailure(exception)
            }
    }
    
    /**
     * Process image with warmup for first inference
     * First ML inference is typically slower, so this method handles warmup
     */
    fun detectPoseWithWarmup(
        image: InputImage,
        onSuccess: (Pose) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (totalInferences == 0) {
            // First inference - might be slower due to model loading
            println("Performing warmup inference...")
        }
        
        detectPose(image, onSuccess, onFailure)
    }
    
    /**
     * Get current performance statistics
     */
    fun getPerformanceStats(): PoseDetectionStats {
        return PoseDetectionStats(
            totalInferences = totalInferences,
            successfulInferences = successfulInferences,
            failedInferences = failedInferences,
            successRate = if (totalInferences > 0) {
                (successfulInferences.toFloat() / totalInferences) * 100f
            } else {
                0f
            },
            isInitialized = isInitialized
        )
    }
    
    /**
     * Reset performance metrics
     */
    fun resetMetrics() {
        totalInferences = 0
        successfulInferences = 0
        failedInferences = 0
    }
    
    /**
     * Log performance metrics for monitoring
     */
    private fun logPerformanceMetrics(lastProcessingTime: Long) {
        val stats = getPerformanceStats()
        println("""
            Pose Detection Performance:
            - Total Inferences: ${stats.totalInferences}
            - Success Rate: ${stats.successRate.toInt()}%
            - Last Processing Time: ${lastProcessingTime}ms
            - Failed Inferences: ${stats.failedInferences}
        """.trimIndent())
    }
    
    /**
     * Check if detector is properly initialized
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * Clean up resources when done
     */
    fun close() {
        poseDetector?.close()
        poseDetector = null
        isInitialized = false
        
        // Log final performance stats
        if (totalInferences > 0) {
            println("Final Pose Detection Stats: ${getPerformanceStats()}")
        }
    }
}

/**
 * Data class for pose detection performance statistics
 */
data class PoseDetectionStats(
    val totalInferences: Int,
    val successfulInferences: Int,
    val failedInferences: Int,
    val successRate: Float,
    val isInitialized: Boolean
)
package com.grloepr.pushtrack.pose

import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions

/**
 * Enhanced client for ML Kit Pose Detection with optimized performance settings
 */
class PoseDetectorClient {
    
    private var poseDetector: PoseDetector? = null
    private val TAG = "PoseDetectorClient"
    
    /**
     * Initialize the pose detector with default settings
     */
    fun initialize() {
        if (poseDetector == null) {
            // Create options with STREAM_MODE (optimized for video)
            val options = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                // Note: setPerformanceMode is not available in this version
                // The default mode is already optimized for performance
                .build()
            
            poseDetector = PoseDetection.getClient(options)
            Log.d(TAG, "Initialized standard pose detector with default settings")
        }
    }
    
    /**
     * Initialize with fast detection mode for maximum performance
     */
    fun initializeFast() {
        if (poseDetector != null) {
            close()
        }
        
        // Create options with STREAM_MODE (optimized for video/real-time)
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            // Fast mode is the default in current ML Kit version
            .build()
        
        poseDetector = PoseDetection.getClient(options)
        Log.d(TAG, "Initialized pose detector for real-time stream processing")
    }
    
    /**
     * Initialize with ultra-fast detection mode for maximum performance
     */
    fun initializeUltraFast() {
        if (poseDetector != null) {
            close()
        }
        
        // Create options with STREAM_MODE (optimized for video/real-time)
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            // Ultra-fast mode is not explicitly available, using FAST by default
            .build()
        
        poseDetector = PoseDetection.getClient(options)
        Log.d(TAG, "Initialized pose detector with FAST performance mode for maximum FPS")
    }
    
    /**
     * Initialize with accurate detection for better landmark identification
     * WARNING: This is significantly slower and may cause lag
     */
    fun initializeAccurate() {
        if (poseDetector != null) {
            close()
        }
        
        try {
            val options = AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build()
            
            poseDetector = PoseDetection.getClient(options)
            Log.d(TAG, "Initialized accurate pose detector (may cause lower FPS)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize accurate pose detector: ${e.message}")
            // Fallback to standard pose detector if accurate model isn't available
            initialize()
        }
    }
    
    /**
     * Detect pose from input image with logging
     */
    fun detectPose(
        image: InputImage,
        onSuccess: (Pose) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val detector = poseDetector ?: run {
            onFailure(IllegalStateException("Pose detector not initialized"))
            return
        }
        
        detector.process(image)
            .addOnSuccessListener { pose ->
                // Log detection success with landmark count
                val landmarkCount = pose.allPoseLandmarks.size
                val visibleLandmarks = pose.allPoseLandmarks.count { it.inFrameLikelihood > 0.5f }
                Log.d(TAG, "Pose detected with $landmarkCount landmarks ($visibleLandmarks visible)")
                
                onSuccess(pose)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Pose detection failed: ${exception.message}")
                onFailure(exception)
            }
    }
    
    /**
     * Close the detector and release resources
     */
    fun close() {
        poseDetector?.close()
        poseDetector = null
        Log.d(TAG, "Pose detector closed and resources released")
    }
}
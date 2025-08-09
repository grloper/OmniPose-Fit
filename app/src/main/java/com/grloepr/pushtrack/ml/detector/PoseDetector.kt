package com.grloepr.pushtrack.ml.detector

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector as MLKitPoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions

/**
 * Wrapper for ML Kit pose detector with proper lifecycle management
 */
class PoseDetector {
    private var detector: MLKitPoseDetector? = null
    private val lock = Any()
    
    /**
     * Initialize the pose detector with optimized settings
     */
    fun initialize() {
        synchronized(lock) {
            if (detector != null) return
            
            val options = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()
            
            detector = PoseDetection.getClient(options)
        }
    }
    
    /**
     * Initialize with accurate (but slower) detector
     * Use this for calibration or when performance is not critical
     */
    fun initializeAccurate() {
        synchronized(lock) {
            if (detector != null) {
                close()
            }
            
            val options = AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build()
            
            detector = PoseDetection.getClient(options)
        }
    }
    
    /**
     * Detect pose from input image
     * @param image Input image for processing
     * @param onSuccess Callback for successful detection
     * @param onFailure Callback for detection failure
     */
    fun detectPose(
        image: InputImage,
        onSuccess: (Pose) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        synchronized(lock) {
            val localDetector = detector ?: run {
                onFailure(IllegalStateException("Detector not initialized"))
                return
            }
            
            localDetector.process(image)
                .addOnSuccessListener { pose ->
                    onSuccess(pose)
                }
                .addOnFailureListener { exception ->
                    onFailure(exception)
                }
        }
    }
    
    /**
     * Close the detector and release resources
     */
    fun close() {
        synchronized(lock) {
            detector?.close()
            detector = null
        }
    }
    
    /**
     * Check if detector is initialized
     */
    fun isInitialized(): Boolean {
        synchronized(lock) {
            return detector != null
        }
    }
}

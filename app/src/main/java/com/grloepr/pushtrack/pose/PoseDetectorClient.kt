package com.grloepr.pushtrack.pose

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * Client for managing ML Kit Pose Detection lifecycle and operations
 */
class PoseDetectorClient {
    
    private var poseDetector: PoseDetector? = null
    
    /**
     * Initialize the pose detector with accurate model configuration for better precision
     */
    fun initialize() {
        if (poseDetector == null) {
            val options = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptions.CPU_GPU)
                .build()
            
            poseDetector = PoseDetection.getClient(options)
        }
    }
    
    /**
     * Process an image and detect poses
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
            onFailure(IllegalStateException("Pose detector not initialized"))
            return
        }
        
        detector.process(image)
            .addOnSuccessListener { pose ->
                onSuccess(pose)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }
    
    /**
     * Clean up resources when done
     */
    fun close() {
        poseDetector?.close()
        poseDetector = null
    }
}
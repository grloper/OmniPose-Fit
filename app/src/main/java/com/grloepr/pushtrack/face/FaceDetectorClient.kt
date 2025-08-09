package com.grloepr.pushtrack.face

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Ultra-Performance Client for managing ML Kit Face Detection
 * Used to improve pose detection accuracy and provide face-specific features
 */
class FaceDetectorClient {
    
    private var faceDetector: FaceDetector? = null
    
    /**
     * Initialize the face detector with ultra-performance optimizations
     */
    fun initialize() {
        if (faceDetector == null) {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // Prioritize speed
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // Get all facial landmarks
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE) // Skip contours for performance
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE) // Skip classification for performance
                .setMinFaceSize(0.1f) // Minimum face size to detect
                .enableTracking() // Enable face tracking for smooth overlay
                .build()
            
            faceDetector = FaceDetection.getClient(options)
        }
    }
    
    /**
     * Process an image and detect faces
     * @param image The input image to process
     * @param onSuccess Callback for successful face detection
     * @param onFailure Callback for detection failure
     */
    fun detectFaces(
        image: InputImage,
        onSuccess: (List<Face>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val detector = faceDetector ?: run {
            onFailure(IllegalStateException("Face detector not initialized"))
            return
        }
        
        detector.process(image)
            .addOnSuccessListener { faces ->
                onSuccess(faces)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }
    
    /**
     * Clean up resources when done
     */
    fun close() {
        faceDetector?.close()
        faceDetector = null
    }
}
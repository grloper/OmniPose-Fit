package com.grloepr.pushtrack.util

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Helper class for debugging pose detection issues
 */
object PoseDebugHelper {

    private const val TAG = "PoseDebugHelper"
    
    /**
     * Log all detected landmarks and their confidence values
     */
    fun logPoseLandmarks(pose: Pose) {
        val landmarkNames = mapOf(
            PoseLandmark.NOSE to "NOSE",
            PoseLandmark.LEFT_EYE to "LEFT_EYE",
            PoseLandmark.RIGHT_EYE to "RIGHT_EYE",
            PoseLandmark.LEFT_EAR to "LEFT_EAR", 
            PoseLandmark.RIGHT_EAR to "RIGHT_EAR",
            PoseLandmark.LEFT_SHOULDER to "LEFT_SHOULDER",
            PoseLandmark.RIGHT_SHOULDER to "RIGHT_SHOULDER",
            PoseLandmark.LEFT_ELBOW to "LEFT_ELBOW",
            PoseLandmark.RIGHT_ELBOW to "RIGHT_ELBOW",
            PoseLandmark.LEFT_WRIST to "LEFT_WRIST",
            PoseLandmark.RIGHT_WRIST to "RIGHT_WRIST",
            PoseLandmark.LEFT_HIP to "LEFT_HIP",
            PoseLandmark.RIGHT_HIP to "RIGHT_HIP",
            PoseLandmark.LEFT_KNEE to "LEFT_KNEE",
            PoseLandmark.RIGHT_KNEE to "RIGHT_KNEE",
            PoseLandmark.LEFT_ANKLE to "LEFT_ANKLE",
            PoseLandmark.RIGHT_ANKLE to "RIGHT_ANKLE"
        )
        
        android.util.Log.d(TAG, "==== POSE LANDMARKS ====")
        
        // Log detection information for all landmarks
        var detectedCount = 0
        var highConfidenceCount = 0
        
        landmarkNames.forEach { (id, name) ->
            val landmark = pose.getPoseLandmark(id)
            if (landmark != null) {
                detectedCount++
                val confidence = landmark.inFrameLikelihood
                if (confidence >= 0.5f) highConfidenceCount++
                
                android.util.Log.d(TAG, "$name: (x=${landmark.position.x.toInt()}, " +
                                   "y=${landmark.position.y.toInt()}) confidence=$confidence")
            } else {
                android.util.Log.d(TAG, "$name: Not detected")
            }
        }
        
        // Log summary
        android.util.Log.d(TAG, "Landmarks detected: $detectedCount/33")
        android.util.Log.d(TAG, "High confidence: $highConfidenceCount/33")
    }
    
    /**
     * Check if all landmarks required for an exercise type are detected with good confidence
     */
    fun checkRequiredLandmarks(pose: Pose, requiredLandmarks: List<Int>): Boolean {
        var allDetected = true
        
        requiredLandmarks.forEach { id ->
            val landmark = pose.getPoseLandmark(id)
            if (landmark == null || landmark.inFrameLikelihood < 0.5f) {
                allDetected = false
                android.util.Log.d(TAG, "Required landmark $id missing or low confidence")
            }
        }
        
        return allDetected
    }
    
    /**
     * Add to ExerciseCameraScreen to periodically log detection data
     */
    fun initializeDebugMode() {
        android.util.Log.d(TAG, "Debug mode initialized")
    }
}

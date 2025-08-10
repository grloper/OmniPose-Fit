package com.grloepr.pushtrack.domain

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.*

/**
 * ULTRA-OPTIMIZED utility class for calculating angles between pose landmarks
 * Specialized for ground-position selfie push-up detection with maximum performance
 */
object AngleUtils {
    
    // Optimized confidence threshold for ground-position detection
    private const val MIN_CONFIDENCE = 0.3f // Lower threshold for better detection in selfie mode
    
    /**
     * ULTRA-FAST elbow angle calculation optimized for ground-position selfie view
     * @param pose The detected pose
     * @param isLeftArm Whether to calculate for left arm (true) or right arm (false)
     * @return Elbow angle in degrees, or null if landmarks are not detected with sufficient confidence
     */
    fun calculateElbowAngle(pose: Pose, isLeftArm: Boolean): Float? {
        val wristType = if (isLeftArm) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
        val elbowType = if (isLeftArm) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
        val shoulderType = if (isLeftArm) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER
        
        val wrist = pose.getPoseLandmark(wristType)
        val elbow = pose.getPoseLandmark(elbowType)
        val shoulder = pose.getPoseLandmark(shoulderType)
        
        // Ultra-fast confidence check with optimized thresholds
        if (wrist?.inFrameLikelihood ?: 0f < MIN_CONFIDENCE ||
            elbow?.inFrameLikelihood ?: 0f < MIN_CONFIDENCE ||
            shoulder?.inFrameLikelihood ?: 0f < MIN_CONFIDENCE) {
            return null
        }
        
        return calculateAngle(
            shoulder!!.position.x, shoulder.position.y,
            elbow!!.position.x, elbow.position.y,
            wrist!!.position.x, wrist.position.y
        )
    }
    
    /**
     * Calculate angle between three points (generic version)
     */
    fun calculateAngle(
        p1x: Float, p1y: Float, 
        p2x: Float, p2y: Float, 
        p3x: Float, p3y: Float
    ): Float {
        // Vector from point2 to point1
        val v1x = p1x - p2x
        val v1y = p1y - p2y
        
        // Vector from point2 to point3
        val v2x = p3x - p2x
        val v2y = p3y - p2y
        
        // Calculate dot product
        val dotProduct = v1x * v2x + v1y * v2y
        
        // Calculate magnitudes
        val magnitude1 = sqrt(v1x * v1x + v1y * v1y)
        val magnitude2 = sqrt(v2x * v2x + v2y * v2y)
        
        // Check for zero vectors
        if (magnitude1 < 0.0001f || magnitude2 < 0.0001f) {
            return 0f
        }
        
        // Calculate cosine of angle
        val cosAngle = dotProduct / (magnitude1 * magnitude2)
        
        // Clamp and convert to degrees
        val clampedCos = cosAngle.coerceIn(-1f, 1f)
        return (acos(clampedCos) * 180f / PI.toFloat())
    }
    
    /**
     * SPECIALIZED function for ground-position push-up detection
     * Focuses on torso movement detection when elbow angles are not reliable
     */
    fun getTorsoMovementIndicator(pose: Pose): Float? {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        
        // Calculate a synthetic "angle" based on torso position for push-up detection
        return when {
            leftShoulder != null && rightShoulder != null && nose != null -> {
                // Use the vertical distance between nose and shoulder line as angle indicator
                val shoulderMidY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
                val noseY = nose.position.y
                val verticalDistance = abs(noseY - shoulderMidY)
                
                // Convert vertical distance to angle-like value (higher distance = lower "angle")
                val normalizedAngle = 180f - (verticalDistance * 2f) // Rough conversion
                normalizedAngle.coerceIn(30f, 180f)
            }
            else -> null
        }
    }
}
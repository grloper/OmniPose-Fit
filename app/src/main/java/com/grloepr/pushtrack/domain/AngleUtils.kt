package com.grloepr.pushtrack.domain

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.*

/**
 * Optimized utility class for calculating angles between pose landmarks
 * Focused on reliable elbow angle detection for push-ups
 */
object AngleUtils {
    
    // Minimum confidence threshold for reliable landmark detection
    private const val MIN_CONFIDENCE = 0.5f
    
    /**
     * Calculate elbow angle from wrist, elbow, and shoulder landmarks
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
        
        // Require minimum confidence for all landmarks
        if (wrist?.inFrameLikelihood ?: 0f < MIN_CONFIDENCE ||
            elbow?.inFrameLikelihood ?: 0f < MIN_CONFIDENCE ||
            shoulder?.inFrameLikelihood ?: 0f < MIN_CONFIDENCE) {
            return null
        }
        
        return calculateAngle(
            wrist!!.position.x, wrist.position.y,
            elbow!!.position.x, elbow.position.y,
            shoulder!!.position.x, shoulder.position.y
        )
    }
    
    /**
     * Calculate angle between three points
     * @param p1x X coordinate of first point (wrist)
     * @param p1y Y coordinate of first point (wrist)
     * @param p2x X coordinate of middle point (elbow - vertex of angle)
     * @param p2y Y coordinate of middle point (elbow - vertex of angle)
     * @param p3x X coordinate of third point (shoulder)
     * @param p3y Y coordinate of third point (shoulder)
     * @return Angle in degrees at the middle point
     */
    private fun calculateAngle(
        p1x: Float, p1y: Float,
        p2x: Float, p2y: Float,
        p3x: Float, p3y: Float
    ): Float {
        // Vector from elbow to wrist
        val v1x = p1x - p2x
        val v1y = p1y - p2y
        
        // Vector from elbow to shoulder
        val v2x = p3x - p2x
        val v2y = p3y - p2y
        
        // Calculate dot product
        val dotProduct = v1x * v2x + v1y * v2y
        
        // Calculate magnitudes
        val magnitude1 = sqrt(v1x * v1x + v1y * v1y)
        val magnitude2 = sqrt(v2x * v2x + v2y * v2y)
        
        // Avoid division by zero
        if (magnitude1 < 0.0001f || magnitude2 < 0.0001f) {
            return 0f
        }
        
        // Calculate cosine of angle
        val cosAngle = dotProduct / (magnitude1 * magnitude2)
        
        // Clamp cosine to valid range [-1, 1]
        val clampedCos = cosAngle.coerceIn(-1f, 1f)
        
        // Return angle in degrees
        return Math.toDegrees(acos(clampedCos).toDouble()).toFloat()
    }
    
    /**
     * Get the better arm angle (higher confidence) for push-up counting
     * @param pose The detected pose
     * @return Pair of (angle, isLeftArm) or null if no arm is detected with sufficient confidence
     */
    fun getBestElbowAngle(pose: Pose): Pair<Float, Boolean>? {
        val leftAngle = calculateElbowAngle(pose, isLeftArm = true)
        val rightAngle = calculateElbowAngle(pose, isLeftArm = false)
        
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        
        val leftConfidence = leftElbow?.inFrameLikelihood ?: 0f
        val rightConfidence = rightElbow?.inFrameLikelihood ?: 0f
        
        return when {
            leftAngle != null && rightAngle != null -> {
                // Both arms detected, choose the one with higher confidence
                if (leftConfidence >= rightConfidence) {
                    Pair(leftAngle, true)
                } else {
                    Pair(rightAngle, false)
                }
            }
            leftAngle != null -> Pair(leftAngle, true)
            rightAngle != null -> Pair(rightAngle, false)
            else -> null
        }
    }
}
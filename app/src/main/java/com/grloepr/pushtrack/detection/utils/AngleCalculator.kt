package com.grloepr.pushtrack.detection.utils

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.*

/**
 * O(1) utility class for calculating angles between pose landmarks
 * Optimized for real-time exercise detection with no growing state
 */
object AngleCalculator {
    
    // Confidence threshold for reliable landmark detection
    private const val MIN_CONFIDENCE = 0.5f
    
    /**
     * Calculate elbow angle (shoulder-elbow-wrist) for a specific arm
     * @param pose The detected pose
     * @param isLeftArm Whether to calculate for left arm (true) or right arm (false)
     * @return Elbow angle in degrees, or null if landmarks are not reliable
     */
    fun calculateElbowAngle(pose: Pose, isLeftArm: Boolean): Float? {
        val shoulderType = if (isLeftArm) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER
        val elbowType = if (isLeftArm) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
        val wristType = if (isLeftArm) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
        
        return calculateAngleFromLandmarks(
            pose.getPoseLandmark(shoulderType),
            pose.getPoseLandmark(elbowType),
            pose.getPoseLandmark(wristType)
        )
    }
    
    /**
     * Calculate average elbow angle from both arms
     * @param pose The detected pose
     * @return Average elbow angle, or null if neither arm is reliable
     */
    fun calculateAverageElbowAngle(pose: Pose): Float? {
        val leftAngle = calculateElbowAngle(pose, true)
        val rightAngle = calculateElbowAngle(pose, false)
        
        return when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2f
            leftAngle != null -> leftAngle
            rightAngle != null -> rightAngle
            else -> null
        }
    }
    
    /**
     * Calculate knee angle (hip-knee-ankle) for a specific leg
     * @param pose The detected pose
     * @param isLeftLeg Whether to calculate for left leg (true) or right leg (false)
     * @return Knee angle in degrees, or null if landmarks are not reliable
     */
    fun calculateKneeAngle(pose: Pose, isLeftLeg: Boolean): Float? {
        val hipType = if (isLeftLeg) PoseLandmark.LEFT_HIP else PoseLandmark.RIGHT_HIP
        val kneeType = if (isLeftLeg) PoseLandmark.LEFT_KNEE else PoseLandmark.RIGHT_KNEE
        val ankleType = if (isLeftLeg) PoseLandmark.LEFT_ANKLE else PoseLandmark.RIGHT_ANKLE
        
        return calculateAngleFromLandmarks(
            pose.getPoseLandmark(hipType),
            pose.getPoseLandmark(kneeType),
            pose.getPoseLandmark(ankleType)
        )
    }
    
    /**
     * Calculate average knee angle from both legs
     * @param pose The detected pose
     * @return Average knee angle, or null if neither leg is reliable
     */
    fun calculateAverageKneeAngle(pose: Pose): Float? {
        val leftAngle = calculateKneeAngle(pose, true)
        val rightAngle = calculateKneeAngle(pose, false)
        
        return when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2f
            leftAngle != null -> leftAngle
            rightAngle != null -> rightAngle
            else -> null
        }
    }
    
    /**
     * Calculate hip angle (shoulder-hip-knee) for torso bend detection
     * @param pose The detected pose
     * @param isLeftSide Whether to calculate for left side (true) or right side (false)
     * @return Hip angle in degrees, or null if landmarks are not reliable
     */
    fun calculateHipAngle(pose: Pose, isLeftSide: Boolean): Float? {
        val shoulderType = if (isLeftSide) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER
        val hipType = if (isLeftSide) PoseLandmark.LEFT_HIP else PoseLandmark.RIGHT_HIP
        val kneeType = if (isLeftSide) PoseLandmark.LEFT_KNEE else PoseLandmark.RIGHT_KNEE
        
        return calculateAngleFromLandmarks(
            pose.getPoseLandmark(shoulderType),
            pose.getPoseLandmark(hipType),
            pose.getPoseLandmark(kneeType)
        )
    }
    
    /**
     * Calculate average hip angle from both sides
     * @param pose The detected pose
     * @return Average hip angle, or null if neither side is reliable
     */
    fun calculateAverageHipAngle(pose: Pose): Float? {
        val leftAngle = calculateHipAngle(pose, true)
        val rightAngle = calculateHipAngle(pose, false)
        
        return when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2f
            leftAngle != null -> leftAngle
            rightAngle != null -> rightAngle
            else -> null
        }
    }
    
    /**
     * Calculate angle between three pose landmarks
     * @param p1 First landmark (forms one side of the angle)
     * @param p2 Middle landmark (vertex of the angle)
     * @param p3 Third landmark (forms other side of the angle)
     * @return Angle in degrees, or null if any landmark is unreliable
     */
    private fun calculateAngleFromLandmarks(
        p1: PoseLandmark?,
        p2: PoseLandmark?,
        p3: PoseLandmark?
    ): Float? {
        if (p1 == null || p2 == null || p3 == null) return null
        
        // Check confidence levels
        if (p1.inFrameLikelihood < MIN_CONFIDENCE ||
            p2.inFrameLikelihood < MIN_CONFIDENCE ||
            p3.inFrameLikelihood < MIN_CONFIDENCE) {
            return null
        }
        
        return calculateAngle(
            p1.position.x, p1.position.y,
            p2.position.x, p2.position.y,
            p3.position.x, p3.position.y
        )
    }
    
    /**
     * Calculate angle between three points in 2D space
     * @param p1x, p1y First point coordinates
     * @param p2x, p2y Middle point coordinates (vertex)
     * @param p3x, p3y Third point coordinates
     * @return Angle in degrees
     */
    fun calculateAngle(
        p1x: Float, p1y: Float,
        p2x: Float, p2y: Float,
        p3x: Float, p3y: Float
    ): Float {
        // Vector from p2 to p1
        val v1x = p1x - p2x
        val v1y = p1y - p2y
        
        // Vector from p2 to p3
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
        
        // Clamp and convert to degrees
        val clampedCos = cosAngle.coerceIn(-1f, 1f)
        return (acos(clampedCos) * 180f / PI.toFloat())
    }
}
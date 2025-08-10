package com.grloepr.pushtrack.counting

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.*

/**
 * Utility class for analyzing pose geometry and extracting push-up relevant metrics
 */
object PoseAnalyzer {
    
    /**
     * Calculate the angle at the elbow joint
     * @param pose The detected pose
     * @param isLeftArm Whether to calculate for left arm (true) or right arm (false)
     * @return Elbow angle in degrees, or null if landmarks not available
     */
    fun calculateElbowAngle(pose: Pose, isLeftArm: Boolean = true): Float? {
        val shoulderType = if (isLeftArm) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER
        val elbowType = if (isLeftArm) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
        val wristType = if (isLeftArm) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
        
        val shoulder = pose.getPoseLandmark(shoulderType) ?: return null
        val elbow = pose.getPoseLandmark(elbowType) ?: return null
        val wrist = pose.getPoseLandmark(wristType) ?: return null
        
        // Check confidence thresholds
        if (shoulder.inFrameLikelihood < 0.5f || 
            elbow.inFrameLikelihood < 0.5f || 
            wrist.inFrameLikelihood < 0.5f) {
            return null
        }
        
        return calculateAngle(
            shoulder.position.x, shoulder.position.y,
            elbow.position.x, elbow.position.y,
            wrist.position.x, wrist.position.y
        )
    }
    
    /**
     * Calculate both elbow angles and return the average
     */
    fun calculateAverageElbowAngle(pose: Pose): Float? {
        val leftAngle = calculateElbowAngle(pose, isLeftArm = true)
        val rightAngle = calculateElbowAngle(pose, isLeftArm = false)
        
        return when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2f
            leftAngle != null -> leftAngle
            rightAngle != null -> rightAngle
            else -> null
        }
    }
    
    /**
     * Calculate the angle between the torso and the ground
     * @param pose The detected pose
     * @return Torso angle in degrees relative to horizontal, or null if not available
     */
    fun calculateTorsoAngle(pose: Pose): Float? {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        
        if (leftShoulder?.inFrameLikelihood ?: 0f < 0.5f ||
            rightShoulder?.inFrameLikelihood ?: 0f < 0.5f ||
            leftHip?.inFrameLikelihood ?: 0f < 0.5f ||
            rightHip?.inFrameLikelihood ?: 0f < 0.5f) {
            return null
        }
        
        // Calculate midpoints
        val shoulderMidX = (leftShoulder!!.position.x + rightShoulder!!.position.x) / 2f
        val shoulderMidY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
        val hipMidX = (leftHip!!.position.x + rightHip!!.position.x) / 2f
        val hipMidY = (leftHip.position.y + rightHip.position.y) / 2f
        
        // Calculate angle relative to horizontal
        val deltaX = shoulderMidX - hipMidX
        val deltaY = shoulderMidY - hipMidY
        
        return atan2(deltaY, deltaX) * 180f / PI.toFloat()
    }
    
    /**
     * Check if the person is in plank position (body roughly horizontal)
     */
    fun isInPlankPosition(pose: Pose): Boolean {
        val torsoAngle = calculateTorsoAngle(pose) ?: return false
        // Torso should be roughly horizontal (within 30 degrees)
        return abs(torsoAngle) < 30f
    }
    
    /**
     * Check if arms are visible and trackable
     */
    fun areArmsVisible(pose: Pose): Boolean {
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        
        val leftArmVisible = (leftElbow?.inFrameLikelihood ?: 0f) > 0.5f && 
                            (leftWrist?.inFrameLikelihood ?: 0f) > 0.5f
        val rightArmVisible = (rightElbow?.inFrameLikelihood ?: 0f) > 0.5f && 
                             (rightWrist?.inFrameLikelihood ?: 0f) > 0.5f
        
        return leftArmVisible || rightArmVisible
    }
    
    /**
     * Calculate angle between three points
     */
    private fun calculateAngle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float {
        val vector1X = x1 - x2
        val vector1Y = y1 - y2
        val vector2X = x3 - x2
        val vector2Y = y3 - y2
        
        val dot = vector1X * vector2X + vector1Y * vector2Y
        val magnitude1 = sqrt(vector1X * vector1X + vector1Y * vector1Y)
        val magnitude2 = sqrt(vector2X * vector2X + vector2Y * vector2Y)
        
        if (magnitude1 == 0f || magnitude2 == 0f) return 0f
        
        val cosAngle = dot / (magnitude1 * magnitude2)
        val clampedCos = cosAngle.coerceIn(-1f, 1f)
        
        return acos(clampedCos) * 180f / PI.toFloat()
    }
}
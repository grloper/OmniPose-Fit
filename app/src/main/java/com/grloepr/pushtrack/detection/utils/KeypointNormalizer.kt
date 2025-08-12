package com.grloepr.pushtrack.detection.utils

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Utility for normalizing pose keypoints to be scale and position invariant
 * Essential for robust detection across different body types and camera distances
 */
object KeypointNormalizer {
    
    /**
     * Normalized pose data with scale-invariant coordinates
     */
    data class NormalizedPose(
        val landmarks: Map<Int, NormalizedLandmark>,
        val torsoLength: Float,
        val shoulderWidth: Float,
        val confidence: Float
    ) {
        fun getLandmark(landmarkType: Int): NormalizedLandmark? = landmarks[landmarkType]
    }
    
    /**
     * Normalized landmark with confidence
     */
    data class NormalizedLandmark(
        val x: Float,
        val y: Float,
        val confidence: Float
    )
    
    /**
     * Normalize pose to be scale and position invariant
     * Uses torso as reference for scaling and centering
     */
    fun normalizePose(pose: Pose): NormalizedPose? {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        
        // Require core landmarks for normalization
        if (leftShoulder == null || rightShoulder == null || 
            leftHip == null || rightHip == null) {
            return null
        }
        
        // Calculate torso center and dimensions
        val shoulderMidX = (leftShoulder.position.x + rightShoulder.position.x) / 2f
        val shoulderMidY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
        val hipMidX = (leftHip.position.x + rightHip.position.x) / 2f
        val hipMidY = (leftHip.position.y + rightHip.position.y) / 2f
        
        // Reference center point (torso center)
        val centerX = (shoulderMidX + hipMidX) / 2f
        val centerY = (shoulderMidY + hipMidY) / 2f
        
        // Scale references
        val torsoLength = distance(shoulderMidX, shoulderMidY, hipMidX, hipMidY)
        val shoulderWidth = distance(leftShoulder.position.x, leftShoulder.position.y,
                                   rightShoulder.position.x, rightShoulder.position.y)
        
        // Prevent division by zero
        if (torsoLength < 1f || shoulderWidth < 1f) {
            return null
        }
        
        // Normalize all landmarks
        val normalizedLandmarks = mutableMapOf<Int, NormalizedLandmark>()
        
        pose.allPoseLandmarks.forEach { landmark ->
            // Translate to center and scale by torso length
            val normalizedX = (landmark.position.x - centerX) / torsoLength
            val normalizedY = (landmark.position.y - centerY) / torsoLength
            
            normalizedLandmarks[landmark.landmarkType] = NormalizedLandmark(
                x = normalizedX,
                y = normalizedY,
                confidence = landmark.inFrameLikelihood
            )
        }
        
        // Calculate overall confidence
        val avgConfidence = pose.allPoseLandmarks
            .map { it.inFrameLikelihood }
            .average()
            .toFloat()
        
        return NormalizedPose(
            landmarks = normalizedLandmarks,
            torsoLength = torsoLength,
            shoulderWidth = shoulderWidth,
            confidence = avgConfidence
        )
    }
    
    /**
     * Calculate angle between three normalized points
     */
    fun calculateNormalizedAngle(
        point1: NormalizedLandmark,
        vertex: NormalizedLandmark,
        point3: NormalizedLandmark
    ): Float {
        val vector1X = point1.x - vertex.x
        val vector1Y = point1.y - vertex.y
        val vector2X = point3.x - vertex.x
        val vector2Y = point3.y - vertex.y
        
        val dot = vector1X * vector2X + vector1Y * vector2Y
        val mag1 = sqrt(vector1X * vector1X + vector1Y * vector1Y)
        val mag2 = sqrt(vector2X * vector2X + vector2Y * vector2Y)
        
        if (mag1 == 0f || mag2 == 0f) return 0f
        
        val cosAngle = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cosAngle.toDouble())).toFloat()
    }
    
    /**
     * Calculate normalized distance between two points
     */
    fun normalizedDistance(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Get vertical position relative to torso (positive = above center, negative = below)
     */
    fun getVerticalPosition(landmark: NormalizedLandmark): Float = -landmark.y // Negative because y increases downward
    
    /**
     * Get horizontal position relative to torso (positive = right, negative = left)
     */
    fun getHorizontalPosition(landmark: NormalizedLandmark): Float = landmark.x
    
    /**
     * Calculate Euclidean distance between two points
     */
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Check if normalized pose has sufficient landmark quality for detection
     */
    fun hasSufficientQuality(normalizedPose: NormalizedPose, requiredLandmarks: List<Int>): Boolean {
        if (normalizedPose.confidence < 0.5f) return false
        
        val availableHighConfidence = requiredLandmarks.count { landmarkType ->
            normalizedPose.getLandmark(landmarkType)?.confidence ?: 0f >= 0.6f
        }
        
        return availableHighConfidence >= (requiredLandmarks.size * 0.7f) // At least 70% of required landmarks
    }
}
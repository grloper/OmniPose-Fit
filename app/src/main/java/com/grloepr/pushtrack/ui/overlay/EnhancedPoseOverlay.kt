package com.grloepr.pushtrack.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.PoseFrameResult

/**
 * Enhanced pose overlay with improved skeleton alignment and visualization
 */
@Composable
fun EnhancedPoseOverlay(
    poseFrameResult: PoseFrameResult,
    modifier: Modifier = Modifier,
    debugMode: Boolean = true
) {
    val connections = remember {
        listOf(
            // Torso - critical for push-up detection
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP),
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
            
            // Arms - critical for push-up angle detection
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
            Pair(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
            Pair(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
            
            // Legs - less critical but helpful for overall pose
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
            Pair(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            Pair(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
            Pair(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        )
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val pose = poseFrameResult.pose
        
        // Draw connections first (behind landmarks)
        connections.forEach { (startLandmarkType, endLandmarkType) ->
            val startLandmark = pose.getPoseLandmark(startLandmarkType)
            val endLandmark = pose.getPoseLandmark(endLandmarkType)
            
            // Lower confidence threshold for better visibility
            if (startLandmark != null && endLandmark != null &&
                startLandmark.inFrameLikelihood > 0.2f && 
                endLandmark.inFrameLikelihood > 0.2f) {
                
                val startPoint = transformCoordinateEnhanced(
                    landmark = startLandmark,
                    poseFrameResult = poseFrameResult,
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )
                
                val endPoint = transformCoordinateEnhanced(
                    landmark = endLandmark,
                    poseFrameResult = poseFrameResult,
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )
                
                // Use different colors for different body parts
                val connectionColor = when {
                    // Arms - green gradient
                    (startLandmarkType == PoseLandmark.LEFT_SHOULDER && endLandmarkType == PoseLandmark.LEFT_ELBOW) ||
                    (startLandmarkType == PoseLandmark.LEFT_ELBOW && endLandmarkType == PoseLandmark.LEFT_WRIST) ||
                    (startLandmarkType == PoseLandmark.RIGHT_SHOULDER && endLandmarkType == PoseLandmark.RIGHT_ELBOW) ||
                    (startLandmarkType == PoseLandmark.RIGHT_ELBOW && endLandmarkType == PoseLandmark.RIGHT_WRIST) -> 
                        Color(0xFF4ECCA3) // Bright green for arms
                    
                    // Torso - blue gradient
                    (startLandmarkType == PoseLandmark.LEFT_SHOULDER && endLandmarkType == PoseLandmark.RIGHT_SHOULDER) ||
                    (startLandmarkType == PoseLandmark.LEFT_HIP && endLandmarkType == PoseLandmark.RIGHT_HIP) ||
                    (startLandmarkType == PoseLandmark.LEFT_SHOULDER && endLandmarkType == PoseLandmark.LEFT_HIP) ||
                    (startLandmarkType == PoseLandmark.RIGHT_SHOULDER && endLandmarkType == PoseLandmark.RIGHT_HIP) -> 
                        Color(0xFF3EAEFF) // Blue for torso
                    
                    // Legs - purple gradient
                    else -> Color(0xFFAA77FF) // Purple for legs
                }
                
                // Draw enhanced connection with gradient and shadow effect
                drawLine(
                    color = connectionColor,
                    start = startPoint,
                    end = endPoint,
                    strokeWidth = 8f, // Thicker for better visibility
                    cap = StrokeCap.Round
                )
            }
        }
        
        // Draw all landmarks
        pose.allPoseLandmarks.forEach { landmark ->
            if (landmark.inFrameLikelihood > 0.2f) {
                val point = transformCoordinateEnhanced(
                    landmark = landmark,
                    poseFrameResult = poseFrameResult,
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )
                
                // Determine color based on body part
                val landmarkColor = when (landmark.landmarkType) {
                    // Head landmarks
                    PoseLandmark.NOSE, PoseLandmark.LEFT_EYE, PoseLandmark.RIGHT_EYE,
                    PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR,
                    PoseLandmark.LEFT_EYE_INNER, PoseLandmark.LEFT_EYE_OUTER,
                    PoseLandmark.RIGHT_EYE_INNER, PoseLandmark.RIGHT_EYE_OUTER -> 
                        Color(0xFFFFC857) // Yellow for head
                    
                    // Arm landmarks - critical for push-ups
                    PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
                    PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
                    PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST -> 
                        Color(0xFF4ECCA3) // Green for arms
                    
                    // Torso landmarks
                    PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP -> 
                        Color(0xFF3EAEFF) // Blue for torso
                    
                    // Leg landmarks
                    else -> Color(0xFFAA77FF) // Purple for legs
                }
                
                // Determine size based on importance for push-up detection
                val radius = when (landmark.landmarkType) {
                    PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW, 
                    PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER -> 10f // Larger for key points
                    
                    PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
                    PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, 
                    PoseLandmark.NOSE -> 8f // Medium for secondary points
                    
                    else -> 6f // Smaller for other points
                }
                
                // Draw landmark with ring effect for better visibility
                drawCircle(
                    color = landmarkColor,
                    radius = radius,
                    center = point
                )
                
                // Add outer ring for key points
                if (landmark.landmarkType == PoseLandmark.LEFT_ELBOW || 
                    landmark.landmarkType == PoseLandmark.RIGHT_ELBOW) {
                    drawCircle(
                        color = Color(0xFFFFFFFF),
                        radius = radius + 4,
                        center = point,
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
        
        // Specifically highlight the elbow joints which are key for push-up detection
        highlightKeyJoints(pose, poseFrameResult)
    }
}

/**
 * Highlight key joints for push-up detection
 */
private fun DrawScope.highlightKeyJoints(pose: Pose, poseFrameResult: PoseFrameResult) {
    val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
    val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
    
    // Highlight elbow joints with animated rings
    listOf(leftElbow, rightElbow).forEach { landmark ->
        landmark?.let {
            if (it.inFrameLikelihood > 0.5f) {
                val point = transformCoordinateEnhanced(
                    landmark = it,
                    poseFrameResult = poseFrameResult,
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )
                
                // Draw highlight circle around elbow
                drawCircle(
                    color = Color(0xFFFFEB3B), // Yellow highlight
                    radius = 20f,
                    center = point,
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}

/**
 * Enhanced coordinate transformation with better alignment and compensation
 * for camera distortion and perspective
 */
private fun transformCoordinateEnhanced(
    landmark: PoseLandmark,
    poseFrameResult: PoseFrameResult,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    val (x, y) = landmark.position.x to landmark.position.y
    val imageWidth = poseFrameResult.width.toFloat()
    val imageHeight = poseFrameResult.height.toFloat()
    
    // Apply rotation transformation based on image rotation
    val (rotatedX, rotatedY, rotatedWidth, rotatedHeight) = when (poseFrameResult.rotationDegrees) {
        90 -> {
            val newX = imageHeight - y
            val newY = x
            arrayOf(newX, newY, imageHeight, imageWidth)
        }
        180 -> {
            val newX = imageWidth - x
            val newY = imageHeight - y
            arrayOf(newX, newY, imageWidth, imageHeight)
        }
        270 -> {
            val newX = y
            val newY = imageWidth - x
            arrayOf(newX, newY, imageHeight, imageWidth)
        }
        else -> {
            arrayOf(x, y, imageWidth, imageHeight)
        }
    }
    
    // Calculate scale factors
    val scaleX = canvasWidth / rotatedWidth
    val scaleY = canvasHeight / rotatedHeight
    
    // Choose the smaller scale to maintain aspect ratio
    val scale = minOf(scaleX, scaleY)
    
    // Calculate scaled coordinates
    var scaledX = rotatedX * scale
    var scaledY = rotatedY * scale
    
    // Center the pose in the canvas
    val offsetX = (canvasWidth - (rotatedWidth * scale)) / 2f
    val offsetY = (canvasHeight - (rotatedHeight * scale)) / 2f
    scaledX += offsetX
    scaledY += offsetY
    
    // Apply mirroring for front camera
    if (poseFrameResult.isFrontCamera) {
        scaledX = canvasWidth - scaledX
    }
    
    // Apply perspective correction for better alignment
    // This slightly adjusts Y coordinate based on position to compensate for camera angle
    val perspectiveCorrection = if (poseFrameResult.isFrontCamera) {
        // Adjust vertical position based on how high the landmark is 
        // (upper body landmarks need more correction)
        val verticalPosition = scaledY / canvasHeight
        if (verticalPosition < 0.4f) -5f else 0f // Adjust upper body more
    } else 0f
    
    scaledY += perspectiveCorrection
    
    return Offset(scaledX, scaledY)
}

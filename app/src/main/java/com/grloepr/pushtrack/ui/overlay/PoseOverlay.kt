package com.grloepr.pushtrack.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.PoseFrameResult

/**
 * Optimized pose overlay with simplified coordinate transformation
 */
@Composable
fun PoseOverlay(
    poseFrameResult: PoseFrameResult?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        poseFrameResult?.let { frameResult ->
            // Transform coordinates for proper overlay alignment
            val transformedPoints = transformCoordinates(
                frameResult = frameResult,
                canvasWidth = size.width,
                canvasHeight = size.height
            )
            
            // Draw pose landmarks with emphasis on elbows for push-up tracking
            drawPoseLandmarks(frameResult.pose, transformedPoints)
            drawPoseConnections(frameResult.pose, transformedPoints)
            
            // Highlight elbow joints for better tracking visibility
            drawElbowHighlights(frameResult.pose, transformedPoints)
        }
    }
}

/**
 * Simple and efficient coordinate transformation
 */
private fun transformCoordinates(
    frameResult: PoseFrameResult,
    canvasWidth: Float,
    canvasHeight: Float
): Map<PoseLandmark, Offset> {
    val transformedPoints = mutableMapOf<PoseLandmark, Offset>()
    
    frameResult.pose.allPoseLandmarks.forEach { landmark ->
        val originalX = landmark.position.x
        val originalY = landmark.position.y
        
        // Apply rotation based on device orientation
        val (rotatedX, rotatedY, imageWidth, imageHeight) = when (frameResult.rotationDegrees) {
            90 -> {
                val newX = frameResult.imageHeight - originalY
                val newY = originalX
                TransformResult(newX, newY, frameResult.imageHeight.toFloat(), frameResult.imageWidth.toFloat())
            }
            180 -> {
                val newX = frameResult.imageWidth - originalX
                val newY = frameResult.imageHeight - originalY
                TransformResult(newX, newY, frameResult.imageWidth.toFloat(), frameResult.imageHeight.toFloat())
            }
            270 -> {
                val newX = originalY
                val newY = frameResult.imageWidth - originalX
                TransformResult(newX, newY, frameResult.imageHeight.toFloat(), frameResult.imageWidth.toFloat())
            }
            else -> {
                TransformResult(originalX, originalY, frameResult.imageWidth.toFloat(), frameResult.imageHeight.toFloat())
            }
        }
        
        // Scale to canvas size with proper aspect ratio
        val scaleX = canvasWidth / imageWidth
        val scaleY = canvasHeight / imageHeight
        val scale = minOf(scaleX, scaleY)
        
        // Apply scaling while maintaining aspect ratio
        var scaledX = rotatedX * scale
        var scaledY = rotatedY * scale
        
        // Center the image in the canvas
        val offsetX = (canvasWidth - imageWidth * scale) / 2f
        val offsetY = (canvasHeight - imageHeight * scale) / 2f
        
        scaledX += offsetX
        scaledY += offsetY
        
        // Apply mirroring for front camera
        if (frameResult.isFrontCamera) {
            scaledX = canvasWidth - scaledX
        }
        
        transformedPoints[landmark] = Offset(scaledX, scaledY)
    }
    
    return transformedPoints
}

/**
 * Draw pose landmarks with emphasis on tracking points
 */
private fun DrawScope.drawPoseLandmarks(
    pose: Pose, 
    transformedPoints: Map<PoseLandmark, Offset>
) {
    // Upper body landmarks (focus on push-up relevant points)
    val upperBodyLandmarks = listOf(
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST
    )
    
    // Other body landmarks
    val otherLandmarks = listOf(
        PoseLandmark.NOSE, PoseLandmark.LEFT_EYE, PoseLandmark.RIGHT_EYE,
        PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR,
        PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
    )
    
    // Draw upper body landmarks with emphasis
    upperBodyLandmarks.forEach { landmarkType ->
        val landmark = pose.getPoseLandmark(landmarkType)
        landmark?.let {
            if (it.inFrameLikelihood > 0.5f) {
                transformedPoints[it]?.let { point ->
                    drawCircle(
                        color = Color.Green,
                        radius = 8f,
                        center = point
                    )
                }
            }
        }
    }
    
    // Draw other landmarks with normal styling
    otherLandmarks.forEach { landmarkType ->
        val landmark = pose.getPoseLandmark(landmarkType)
        landmark?.let {
            if (it.inFrameLikelihood > 0.5f) {
                transformedPoints[it]?.let { point ->
                    drawCircle(
                        color = Color.Yellow,
                        radius = 6f,
                        center = point
                    )
                }
            }
        }
    }
}

/**
 * Draw connections between pose landmarks
 */
private fun DrawScope.drawPoseConnections(
    pose: Pose, 
    transformedPoints: Map<PoseLandmark, Offset>
) {
    // Define key connections
    val connections = listOf(
        // Arms (most important for push-ups)
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
        Pair(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
        Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
        Pair(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
        
        // Shoulders
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
        
        // Torso
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
        Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP),
        Pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
        
        // Legs
        Pair(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
        Pair(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
        Pair(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
        Pair(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
    )
    
    connections.forEach { (startType, endType) ->
        val startLandmark = pose.getPoseLandmark(startType)
        val endLandmark = pose.getPoseLandmark(endType)
        
        if (startLandmark != null && endLandmark != null &&
            startLandmark.inFrameLikelihood > 0.5f && endLandmark.inFrameLikelihood > 0.5f) {
            
            val startPoint = transformedPoints[startLandmark]
            val endPoint = transformedPoints[endLandmark]
            
            if (startPoint != null && endPoint != null) {
                // Draw arm connections with thicker lines for visibility
                val isArm = (startType == PoseLandmark.LEFT_SHOULDER && endType == PoseLandmark.LEFT_ELBOW) ||
                            (startType == PoseLandmark.LEFT_ELBOW && endType == PoseLandmark.LEFT_WRIST) ||
                            (startType == PoseLandmark.RIGHT_SHOULDER && endType == PoseLandmark.RIGHT_ELBOW) ||
                            (startType == PoseLandmark.RIGHT_ELBOW && endType == PoseLandmark.RIGHT_WRIST)
                
                drawLine(
                    color = if (isArm) Color(0xFF00FF00) else Color(0xFF4080FF), // Green for arms, blue for others
                    start = startPoint,
                    end = endPoint,
                    strokeWidth = if (isArm) 5f else 3f
                )
            }
        }
    }
}

/**
 * Draw highlights around elbow joints to emphasize the tracking points
 */
private fun DrawScope.drawElbowHighlights(
    pose: Pose,
    transformedPoints: Map<PoseLandmark, Offset>
) {
    val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
    val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
    
    // Draw highlight circle around left elbow
    if (leftElbow != null && leftElbow.inFrameLikelihood > 0.5f) {
        transformedPoints[leftElbow]?.let { point ->
            drawCircle(
                color = Color(0x40FF0000),  // Semi-transparent red
                radius = 25f,
                center = point,
                style = Stroke(width = 3f)
            )
        }
    }
    
    // Draw highlight circle around right elbow
    if (rightElbow != null && rightElbow.inFrameLikelihood > 0.5f) {
        transformedPoints[rightElbow]?.let { point ->
            drawCircle(
                color = Color(0x40FF0000),  // Semi-transparent red
                radius = 25f,
                center = point,
                style = Stroke(width = 3f)
            )
        }
    }
}
package com.grloepr.pushtrack.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.PoseFrameResult

/**
 * Improved visible PoseOverlay with enhanced visibility and debugging
 */
@Composable
fun PoseOverlay(
    poseFrameResult: PoseFrameResult,
    modifier: Modifier = Modifier,
    debugMode: Boolean = true // Enable debug mode by default for better visibility
) {
    // Pre-compute essential landmark connections for push-ups
    val connections = remember {
        listOf(
            // Torso
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP),
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
            
            // Left arm
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
            Pair(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            
            // Right arm
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
            Pair(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
            
            // Legs (optional for full body visibility)
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
            Pair(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            Pair(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
            Pair(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
        )
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val pose = poseFrameResult.pose
        
        // DRAW ALL LANDMARKS FIRST (makes them more visible)
        pose.allPoseLandmarks.forEach { landmark ->
            if (landmark.inFrameLikelihood > 0.2f) { // Lower threshold for better visibility
                val point = transformCoordinate(
                    landmark = landmark,
                    poseFrameResult = poseFrameResult,
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )
                
                // Draw landmark as larger circle with brighter color for visibility
                drawCircle(
                    color = Color(0xFFFF5252), // Bright red
                    radius = 10f, // Larger radius
                    center = point
                )
                
                // Draw landmark ID in debug mode
                if (debugMode) {
                    drawCircle(
                        color = Color.White,
                        radius = 5f,
                        center = point
                    )
                }
            }
        }
        
        // Now draw connections between landmarks
        connections.forEach { (startLandmarkType, endLandmarkType) ->
            val startLandmark = pose.getPoseLandmark(startLandmarkType)
            val endLandmark = pose.getPoseLandmark(endLandmarkType)
            
            // Lower confidence threshold to see more landmarks
            if (startLandmark != null && endLandmark != null &&
                startLandmark.inFrameLikelihood > 0.2f && 
                endLandmark.inFrameLikelihood > 0.2f) {
                
                val startPoint = transformCoordinate(
                    landmark = startLandmark,
                    poseFrameResult = poseFrameResult,
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )
                
                val endPoint = transformCoordinate(
                    landmark = endLandmark,
                    poseFrameResult = poseFrameResult,
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )
                
                // Draw much thicker connection line with brighter color
                drawLine(
                    color = Color(0xFF4CAF50), // Bright green
                    start = startPoint,
                    end = endPoint,
                    strokeWidth = 8f, // Much thicker line
                    cap = StrokeCap.Round
                )
            }
        }
        
        // Special highlight for the important elbow angles
        highlightElbowAngles(pose, poseFrameResult)
    }
}

/**
 * Highlight the elbow angles specifically for push-up tracking
 */
private fun DrawScope.highlightElbowAngles(pose: Pose, poseFrameResult: PoseFrameResult) {
    val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
    val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
    val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
    
    val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
    val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
    val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
    
    // Draw left elbow angle
    if (leftShoulder != null && leftElbow != null && leftWrist != null &&
        leftShoulder.inFrameLikelihood > 0.2f && 
        leftElbow.inFrameLikelihood > 0.2f &&
        leftWrist.inFrameLikelihood > 0.2f) {
        
        val shoulderPoint = transformCoordinate(leftShoulder, poseFrameResult, size.width, size.height)
        val elbowPoint = transformCoordinate(leftElbow, poseFrameResult, size.width, size.height)
        val wristPoint = transformCoordinate(leftWrist, poseFrameResult, size.width, size.height)
        
        // Draw large highlight circles at elbow joints
        drawCircle(
            color = Color(0xFFFFEB3B), // Yellow
            radius = 20f,
            center = elbowPoint,
            style = Stroke(width = 5f)
        )
        
        // Draw thicker lines for arm segments
        drawLine(
            color = Color(0xFFFFEB3B), // Yellow
            start = shoulderPoint,
            end = elbowPoint,
            strokeWidth = 12f,
            cap = StrokeCap.Round
        )
        
        drawLine(
            color = Color(0xFFFFEB3B), // Yellow
            start = elbowPoint,
            end = wristPoint,
            strokeWidth = 12f,
            cap = StrokeCap.Round
        )
    }
    
    // Draw right elbow angle
    if (rightShoulder != null && rightElbow != null && rightWrist != null &&
        rightShoulder.inFrameLikelihood > 0.2f && 
        rightElbow.inFrameLikelihood > 0.2f &&
        rightWrist.inFrameLikelihood > 0.2f) {
        
        val shoulderPoint = transformCoordinate(rightShoulder, poseFrameResult, size.width, size.height)
        val elbowPoint = transformCoordinate(rightElbow, poseFrameResult, size.width, size.height)
        val wristPoint = transformCoordinate(rightWrist, poseFrameResult, size.width, size.height)
        
        // Draw large highlight circles at elbow joints
        drawCircle(
            color = Color(0xFFFFEB3B), // Yellow
            radius = 20f,
            center = elbowPoint,
            style = Stroke(width = 5f)
        )
        
        // Draw thicker lines for arm segments
        drawLine(
            color = Color(0xFFFFEB3B), // Yellow
            start = shoulderPoint,
            end = elbowPoint,
            strokeWidth = 12f,
            cap = StrokeCap.Round
        )
        
        drawLine(
            color = Color(0xFFFFEB3B), // Yellow
            start = elbowPoint,
            end = wristPoint,
            strokeWidth = 12f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * High-performance coordinate transformation
 * This efficiently transforms ML Kit coordinates to canvas coordinates
 */
private fun transformCoordinate(
    landmark: PoseLandmark,
    poseFrameResult: PoseFrameResult,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    val x = landmark.position.x
    val y = landmark.position.y
    
    // Apply rotation transformation based on image rotation
    val (rotatedX, rotatedY, rotatedWidth, rotatedHeight) = when (poseFrameResult.rotationDegrees) {
        90 -> {
            val newX = poseFrameResult.imageHeight - y
            val newY = x
            arrayOf(newX, newY, poseFrameResult.imageHeight.toFloat(), poseFrameResult.imageWidth.toFloat())
        }
        180 -> {
            val newX = poseFrameResult.imageWidth - x
            val newY = poseFrameResult.imageHeight - y
            arrayOf(newX, newY, poseFrameResult.imageWidth.toFloat(), poseFrameResult.imageHeight.toFloat())
        }
        270 -> {
            val newX = y
            val newY = poseFrameResult.imageWidth - x
            arrayOf(newX, newY, poseFrameResult.imageHeight.toFloat(), poseFrameResult.imageWidth.toFloat())
        }
        else -> {
            arrayOf(x, y, poseFrameResult.imageWidth.toFloat(), poseFrameResult.imageHeight.toFloat())
        }
    }
    
    // Scale to canvas size preserving aspect ratio
    val scaleX = canvasWidth / rotatedWidth
    val scaleY = canvasHeight / rotatedHeight
    val scale = minOf(scaleX, scaleY)
    
    var scaledX = rotatedX * scale
    var scaledY = rotatedY * scale
    
    // Center in canvas
    val offsetX = (canvasWidth - (rotatedWidth * scale)) / 2f
    val offsetY = (canvasHeight - (rotatedHeight * scale)) / 2f
    
    scaledX += offsetX
    scaledY += offsetY
    
    // Mirror for front camera
    if (poseFrameResult.isFrontCamera) {
        scaledX = canvasWidth - scaledX
    }
    
    return Offset(scaledX, scaledY)
}
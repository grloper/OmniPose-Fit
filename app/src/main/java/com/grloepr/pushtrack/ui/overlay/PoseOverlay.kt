package com.grloepr.pushtrack.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.PoseFrameResult

/**
 * Composable that overlays pose detection landmarks on the camera preview
 * Transforms coordinates to properly align with camera preview across rotations and camera orientations
 */
@Composable
fun PoseOverlay(
    poseFrameResult: PoseFrameResult?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        poseFrameResult?.let { frameResult ->
            val transformedPoints = transformCoordinates(
                frameResult = frameResult,
                canvasWidth = size.width,
                canvasHeight = size.height
            )
            
            drawPoseLandmarks(frameResult.pose, transformedPoints)
            drawPoseConnections(frameResult.pose, transformedPoints)
        }
    }
}

/**
 * Legacy composable for backward compatibility
 */
@Composable
fun PoseOverlay(
    pose: Pose?,
    imageWidth: Int = 640,  // Default ML Kit image dimensions
    imageHeight: Int = 480,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        pose?.let { detectedPose ->
            val scaleX = size.width / imageWidth
            val scaleY = size.height / imageHeight
            
            drawPoseLandmarks(detectedPose, scaleX, scaleY)
            drawPoseConnections(detectedPose, scaleX, scaleY)
        }
    }
}

/**
 * Ultra-Precise coordinate transformation from image space to view space
 * Fixes skeleton alignment to perfectly match body position across all orientations
 */
private fun transformCoordinates(
    frameResult: PoseFrameResult,
    canvasWidth: Float,
    canvasHeight: Float
): Map<PoseLandmark, Offset> {
    val transformedPoints = mutableMapOf<PoseLandmark, Offset>()
    
    frameResult.pose.allPoseLandmarks.forEach { landmark ->
        val transformedPoint = transformLandmarkCoordinate(
            landmark = landmark,
            frameResult = frameResult,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )
        transformedPoints[landmark] = transformedPoint
    }
    
    return transformedPoints
}

/**
 * Ultra-Precise transformation for individual landmark coordinates
 * Handles all rotation cases and front camera mirroring with perfect accuracy
 */
private fun transformLandmarkCoordinate(
    landmark: PoseLandmark,
    frameResult: PoseFrameResult,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    val originalX = landmark.position.x
    val originalY = landmark.position.y
    
    // Step 1: Apply rotation transformation to map from image orientation to view orientation
    val (rotatedX, rotatedY, effectiveWidth, effectiveHeight) = when (frameResult.rotationDegrees) {
        90 -> {
            // 90° clockwise: X becomes Y, Y becomes (width - X)
            val newX = frameResult.imageHeight - originalY
            val newY = originalX
            Quadruple(newX, newY, frameResult.imageHeight.toFloat(), frameResult.imageWidth.toFloat())
        }
        180 -> {
            // 180°: Both X and Y are inverted
            val newX = frameResult.imageWidth - originalX
            val newY = frameResult.imageHeight - originalY
            Quadruple(newX, newY, frameResult.imageWidth.toFloat(), frameResult.imageHeight.toFloat())
        }
        270 -> {
            // 270° clockwise (or 90° counter-clockwise): X becomes (height - Y), Y becomes X
            val newX = originalY
            val newY = frameResult.imageWidth - originalX
            Quadruple(newX, newY, frameResult.imageHeight.toFloat(), frameResult.imageWidth.toFloat())
        }
        else -> {
            // 0° (no rotation)
            Quadruple(originalX, originalY, frameResult.imageWidth.toFloat(), frameResult.imageHeight.toFloat())
        }
    }
    
    // Step 2: Scale to canvas size with proper aspect ratio preservation
    val scaleX = canvasWidth / effectiveWidth
    val scaleY = canvasHeight / effectiveHeight
    
    // Use uniform scaling to maintain aspect ratio (prevents distortion)
    val uniformScale = minOf(scaleX, scaleY)
    
    var scaledX = rotatedX * uniformScale
    var scaledY = rotatedY * uniformScale
    
    // Step 3: Center the image in canvas if aspect ratios don't match
    val imageCanvasWidth = effectiveWidth * uniformScale
    val imageCanvasHeight = effectiveHeight * uniformScale
    
    val offsetX = (canvasWidth - imageCanvasWidth) / 2f
    val offsetY = (canvasHeight - imageCanvasHeight) / 2f
    
    scaledX += offsetX
    scaledY += offsetY
    
    // Step 4: Apply horizontal mirroring for front camera (selfie view)
    if (frameResult.isFrontCamera) {
        scaledX = canvasWidth - scaledX
    }
    
    return Offset(scaledX, scaledY)
}

/**
 * Data class to hold four values for coordinate transformation
 */
private data class Quadruple<T>(val first: T, val second: T, val third: T, val fourth: T)

/**
 * Draw individual pose landmarks as circles with coordinate transformation
 */
private fun DrawScope.drawPoseLandmarks(
    pose: Pose, 
    transformedPoints: Map<PoseLandmark, Offset>
) {
    // Head landmarks
    val headLandmarks = listOf(
        PoseLandmark.NOSE,
        PoseLandmark.LEFT_EYE_INNER,
        PoseLandmark.LEFT_EYE,
        PoseLandmark.LEFT_EYE_OUTER,
        PoseLandmark.RIGHT_EYE_INNER,
        PoseLandmark.RIGHT_EYE,
        PoseLandmark.RIGHT_EYE_OUTER,
        PoseLandmark.LEFT_EAR,
        PoseLandmark.RIGHT_EAR
    )
    
    // Upper body landmarks
    val upperBodyLandmarks = listOf(
        PoseLandmark.LEFT_SHOULDER,
        PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW,
        PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_HIP
    )
    
    // Lower body landmarks
    val lowerBodyLandmarks = listOf(
        PoseLandmark.LEFT_KNEE,
        PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_ANKLE,
        PoseLandmark.LEFT_HEEL,
        PoseLandmark.RIGHT_HEEL,
        PoseLandmark.LEFT_FOOT_INDEX,
        PoseLandmark.RIGHT_FOOT_INDEX
    )
    
    // Draw head landmarks in yellow with higher confidence threshold
    headLandmarks.forEach { landmarkType ->
        val landmark = pose.getPoseLandmark(landmarkType)
        landmark?.let {
            if (it.inFrameLikelihood > 0.6f) {
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
    
    // Draw upper body landmarks in green
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
    
    // Draw lower body landmarks in cyan with more permissive confidence
    lowerBodyLandmarks.forEach { landmarkType ->
        val landmark = pose.getPoseLandmark(landmarkType)
        landmark?.let {
            if (it.inFrameLikelihood > 0.3f) { // Lowered threshold for better leg detection
                transformedPoints[it]?.let { point ->
                    drawCircle(
                        color = Color.Cyan,
                        radius = 7f,
                        center = point
                    )
                }
            }
        }
    }
}

/**
 * Legacy method for backward compatibility
 */
private fun DrawScope.drawPoseLandmarks(pose: Pose, scaleX: Float, scaleY: Float) {
    // Head landmarks
    val headLandmarks = listOf(
        PoseLandmark.NOSE,
        PoseLandmark.LEFT_EYE_INNER,
        PoseLandmark.LEFT_EYE,
        PoseLandmark.LEFT_EYE_OUTER,
        PoseLandmark.RIGHT_EYE_INNER,
        PoseLandmark.RIGHT_EYE,
        PoseLandmark.RIGHT_EYE_OUTER,
        PoseLandmark.LEFT_EAR,
        PoseLandmark.RIGHT_EAR
    )
    
    // Upper body landmarks
    val upperBodyLandmarks = listOf(
        PoseLandmark.LEFT_SHOULDER,
        PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW,
        PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_HIP
    )
    
    // Lower body landmarks
    val lowerBodyLandmarks = listOf(
        PoseLandmark.LEFT_KNEE,
        PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_ANKLE,
        PoseLandmark.LEFT_HEEL,
        PoseLandmark.RIGHT_HEEL,
        PoseLandmark.LEFT_FOOT_INDEX,
        PoseLandmark.RIGHT_FOOT_INDEX
    )
    
    // Draw head landmarks in yellow with higher confidence threshold
    headLandmarks.forEach { landmarkType ->
        val landmark = pose.getPoseLandmark(landmarkType)
        landmark?.let {
            if (it.inFrameLikelihood > 0.6f) {
                drawCircle(
                    color = Color.Yellow,
                    radius = 6f,
                    center = Offset(
                        it.position.x * scaleX,
                        it.position.y * scaleY
                    )
                )
            }
        }
    }
    
    // Draw upper body landmarks in green
    upperBodyLandmarks.forEach { landmarkType ->
        val landmark = pose.getPoseLandmark(landmarkType)
        landmark?.let {
            if (it.inFrameLikelihood > 0.5f) {
                drawCircle(
                    color = Color.Green,
                    radius = 8f,
                    center = Offset(
                        it.position.x * scaleX,
                        it.position.y * scaleY
                    )
                )
            }
        }
    }
    
    // Draw lower body landmarks in cyan with more permissive confidence
    lowerBodyLandmarks.forEach { landmarkType ->
        val landmark = pose.getPoseLandmark(landmarkType)
        landmark?.let {
            if (it.inFrameLikelihood > 0.3f) { // Lowered threshold for better leg detection
                drawCircle(
                    color = Color.Cyan,
                    radius = 7f,
                    center = Offset(
                        it.position.x * scaleX,
                        it.position.y * scaleY
                    )
                )
            }
        }
    }
}

/**
 * Draw connections between pose landmarks with coordinate transformation
 */
private fun DrawScope.drawPoseConnections(
    pose: Pose, 
    transformedPoints: Map<PoseLandmark, Offset>
) {
    val connections = listOf(
        // Face outline
        Pair(PoseLandmark.LEFT_EAR, PoseLandmark.LEFT_EYE_OUTER),
        Pair(PoseLandmark.LEFT_EYE_OUTER, PoseLandmark.LEFT_EYE),
        Pair(PoseLandmark.LEFT_EYE, PoseLandmark.NOSE),
        Pair(PoseLandmark.NOSE, PoseLandmark.RIGHT_EYE),
        Pair(PoseLandmark.RIGHT_EYE, PoseLandmark.RIGHT_EYE_OUTER),
        Pair(PoseLandmark.RIGHT_EYE_OUTER, PoseLandmark.RIGHT_EAR),
        
        // Shoulders
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
        
        // Left arm
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
        Pair(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
        
        // Right arm
        Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
        Pair(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
        
        // Torso
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
        Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP),
        Pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
        
        // Left leg
        Pair(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
        Pair(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
        Pair(PoseLandmark.LEFT_ANKLE, PoseLandmark.LEFT_HEEL),
        Pair(PoseLandmark.LEFT_HEEL, PoseLandmark.LEFT_FOOT_INDEX),
        
        // Right leg
        Pair(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
        Pair(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
        Pair(PoseLandmark.RIGHT_ANKLE, PoseLandmark.RIGHT_HEEL),
        Pair(PoseLandmark.RIGHT_HEEL, PoseLandmark.RIGHT_FOOT_INDEX)
    )
    
    connections.forEach { (startType, endType) ->
        val startLandmark = pose.getPoseLandmark(startType)
        val endLandmark = pose.getPoseLandmark(endType)
        
        if (startLandmark != null && endLandmark != null &&
            startLandmark.inFrameLikelihood > 0.3f && endLandmark.inFrameLikelihood > 0.3f) {
            
            val startPoint = transformedPoints[startLandmark]
            val endPoint = transformedPoints[endLandmark]
            
            if (startPoint != null && endPoint != null) {
                drawLine(
                    color = Color.Blue,
                    start = startPoint,
                    end = endPoint,
                    strokeWidth = 3f
                )
            }
        }
    }
}

/**
 * Legacy method for backward compatibility
 */
private fun DrawScope.drawPoseConnections(pose: Pose, scaleX: Float, scaleY: Float) {
    val connections = listOf(
        // Face outline
        Pair(PoseLandmark.LEFT_EAR, PoseLandmark.LEFT_EYE_OUTER),
        Pair(PoseLandmark.LEFT_EYE_OUTER, PoseLandmark.LEFT_EYE),
        Pair(PoseLandmark.LEFT_EYE, PoseLandmark.NOSE),
        Pair(PoseLandmark.NOSE, PoseLandmark.RIGHT_EYE),
        Pair(PoseLandmark.RIGHT_EYE, PoseLandmark.RIGHT_EYE_OUTER),
        Pair(PoseLandmark.RIGHT_EYE_OUTER, PoseLandmark.RIGHT_EAR),
        
        // Shoulders
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
        
        // Left arm
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
        Pair(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
        
        // Right arm
        Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
        Pair(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
        
        // Torso
        Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
        Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP),
        Pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
        
        // Left leg
        Pair(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
        Pair(PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
        Pair(PoseLandmark.LEFT_ANKLE, PoseLandmark.LEFT_HEEL),
        Pair(PoseLandmark.LEFT_HEEL, PoseLandmark.LEFT_FOOT_INDEX),
        
        // Right leg
        Pair(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
        Pair(PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
        Pair(PoseLandmark.RIGHT_ANKLE, PoseLandmark.RIGHT_HEEL),
        Pair(PoseLandmark.RIGHT_HEEL, PoseLandmark.RIGHT_FOOT_INDEX)
    )
    
    connections.forEach { (startType, endType) ->
        val startLandmark = pose.getPoseLandmark(startType)
        val endLandmark = pose.getPoseLandmark(endType)
        
        if (startLandmark != null && endLandmark != null &&
            startLandmark.inFrameLikelihood > 0.3f && endLandmark.inFrameLikelihood > 0.3f) {
            
            drawLine(
                color = Color.Blue,
                start = Offset(
                    startLandmark.position.x * scaleX,
                    startLandmark.position.y * scaleY
                ),
                end = Offset(
                    endLandmark.position.x * scaleX,
                    endLandmark.position.y * scaleY
                ),
                strokeWidth = 3f
            )
        }
    }
}
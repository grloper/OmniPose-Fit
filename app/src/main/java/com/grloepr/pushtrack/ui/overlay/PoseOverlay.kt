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

/**
 * Composable that overlays pose detection landmarks on the camera preview
 * As per documentation: draws key landmarks as green circles and blue skeleton lines
 * Uses actual image dimensions for proper coordinate transformation
 */
@Composable
fun PoseOverlay(
    pose: Pose?,
    imageWidth: Int,
    imageHeight: Int,
    isFrontCamera: Boolean = false,  // Whether front camera is being used
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        pose?.let { detectedPose ->
            // Calculate scaling factors using actual image dimensions
            val scaleX = size.width / imageWidth.toFloat()
            val scaleY = size.height / imageHeight.toFloat()
            
            // Draw key landmarks as green circles (as documented)
            drawPoseLandmarks(detectedPose, scaleX, scaleY, imageWidth, isFrontCamera)
            
            // Draw skeleton connections as blue lines (as documented)
            drawPoseConnections(detectedPose, scaleX, scaleY, imageWidth, isFrontCamera)
        }
    }
}

/**
 * Draw key landmarks (shoulders, elbows, wrists, hips) as green circles (as documented)
 * Only renders landmarks with >50% confidence (as documented)
 */
private fun DrawScope.drawPoseLandmarks(
    pose: Pose,
    scaleX: Float,
    scaleY: Float,
    imageWidth: Int,
    isFrontCamera: Boolean
) {
    // Key landmarks as specified in documentation
    val keyLandmarks = listOf(
        PoseLandmark.LEFT_SHOULDER,
        PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW,
        PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_HIP
    )
    
    keyLandmarks.forEach { landmarkType ->
        val landmark = pose.getPoseLandmark(landmarkType)
        landmark?.let {
            // Only render landmarks with >50% confidence (as documented)
            if (it.inFrameLikelihood > 0.5f) {
                val x = if (isFrontCamera) imageWidth - it.position.x else it.position.x
                drawCircle(
                    color = Color.Green,  // Green circles as documented
                    radius = 8f,
                    center = Offset(
                        x * scaleX,
                        it.position.y * scaleY
                    )
                )
            }
        }
    }
}

/**
 * Draw skeleton connections as blue lines (as documented)
 */
private fun DrawScope.drawPoseConnections(
    pose: Pose,
    scaleX: Float,
    scaleY: Float,
    imageWidth: Int,
    isFrontCamera: Boolean
) {
    // Skeleton connections for key body parts
    val connections = listOf(
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
        Pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
    )
    
    connections.forEach { (startType, endType) ->
        val startLandmark = pose.getPoseLandmark(startType)
        val endLandmark = pose.getPoseLandmark(endType)
        
        if (startLandmark != null && endLandmark != null &&
            startLandmark.inFrameLikelihood > 0.5f && endLandmark.inFrameLikelihood > 0.5f) {
            
            val startX = if (isFrontCamera) imageWidth - startLandmark.position.x else startLandmark.position.x
            val endX = if (isFrontCamera) imageWidth - endLandmark.position.x else endLandmark.position.x
            
            drawLine(
                color = Color.Blue,  // Blue lines as documented
                start = Offset(
                    startX * scaleX,
                    startLandmark.position.y * scaleY
                ),
                end = Offset(
                    endX * scaleX,
                    endLandmark.position.y * scaleY
                ),
                strokeWidth = 3f
            )
        }
    }
}
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
 */
@Composable
fun PoseOverlay(
    pose: Pose?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        pose?.let { detectedPose ->
            drawPoseLandmarks(detectedPose)
            drawPoseConnections(detectedPose)
        }
    }
}

/**
 * Draw individual pose landmarks as circles
 */
private fun DrawScope.drawPoseLandmarks(pose: Pose) {
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
            // Only draw landmarks with reasonable confidence
            if (it.inFrameLikelihood > 0.5f) {
                drawCircle(
                    color = Color.Green,
                    radius = 8f,
                    center = Offset(it.position.x, it.position.y)
                )
            }
        }
    }
}

/**
 * Draw connections between pose landmarks
 */
private fun DrawScope.drawPoseConnections(pose: Pose) {
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
            
            drawLine(
                color = Color.Blue,
                start = Offset(startLandmark.position.x, startLandmark.position.y),
                end = Offset(endLandmark.position.x, endLandmark.position.y),
                strokeWidth = 4f
            )
        }
    }
}
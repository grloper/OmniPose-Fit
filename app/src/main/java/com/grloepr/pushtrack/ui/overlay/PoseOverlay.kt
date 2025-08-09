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
 * Transforms coordinates to properly align with camera preview
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
 * Draw individual pose landmarks as circles with coordinate transformation
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
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
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import kotlin.math.min

/**
 * Configurable styling and thresholds for the pose overlay.
 * Keeping this data class allows easy tweaking for different cameras (e.g. Galaxy S24 Ultra) without
 * touching the rendering code.
 */
data class PoseOverlayStyle(
    val headConfidenceThreshold: Float = 0.6f,
    val upperBodyConfidenceThreshold: Float = 0.5f,
    val lowerBodyConfidenceThreshold: Float = 0.3f,
    val connectionConfidenceThreshold: Float = 0.3f,
    val headRadius: Float = 6f,
    val upperBodyRadius: Float = 8f,
    val lowerBodyRadius: Float = 7f,
    val headColor: Color = Color(0xFFFFD54F),
    val upperBodyColor: Color = Color(0xFF00E676),
    val lowerBodyColor: Color = Color(0xFF64B5F6),
    val connectionColor: Color = Color(0xFF4CAF50),
    val connectionStrokeWidth: Float = 4f
)

/**
 * Composable that overlays pose detection landmarks on the camera preview.
 * Handles rotation, aspect-ratio differences, and front camera mirroring so the skeleton sticks to
 * the subject regardless of device orientation or resolution.
 */
@Composable
fun PoseOverlay(
    poseResult: PoseDetectionResult,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier,
    style: PoseOverlayStyle = PoseOverlayStyle()
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val transformer = PoseCoordinateTransformer(
            imageWidth = poseResult.imageWidth,
            imageHeight = poseResult.imageHeight,
            rotationDegrees = poseResult.rotationDegrees,
            isFrontCamera = isFrontCamera,
            canvasWidth = size.width,
            canvasHeight = size.height
        )

        drawPoseLandmarks(poseResult.pose, transformer, style)
        drawPoseConnections(poseResult.pose, transformer, style)
    }
}

/**
 * Converts ML Kit landmark positions into canvas coordinates, accounting for rotation, mirroring,
 * and aspect-ratio scaling.
 */
private class PoseCoordinateTransformer(
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val rotationDegrees: Int,
    private val isFrontCamera: Boolean,
    canvasWidth: Float,
    canvasHeight: Float
) {
    private val effectiveImageWidth: Float
    private val effectiveImageHeight: Float
    private val scaleX: Float
    private val scaleY: Float
    private val offsetX: Float
    private val offsetY: Float

    init {
        // The imageWidth and imageHeight here are the actual media dimensions from the camera
        // ML Kit's InputImage.fromMediaImage() with rotationDegrees rotates the internal processing,
        // but the landmarks are returned in the COORDINATE SYSTEM of the rotated image
        
        // When we pass rotationDegrees to InputImage, ML Kit:
        // 1. Processes the image with that rotation
        // 2. Returns landmarks in the coordinate system of the ROTATED image
        // So for 90°/270°, landmarks are in a coordinate system where width/height are swapped
        
        // Since landmarks are in rotated coordinates, we need to account for dimension swap
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        effectiveImageWidth = if (swapped) imageHeight.toFloat() else imageWidth.toFloat()
        effectiveImageHeight = if (swapped) imageWidth.toFloat() else imageHeight.toFloat()

        // Calculate scaling to fit canvas (matching PreviewView FIT_CENTER behavior)
        val scale = min(canvasWidth / effectiveImageWidth, canvasHeight / effectiveImageHeight)
        scaleX = scale
        scaleY = scale

        // Calculate centering offsets for letterboxing/pillarboxing
        val scaledWidth = effectiveImageWidth * scaleX
        val scaledHeight = effectiveImageHeight * scaleY
        offsetX = (canvasWidth - scaledWidth) / 2f
        offsetY = (canvasHeight - scaledHeight) / 2f
        
        // Debug logging
        println("PoseTransformer: media=$imageWidth×$imageHeight, rotation=$rotationDegrees°")
        println("PoseTransformer: effective=$effectiveImageWidth×$effectiveImageHeight, canvas=$canvasWidth×$canvasHeight")
        println("PoseTransformer: scale=$scale, offsets=($offsetX,$offsetY), isFront=$isFrontCamera")
    }

    fun map(landmark: PoseLandmark): Offset {
        // ML Kit returns coordinates in the rotated image coordinate space
        // Since we pass rotationDegrees to InputImage.fromMediaImage(), the landmarks
        // are already in the coordinate system that matches display orientation
        
        var x = landmark.position.x
        var y = landmark.position.y
        
        // Apply horizontal mirroring for front camera
        // PreviewView automatically mirrors the camera preview for front camera,
        // so we need to mirror the landmarks to match
        if (isFrontCamera) {
            x = effectiveImageWidth - x
        }

        // Ensure coordinates are within bounds
        x = x.coerceIn(0f, effectiveImageWidth)
        y = y.coerceIn(0f, effectiveImageHeight)

        // Scale and translate to canvas coordinates (accounting for FIT_CENTER)
        val canvasX = offsetX + x * scaleX
        val canvasY = offsetY + y * scaleY

        return Offset(canvasX, canvasY)
    }
}

private fun DrawScope.drawPoseLandmarks(
    pose: Pose,
    transformer: PoseCoordinateTransformer,
    style: PoseOverlayStyle
) {
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

    headLandmarks.forEach { type ->
        pose.getPoseLandmark(type)
            ?.takeIf { it.inFrameLikelihood >= style.headConfidenceThreshold }
            ?.let { landmark ->
                val center = transformer.map(landmark)
                drawCircle(
                    color = style.headColor,
                    radius = style.headRadius,
                    center = center
                )
            }
    }

    upperBodyLandmarks.forEach { type ->
        pose.getPoseLandmark(type)
            ?.takeIf { it.inFrameLikelihood >= style.upperBodyConfidenceThreshold }
            ?.let { landmark ->
                val center = transformer.map(landmark)
                drawCircle(
                    color = style.upperBodyColor,
                    radius = style.upperBodyRadius,
                    center = center
                )
            }
    }

    lowerBodyLandmarks.forEach { type ->
        pose.getPoseLandmark(type)
            ?.takeIf { it.inFrameLikelihood >= style.lowerBodyConfidenceThreshold }
            ?.let { landmark ->
                val center = transformer.map(landmark)
                drawCircle(
                    color = style.lowerBodyColor,
                    radius = style.lowerBodyRadius,
                    center = center
                )
            }
    }
}

private fun DrawScope.drawPoseConnections(
    pose: Pose,
    transformer: PoseCoordinateTransformer,
    style: PoseOverlayStyle
) {
    val connections = listOf(
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
        PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
        PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
        PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
        PoseLandmark.LEFT_HEEL to PoseLandmark.LEFT_FOOT_INDEX,
        PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
        PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
        PoseLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_FOOT_INDEX
    )

    connections.forEach { (startType, endType) ->
        val start = pose.getPoseLandmark(startType)
        val end = pose.getPoseLandmark(endType)

        if (start != null && end != null &&
            start.inFrameLikelihood >= style.connectionConfidenceThreshold &&
            end.inFrameLikelihood >= style.connectionConfidenceThreshold
        ) {
            val startOffset = transformer.map(start)
            val endOffset = transformer.map(end)
            drawLine(
                color = style.connectionColor,
                start = startOffset,
                end = endOffset,
                strokeWidth = style.connectionStrokeWidth
            )
        }
    }
}
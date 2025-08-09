package com.grloepr.pushtrack.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import com.grloepr.pushtrack.analysis.CombinedDetectionResult

/**
 * Ultra-Precise Face Overlay for rendering face landmarks with perfect alignment
 */
@Composable
fun FaceOverlay(
    combinedResult: CombinedDetectionResult?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        combinedResult?.let { result ->
            result.faces.forEach { face ->
                drawFaceLandmarks(face, result)
                drawFaceBoundingBox(face, result)
            }
        }
    }
}

/**
 * Draw face landmarks with ultra-precise coordinate transformation
 */
private fun DrawScope.drawFaceLandmarks(
    face: Face,
    result: CombinedDetectionResult
) {
    // Face landmark types to draw
    val landmarkTypes = listOf(
        FaceLandmark.LEFT_EYE,
        FaceLandmark.RIGHT_EYE,
        FaceLandmark.NOSE_BASE,
        FaceLandmark.LEFT_EAR,
        FaceLandmark.RIGHT_EAR,
        FaceLandmark.MOUTH_LEFT,
        FaceLandmark.MOUTH_RIGHT,
        FaceLandmark.MOUTH_BOTTOM,
        FaceLandmark.LEFT_CHEEK,
        FaceLandmark.RIGHT_CHEEK
    )
    
    landmarkTypes.forEach { landmarkType ->
        val landmark = face.getLandmark(landmarkType)
        landmark?.let {
            val transformedPoint = transformFaceLandmarkCoordinate(
                landmark = it,
                result = result,
                canvasWidth = size.width,
                canvasHeight = size.height
            )
            
            // Draw landmark as small circle
            drawCircle(
                color = Color.Magenta,
                radius = 4f,
                center = transformedPoint
            )
        }
    }
}

/**
 * Draw face bounding box with ultra-precise coordinate transformation
 */
private fun DrawScope.drawFaceBoundingBox(
    face: Face,
    result: CombinedDetectionResult
) {
    val boundingBox = face.boundingBox
    
    // Transform bounding box coordinates
    val topLeft = transformFaceCoordinate(
        x = boundingBox.left.toFloat(),
        y = boundingBox.top.toFloat(),
        result = result,
        canvasWidth = size.width,
        canvasHeight = size.height
    )
    
    val bottomRight = transformFaceCoordinate(
        x = boundingBox.right.toFloat(),
        y = boundingBox.bottom.toFloat(),
        result = result,
        canvasWidth = size.width,
        canvasHeight = size.height
    )
    
    // Draw bounding box
    drawRect(
        color = Color.Magenta,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(
            width = bottomRight.x - topLeft.x,
            height = bottomRight.y - topLeft.y
        ),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
    )
}

/**
 * Transform face landmark coordinate with ultra-precise alignment
 */
private fun transformFaceLandmarkCoordinate(
    landmark: FaceLandmark,
    result: CombinedDetectionResult,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    return transformFaceCoordinate(
        x = landmark.position.x,
        y = landmark.position.y,
        result = result,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight
    )
}

/**
 * Transform face coordinate with ultra-precise alignment (reuse pose transformation logic)
 */
private fun transformFaceCoordinate(
    x: Float,
    y: Float,
    result: CombinedDetectionResult,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    // Use the same ultra-precise transformation logic as pose overlay
    
    // Step 1: Apply rotation transformation
    val (rotatedX, rotatedY, effectiveWidth, effectiveHeight) = when (result.rotationDegrees) {
        90 -> {
            val newX = result.imageHeight - y
            val newY = x
            Quadruple(newX, newY, result.imageHeight.toFloat(), result.imageWidth.toFloat())
        }
        180 -> {
            val newX = result.imageWidth - x
            val newY = result.imageHeight - y
            Quadruple(newX, newY, result.imageWidth.toFloat(), result.imageHeight.toFloat())
        }
        270 -> {
            val newX = y
            val newY = result.imageWidth - x
            Quadruple(newX, newY, result.imageHeight.toFloat(), result.imageWidth.toFloat())
        }
        else -> {
            Quadruple(x, y, result.imageWidth.toFloat(), result.imageHeight.toFloat())
        }
    }
    
    // Step 2: Scale to canvas size with proper aspect ratio preservation
    val scaleX = canvasWidth / effectiveWidth
    val scaleY = canvasHeight / effectiveHeight
    val uniformScale = minOf(scaleX, scaleY)
    
    var scaledX = rotatedX * uniformScale
    var scaledY = rotatedY * uniformScale
    
    // Step 3: Center the image in canvas
    val imageCanvasWidth = effectiveWidth * uniformScale
    val imageCanvasHeight = effectiveHeight * uniformScale
    
    val offsetX = (canvasWidth - imageCanvasWidth) / 2f
    val offsetY = (canvasHeight - imageCanvasHeight) / 2f
    
    scaledX += offsetX
    scaledY += offsetY
    
    // Step 4: Apply horizontal mirroring for front camera
    if (result.isFrontCamera) {
        scaledX = canvasWidth - scaledX
    }
    
    return Offset(scaledX, scaledY)
}

/**
 * Data class to hold four values for coordinate transformation
 */
private data class Quadruple<T>(val first: T, val second: T, val third: T, val fourth: T)
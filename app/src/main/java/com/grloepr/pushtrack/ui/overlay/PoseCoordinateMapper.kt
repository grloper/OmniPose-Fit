package com.grloepr.pushtrack.ui.overlay

import androidx.compose.ui.geometry.Offset
import kotlin.math.min

/**
 * Converts ML Kit landmark positions into canvas coordinates, accounting for
 * rotation-induced dimension swaps, front-camera mirroring, and the
 * FIT_CENTER letterboxing used by the camera preview.
 */
class PoseCoordinateMapper(
    imageWidth: Int,
    imageHeight: Int,
    rotationDegrees: Int,
    private val isFrontCamera: Boolean,
    canvasWidth: Float,
    canvasHeight: Float
) {
    private val effectiveImageWidth: Float
    private val effectiveImageHeight: Float
    private val scale: Float
    private val offsetX: Float
    private val offsetY: Float

    init {
        // ML Kit returns landmarks in the coordinate system of the ROTATED image,
        // so 90°/270° rotations swap the effective dimensions.
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        effectiveImageWidth = if (swapped) imageHeight.toFloat() else imageWidth.toFloat()
        effectiveImageHeight = if (swapped) imageWidth.toFloat() else imageHeight.toFloat()

        scale = min(canvasWidth / effectiveImageWidth, canvasHeight / effectiveImageHeight)
        offsetX = (canvasWidth - effectiveImageWidth * scale) / 2f
        offsetY = (canvasHeight - effectiveImageHeight * scale) / 2f
    }

    fun map(x: Float, y: Float): Offset {
        // PreviewView mirrors the front camera feed; mirror landmarks to match.
        var mappedX = if (isFrontCamera) effectiveImageWidth - x else x
        var mappedY = y

        mappedX = mappedX.coerceIn(0f, effectiveImageWidth)
        mappedY = mappedY.coerceIn(0f, effectiveImageHeight)

        return Offset(offsetX + mappedX * scale, offsetY + mappedY * scale)
    }
}

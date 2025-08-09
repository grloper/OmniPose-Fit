package com.grloepr.pushtrack.ui.overlay

/**
 * Result container for coordinate transformation steps.
 * Destructuring order: (x, y, width, height)
 */
data class TransformResult(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

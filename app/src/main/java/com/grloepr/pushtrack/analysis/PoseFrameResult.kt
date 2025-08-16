package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose

/**
 * Data class to hold pose detection results with complete frame information
 * for accurate coordinate transformation and rendering
 */
data class PoseFrameResult(
    val pose: Pose,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val isFrontCamera: Boolean
)

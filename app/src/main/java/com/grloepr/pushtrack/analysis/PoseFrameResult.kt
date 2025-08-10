package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose

/**
 * Data class to hold pose detection results with complete frame information
 * for accurate coordinate transformation and rendering
 */
data class PoseFrameResult(
    val pose: Pose,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val isFrontCamera: Boolean
)

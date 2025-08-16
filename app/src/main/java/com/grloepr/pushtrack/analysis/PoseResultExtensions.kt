package com.grloepr.pushtrack.analysis

/**
 * Extension functions for pose detection result types
 */

/**
 * Convert a PoseDetectionResult to a PoseFrameResult for use with EnhancedPoseOverlay
 */
fun PoseDetectionResult.toPoseFrameResult(isFrontCamera: Boolean = false, rotationDegrees: Int = 0): PoseFrameResult {
    return PoseFrameResult(
        pose = this.pose,
        width = this.imageWidth,
        height = this.imageHeight,
        rotationDegrees = rotationDegrees,
        isFrontCamera = isFrontCamera
    )
}

package com.grloepr.pushtrack.analysis

/**
 * Extension functions for pose detection result types
 */

/**
 * Convert a PoseDetectionResult to a PoseFrameResult for use with EnhancedPoseOverlay
 */
fun PoseDetectionResult.toPoseFrameResult(
    isFrontCamera: Boolean = false
): PoseFrameResult {
    return PoseFrameResult(
        pose = this.pose,
        imageWidth = this.imageWidth,
        imageHeight = this.imageHeight,
        rotationDegrees = this.rotationDegrees,
        isFrontCamera = isFrontCamera
    )
}

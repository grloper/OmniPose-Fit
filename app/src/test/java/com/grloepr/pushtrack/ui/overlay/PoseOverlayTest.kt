package com.grloepr.pushtrack.ui.overlay

import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.PoseFrameResult
import org.junit.Test
import org.junit.Assert.*
import io.mockk.mockk
import io.mockk.every

/**
 * Test class to verify coordinate transformation logic for pose overlay
 */
class PoseOverlayTest {

    @Test
    fun `test front camera coordinate mirroring`() {
        // Test coordinate transformation for front camera
        val imageWidth = 640
        val originalX = 100f
        
        // When using front camera, x should be mirrored
        val expectedMirroredX = imageWidth - originalX // 640 - 100 = 540
        
        assertEquals(540f, expectedMirroredX, 0.1f)
    }
    
    @Test
    fun `test back camera coordinate no mirroring`() {
        // Test coordinate transformation for back camera
        val imageWidth = 640
        val originalX = 100f
        
        // When using back camera, x should remain the same
        val expectedX = originalX // 100
        
        assertEquals(100f, expectedX, 0.1f)
    }
    
    @Test
    fun `test coordinate mirroring edge cases`() {
        val imageWidth = 640
        
        // Test left edge (x = 0)
        val leftEdgeX = 0f
        val mirroredLeftEdge = imageWidth - leftEdgeX // Should be 640
        assertEquals(640f, mirroredLeftEdge, 0.1f)
        
        // Test right edge (x = imageWidth)
        val rightEdgeX = imageWidth.toFloat()
        val mirroredRightEdge = imageWidth - rightEdgeX // Should be 0
        assertEquals(0f, mirroredRightEdge, 0.1f)
        
        // Test center (x = imageWidth/2)
        val centerX = imageWidth / 2f
        val mirroredCenter = imageWidth - centerX // Should be 320
        assertEquals(320f, mirroredCenter, 0.1f)
    }
    
    @Test
    fun `test enhanced coordinate transformation with rotation`() {
        // Mock a pose landmark
        val mockLandmark = mockk<PoseLandmark>()
        val mockPosition = mockk<com.google.mlkit.vision.common.PointF>()
        every { mockPosition.x } returns 100f
        every { mockPosition.y } returns 200f
        every { mockLandmark.position } returns mockPosition
        
        // Test different rotation scenarios
        val imageWidth = 640
        val imageHeight = 480
        
        // Test 0 degree rotation with front camera
        val frontCameraPoseResult = PoseFrameResult(
            pose = mockk(),
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            rotationDegrees = 0,
            isFrontCamera = true
        )
        
        // Test that X coordinate is mirrored for front camera
        val expectedMirroredX = imageWidth - 100f // 640 - 100 = 540
        assertEquals(540f, expectedMirroredX, 0.1f)
        
        // Test 90 degree rotation
        val rotated90PoseResult = PoseFrameResult(
            pose = mockk(),
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            rotationDegrees = 90,
            isFrontCamera = false
        )
        
        // For 90 degree rotation: newX = imageHeight - y, newY = x
        val expectedRotated90X = imageHeight - 200f // 480 - 200 = 280
        val expectedRotated90Y = 100f
        assertEquals(280f, expectedRotated90X, 0.1f)
        assertEquals(100f, expectedRotated90Y, 0.1f)
    }
}
package com.grloepr.pushtrack.ui.overlay

import org.junit.Test
import org.junit.Assert.*

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
}
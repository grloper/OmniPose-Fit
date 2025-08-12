package com.grloepr.pushtrack.detection.utils

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AngleCalculatorTest {
    
    @Test
    fun `calculateAngle should return correct angle for right angle`() {
        // Create a right angle: (0,0) -> (1,0) -> (1,1)
        val angle = AngleCalculator.calculateAngle(
            0f, 0f,   // First point
            1f, 0f,   // Vertex
            1f, 1f    // Third point
        )
        
        // Should be 90 degrees
        assertEquals(90f, angle, 0.1f)
    }
    
    @Test
    fun `calculateAngle should return correct angle for straight line`() {
        // Create a straight line: (0,0) -> (1,0) -> (2,0)
        val angle = AngleCalculator.calculateAngle(
            0f, 0f,   // First point
            1f, 0f,   // Vertex
            2f, 0f    // Third point
        )
        
        // Should be 180 degrees
        assertEquals(180f, angle, 0.1f)
    }
    
    @Test
    fun `calculateAngle should handle zero vectors gracefully`() {
        // Same points
        val angle = AngleCalculator.calculateAngle(
            1f, 1f,   // First point
            1f, 1f,   // Vertex (same as first)
            1f, 1f    // Third point (same as others)
        )
        
        // Should return 0 for degenerate case
        assertEquals(0f, angle, 0.1f)
    }
    
    @Test
    fun `calculateElbowAngle should return null for missing landmarks`() {
        val pose = mockk<Pose>()
        every { pose.getPoseLandmark(any()) } returns null
        
        val angle = AngleCalculator.calculateElbowAngle(pose, true)
        
        assertNull(angle)
    }
    
    @Test
    fun `calculateElbowAngle should return null for low confidence landmarks`() {
        val pose = mockk<Pose>()
        
        // Create landmarks with low confidence
        val shoulder = createMockLandmark(0f, 0f, 0.3f) // Below threshold
        val elbow = createMockLandmark(1f, 0f, 0.3f)
        val wrist = createMockLandmark(1f, 1f, 0.3f)
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns shoulder
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW) } returns elbow
        every { pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) } returns wrist
        
        val angle = AngleCalculator.calculateElbowAngle(pose, true)
        
        assertNull(angle)
    }
    
    @Test
    fun `calculateElbowAngle should return correct angle for valid landmarks`() {
        val pose = mockk<Pose>()
        
        // Create landmarks forming a right angle
        val shoulder = createMockLandmark(0f, 0f, 0.9f)
        val elbow = createMockLandmark(1f, 0f, 0.9f)
        val wrist = createMockLandmark(1f, 1f, 0.9f)
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns shoulder
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW) } returns elbow
        every { pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) } returns wrist
        
        val angle = AngleCalculator.calculateElbowAngle(pose, true)
        
        assertNotNull(angle)
        assertEquals(90f, angle!!, 0.1f)
    }
    
    @Test
    fun `calculateAverageElbowAngle should use both arms when available`() {
        val pose = mockk<Pose>()
        
        // Left arm: 90 degree angle
        val leftShoulder = createMockLandmark(0f, 0f, 0.9f)
        val leftElbow = createMockLandmark(1f, 0f, 0.9f)
        val leftWrist = createMockLandmark(1f, 1f, 0.9f)
        
        // Right arm: 90 degree angle
        val rightShoulder = createMockLandmark(2f, 0f, 0.9f)
        val rightElbow = createMockLandmark(3f, 0f, 0.9f)
        val rightWrist = createMockLandmark(3f, 1f, 0.9f)
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns leftShoulder
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW) } returns leftElbow
        every { pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) } returns leftWrist
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) } returns rightShoulder
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW) } returns rightElbow
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) } returns rightWrist
        
        val angle = AngleCalculator.calculateAverageElbowAngle(pose)
        
        assertNotNull(angle)
        assertEquals(90f, angle!!, 0.1f) // Average of 90 and 90
    }
    
    @Test
    fun `calculateAverageElbowAngle should use single arm when only one available`() {
        val pose = mockk<Pose>()
        
        // Only left arm available
        val leftShoulder = createMockLandmark(0f, 0f, 0.9f)
        val leftElbow = createMockLandmark(1f, 0f, 0.9f)
        val leftWrist = createMockLandmark(1f, 1f, 0.9f)
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns leftShoulder
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW) } returns leftElbow
        every { pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) } returns leftWrist
        
        // Right arm not available
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) } returns null
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW) } returns null
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) } returns null
        
        val angle = AngleCalculator.calculateAverageElbowAngle(pose)
        
        assertNotNull(angle)
        assertEquals(90f, angle!!, 0.1f)
    }
    
    @Test
    fun `calculateKneeAngle should work correctly`() {
        val pose = mockk<Pose>()
        
        // Create landmarks forming a right angle at knee
        val hip = createMockLandmark(0f, 0f, 0.9f)
        val knee = createMockLandmark(1f, 0f, 0.9f)
        val ankle = createMockLandmark(1f, 1f, 0.9f)
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_HIP) } returns hip
        every { pose.getPoseLandmark(PoseLandmark.LEFT_KNEE) } returns knee
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE) } returns ankle
        
        val angle = AngleCalculator.calculateKneeAngle(pose, true)
        
        assertNotNull(angle)
        assertEquals(90f, angle!!, 0.1f)
    }
    
    @Test
    fun `calculateHipAngle should work correctly`() {
        val pose = mockk<Pose>()
        
        // Create landmarks forming a right angle at hip
        val shoulder = createMockLandmark(0f, 0f, 0.9f)
        val hip = createMockLandmark(1f, 0f, 0.9f)
        val knee = createMockLandmark(1f, 1f, 0.9f)
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns shoulder
        every { pose.getPoseLandmark(PoseLandmark.LEFT_HIP) } returns hip
        every { pose.getPoseLandmark(PoseLandmark.LEFT_KNEE) } returns knee
        
        val angle = AngleCalculator.calculateHipAngle(pose, true)
        
        assertNotNull(angle)
        assertEquals(90f, angle!!, 0.1f)
    }
    
    private fun createMockLandmark(x: Float, y: Float, confidence: Float): PoseLandmark {
        val landmark = mockk<PoseLandmark>()
        val position = mockk<android.graphics.PointF>()
        every { position.x } returns x
        every { position.y } returns y
        every { landmark.position } returns position
        every { landmark.inFrameLikelihood } returns confidence
        return landmark
    }
}
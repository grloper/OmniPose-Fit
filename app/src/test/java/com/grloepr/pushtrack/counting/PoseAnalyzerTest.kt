package com.grloepr.pushtrack.counting

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import kotlin.math.abs

/**
 * Unit tests for PoseAnalyzer geometry calculations
 */
class PoseAnalyzerTest {
    
    @Test
    fun `calculateElbowAngle should return correct angle for straight arm`() {
        val mockPose = createMockPoseWithArm(
            shoulderX = 0f, shoulderY = 0f,
            elbowX = 10f, elbowY = 0f,
            wristX = 20f, wristY = 0f
        )
        
        val angle = PoseAnalyzer.calculateElbowAngle(mockPose, isLeftArm = true)
        
        assertNotNull("Angle should not be null", angle)
        assertEquals("Straight arm should be ~180 degrees", 180f, angle!!, 5f)
    }
    
    @Test
    fun `calculateElbowAngle should return correct angle for right angle`() {
        val mockPose = createMockPoseWithArm(
            shoulderX = 0f, shoulderY = 0f,
            elbowX = 10f, elbowY = 0f,
            wristX = 10f, wristY = 10f
        )
        
        val angle = PoseAnalyzer.calculateElbowAngle(mockPose, isLeftArm = true)
        
        assertNotNull("Angle should not be null", angle)
        assertEquals("Right angle should be ~90 degrees", 90f, angle!!, 5f)
    }
    
    @Test
    fun `calculateElbowAngle should return null for low confidence landmarks`() {
        val mockPose = createMockPoseWithArm(
            shoulderX = 0f, shoulderY = 0f,
            elbowX = 10f, elbowY = 0f,
            wristX = 20f, wristY = 0f,
            confidence = 0.3f // Below threshold
        )
        
        val angle = PoseAnalyzer.calculateElbowAngle(mockPose, isLeftArm = true)
        
        assertNull("Angle should be null for low confidence", angle)
    }
    
    @Test
    fun `calculateElbowAngle should return null for missing landmarks`() {
        val mockPose = mock(Pose::class.java)
        `when`(mockPose.getPoseLandmark(any())).thenReturn(null)
        
        val angle = PoseAnalyzer.calculateElbowAngle(mockPose, isLeftArm = true)
        
        assertNull("Angle should be null for missing landmarks", angle)
    }
    
    @Test
    fun `calculateAverageElbowAngle should average both arms correctly`() {
        val mockPose = mock(Pose::class.java)
        
        // Left arm at 90 degrees
        val leftShoulder = createMockLandmark(0f, 0f, 0.9f)
        val leftElbow = createMockLandmark(10f, 0f, 0.9f)
        val leftWrist = createMockLandmark(10f, 10f, 0.9f)
        
        // Right arm at 120 degrees  
        val rightShoulder = createMockLandmark(30f, 0f, 0.9f)
        val rightElbow = createMockLandmark(20f, 0f, 0.9f)
        val rightWrist = createMockLandmark(15f, 8.66f, 0.9f) // ~120 degree angle
        
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)).thenReturn(leftShoulder)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)).thenReturn(leftElbow)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_WRIST)).thenReturn(leftWrist)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)).thenReturn(rightShoulder)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)).thenReturn(rightElbow)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)).thenReturn(rightWrist)
        
        val avgAngle = PoseAnalyzer.calculateAverageElbowAngle(mockPose)
        
        assertNotNull("Average angle should not be null", avgAngle)
        assertEquals("Average should be ~105 degrees", 105f, avgAngle!!, 10f)
    }
    
    @Test
    fun `calculateAverageElbowAngle should use single arm when other is not visible`() {
        val mockPose = mock(Pose::class.java)
        
        // Only left arm visible
        val leftShoulder = createMockLandmark(0f, 0f, 0.9f)
        val leftElbow = createMockLandmark(10f, 0f, 0.9f)
        val leftWrist = createMockLandmark(10f, 10f, 0.9f)
        
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)).thenReturn(leftShoulder)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)).thenReturn(leftElbow)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_WRIST)).thenReturn(leftWrist)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)).thenReturn(null)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)).thenReturn(null)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)).thenReturn(null)
        
        val avgAngle = PoseAnalyzer.calculateAverageElbowAngle(mockPose)
        
        assertNotNull("Average angle should not be null", avgAngle)
        assertEquals("Should use left arm angle", 90f, avgAngle!!, 5f)
    }
    
    @Test
    fun `calculateTorsoAngle should return horizontal angle for plank position`() {
        val mockPose = mock(Pose::class.java)
        
        // Horizontal torso (plank position)
        val leftShoulder = createMockLandmark(100f, 100f, 0.9f)
        val rightShoulder = createMockLandmark(200f, 100f, 0.9f)
        val leftHip = createMockLandmark(100f, 150f, 0.9f)
        val rightHip = createMockLandmark(200f, 150f, 0.9f)
        
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)).thenReturn(leftShoulder)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)).thenReturn(rightShoulder)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_HIP)).thenReturn(leftHip)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_HIP)).thenReturn(rightHip)
        
        val torsoAngle = PoseAnalyzer.calculateTorsoAngle(mockPose)
        
        assertNotNull("Torso angle should not be null", torsoAngle)
        // Should be close to -90 degrees (pointing downward)
        assertTrue("Torso angle should indicate horizontal position", abs(torsoAngle!! + 90f) < 10f)
    }
    
    @Test
    fun `isInPlankPosition should return true for horizontal torso`() {
        val mockPose = mock(Pose::class.java)
        
        // Create a pose where torso is roughly horizontal
        val leftShoulder = createMockLandmark(100f, 100f, 0.9f)
        val rightShoulder = createMockLandmark(200f, 105f, 0.9f) // Slight variation
        val leftHip = createMockLandmark(100f, 150f, 0.9f)
        val rightHip = createMockLandmark(200f, 155f, 0.9f)
        
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)).thenReturn(leftShoulder)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)).thenReturn(rightShoulder)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_HIP)).thenReturn(leftHip)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_HIP)).thenReturn(rightHip)
        
        val isPlank = PoseAnalyzer.isInPlankPosition(mockPose)
        
        assertTrue("Should detect plank position", isPlank)
    }
    
    @Test
    fun `isInPlankPosition should return false for vertical torso`() {
        val mockPose = mock(Pose::class.java)
        
        // Create a pose where torso is vertical (standing)
        val leftShoulder = createMockLandmark(100f, 100f, 0.9f)
        val rightShoulder = createMockLandmark(200f, 100f, 0.9f)
        val leftHip = createMockLandmark(100f, 200f, 0.9f) // Much lower
        val rightHip = createMockLandmark(200f, 200f, 0.9f)
        
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)).thenReturn(leftShoulder)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)).thenReturn(rightShoulder)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_HIP)).thenReturn(leftHip)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_HIP)).thenReturn(rightHip)
        
        val isPlank = PoseAnalyzer.isInPlankPosition(mockPose)
        
        assertFalse("Should not detect plank position for vertical torso", isPlank)
    }
    
    @Test
    fun `areArmsVisible should return true when at least one arm is visible`() {
        val mockPose = mock(Pose::class.java)
        
        // Only left arm visible
        val leftElbow = createMockLandmark(100f, 100f, 0.8f)
        val leftWrist = createMockLandmark(120f, 120f, 0.8f)
        
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)).thenReturn(leftElbow)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_WRIST)).thenReturn(leftWrist)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)).thenReturn(null)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)).thenReturn(null)
        
        val armsVisible = PoseAnalyzer.areArmsVisible(mockPose)
        
        assertTrue("Should detect arms as visible when left arm is visible", armsVisible)
    }
    
    @Test
    fun `areArmsVisible should return false when no arms are visible`() {
        val mockPose = mock(Pose::class.java)
        `when`(mockPose.getPoseLandmark(any())).thenReturn(null)
        
        val armsVisible = PoseAnalyzer.areArmsVisible(mockPose)
        
        assertFalse("Should not detect arms as visible when none are visible", armsVisible)
    }
    
    @Test
    fun `areArmsVisible should return false for low confidence landmarks`() {
        val mockPose = mock(Pose::class.java)
        
        // Low confidence landmarks
        val leftElbow = createMockLandmark(100f, 100f, 0.3f)
        val leftWrist = createMockLandmark(120f, 120f, 0.3f)
        val rightElbow = createMockLandmark(200f, 100f, 0.3f)
        val rightWrist = createMockLandmark(220f, 120f, 0.3f)
        
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)).thenReturn(leftElbow)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_WRIST)).thenReturn(leftWrist)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)).thenReturn(rightElbow)
        `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)).thenReturn(rightWrist)
        
        val armsVisible = PoseAnalyzer.areArmsVisible(mockPose)
        
        assertFalse("Should not detect arms as visible for low confidence", armsVisible)
    }
    
    /**
     * Helper function to create a mock pose with arm landmarks at specific positions
     */
    private fun createMockPoseWithArm(
        shoulderX: Float, shoulderY: Float,
        elbowX: Float, elbowY: Float,
        wristX: Float, wristY: Float,
        confidence: Float = 0.9f
    ): Pose {
        val mockPose = mock(Pose::class.java)
        
        val shoulder = createMockLandmark(shoulderX, shoulderY, confidence)
        val elbow = createMockLandmark(elbowX, elbowY, confidence)
        val wrist = createMockLandmark(wristX, wristY, confidence)
        
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)).thenReturn(shoulder)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)).thenReturn(elbow)
        `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_WRIST)).thenReturn(wrist)
        
        return mockPose
    }
    
    private fun createMockLandmark(x: Float, y: Float, confidence: Float): PoseLandmark {
        val mockLandmark = mock(PoseLandmark::class.java)
        val mockPosition = mock(PoseLandmark.Position::class.java)
        
        `when`(mockPosition.x).thenReturn(x)
        `when`(mockPosition.y).thenReturn(y)
        `when`(mockLandmark.position).thenReturn(mockPosition)
        `when`(mockLandmark.inFrameLikelihood).thenReturn(confidence)
        
        return mockLandmark
    }
}
package com.grloepr.pushtrack.counting

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

/**
 * Unit tests for PushUpCounter logic
 */
class PushUpCounterTest {
    
    private lateinit var pushUpCounter: PushUpCounter
    
    @Before
    fun setUp() {
        pushUpCounter = PushUpCounter()
    }
    
    @Test
    fun `initial state should be neutral with zero reps`() = runTest {
        assertEquals(PushUpState.NEUTRAL, pushUpCounter.currentStateFlow.first())
        assertEquals(0, pushUpCounter.repCountFlow.first())
        assertEquals(0, pushUpCounter.getCurrentRepCount())
    }
    
    @Test
    fun `reset counter should clear all state`() = runTest {
        // First simulate some activity
        val mockPose = createMockPose(elbowAngle = 90f, inPlankPosition = true)
        pushUpCounter.processPose(mockPose)
        
        // Reset and verify
        pushUpCounter.resetCounter()
        
        assertEquals(PushUpState.NEUTRAL, pushUpCounter.currentStateFlow.first())
        assertEquals(0, pushUpCounter.repCountFlow.first())
        assertEquals(0, pushUpCounter.getCurrentRepCount())
        assertNull(pushUpCounter.lastMovementFlow.first())
    }
    
    @Test
    fun `should not count reps when arms not visible`() = runTest {
        val mockPose = createMockPose(elbowAngle = 90f, inPlankPosition = true, armsVisible = false)
        
        // Process multiple poses
        repeat(10) {
            pushUpCounter.processPose(mockPose)
        }
        
        assertEquals(0, pushUpCounter.getCurrentRepCount())
        assertEquals(PushUpState.NEUTRAL, pushUpCounter.currentStateFlow.first())
    }
    
    @Test
    fun `should not count reps when not in plank position`() = runTest {
        val mockPose = createMockPose(elbowAngle = 90f, inPlankPosition = false, armsVisible = true)
        
        // Process multiple poses
        repeat(10) {
            pushUpCounter.processPose(mockPose)
        }
        
        assertEquals(0, pushUpCounter.getCurrentRepCount())
        assertEquals(PushUpState.NEUTRAL, pushUpCounter.currentStateFlow.first())
    }
    
    @Test
    fun `complete push-up cycle should increment counter`() = runTest {
        // Simulate complete push-up: up -> down -> up
        val poses = listOf(
            createMockPose(elbowAngle = 170f, inPlankPosition = true, armsVisible = true), // Up position
            createMockPose(elbowAngle = 150f, inPlankPosition = true, armsVisible = true), // Descending
            createMockPose(elbowAngle = 120f, inPlankPosition = true, armsVisible = true), // Descending
            createMockPose(elbowAngle = 90f, inPlankPosition = true, armsVisible = true),  // Down position
            createMockPose(elbowAngle = 80f, inPlankPosition = true, armsVisible = true),  // Down position
            createMockPose(elbowAngle = 100f, inPlankPosition = true, armsVisible = true), // Ascending
            createMockPose(elbowAngle = 130f, inPlankPosition = true, armsVisible = true), // Ascending
            createMockPose(elbowAngle = 160f, inPlankPosition = true, armsVisible = true), // Up position
        )
        
        // Process poses with delays to respect minimum state hold time
        poses.forEach { pose ->
            pushUpCounter.processPose(pose)
            Thread.sleep(350) // Slightly more than minStateHoldTime
        }
        
        assertEquals(1, pushUpCounter.getCurrentRepCount())
        assertNotNull(pushUpCounter.lastMovementFlow.first())
    }
    
    @Test
    fun `partial movement should not count as rep`() = runTest {
        // Simulate partial push-up: up -> halfway down -> up
        val poses = listOf(
            createMockPose(elbowAngle = 170f, inPlankPosition = true, armsVisible = true), // Up position
            createMockPose(elbowAngle = 150f, inPlankPosition = true, armsVisible = true), // Slight descent
            createMockPose(elbowAngle = 130f, inPlankPosition = true, armsVisible = true), // Partial descent
            createMockPose(elbowAngle = 150f, inPlankPosition = true, armsVisible = true), // Back up
            createMockPose(elbowAngle = 170f, inPlankPosition = true, armsVisible = true), // Up position
        )
        
        poses.forEach { pose ->
            pushUpCounter.processPose(pose)
            Thread.sleep(350)
        }
        
        assertEquals(0, pushUpCounter.getCurrentRepCount())
    }
    
    @Test
    fun `multiple complete push-ups should increment counter correctly`() = runTest {
        // Simulate 3 complete push-ups
        repeat(3) {
            val poses = listOf(
                createMockPose(elbowAngle = 170f, inPlankPosition = true, armsVisible = true),
                createMockPose(elbowAngle = 130f, inPlankPosition = true, armsVisible = true),
                createMockPose(elbowAngle = 85f, inPlankPosition = true, armsVisible = true),
                createMockPose(elbowAngle = 120f, inPlankPosition = true, armsVisible = true),
                createMockPose(elbowAngle = 165f, inPlankPosition = true, armsVisible = true),
            )
            
            poses.forEach { pose ->
                pushUpCounter.processPose(pose)
                Thread.sleep(350)
            }
        }
        
        assertEquals(3, pushUpCounter.getCurrentRepCount())
    }
    
    /**
     * Helper function to create mock poses for testing
     */
    private fun createMockPose(
        elbowAngle: Float,
        inPlankPosition: Boolean,
        armsVisible: Boolean = true
    ): Pose {
        val mockPose = mock(Pose::class.java)
        
        // Mock landmarks for elbow angle calculation
        if (armsVisible) {
            val leftShoulder = createMockLandmark(100f, 100f, 0.8f)
            val leftElbow = createMockLandmark(120f, 120f, 0.8f)
            val leftWrist = createMockLandmark(140f, 140f, 0.8f)
            
            `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)).thenReturn(leftShoulder)
            `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)).thenReturn(leftElbow)
            `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_WRIST)).thenReturn(leftWrist)
            
            // Mock right arm landmarks for completeness
            val rightShoulder = createMockLandmark(200f, 100f, 0.8f)
            val rightElbow = createMockLandmark(180f, 120f, 0.8f)
            val rightWrist = createMockLandmark(160f, 140f, 0.8f)
            
            `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)).thenReturn(rightShoulder)
            `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)).thenReturn(rightElbow)
            `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)).thenReturn(rightWrist)
        } else {
            // Return null or low confidence landmarks
            `when`(mockPose.getPoseLandmark(any())).thenReturn(null)
        }
        
        // Mock landmarks for torso angle (plank position) calculation
        if (inPlankPosition) {
            val leftHip = createMockLandmark(110f, 200f, 0.8f)
            val rightHip = createMockLandmark(190f, 200f, 0.8f)
            
            `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_HIP)).thenReturn(leftHip)
            `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_HIP)).thenReturn(rightHip)
        } else {
            // Mock landmarks that would indicate non-plank position
            val leftHip = createMockLandmark(110f, 250f, 0.8f) // More vertical position
            val rightHip = createMockLandmark(190f, 250f, 0.8f)
            
            `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_HIP)).thenReturn(leftHip)
            `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_HIP)).thenReturn(rightHip)
        }
        
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
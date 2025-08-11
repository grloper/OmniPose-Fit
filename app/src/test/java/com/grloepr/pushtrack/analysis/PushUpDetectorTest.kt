package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PushUpDetectorTest {
    
    private lateinit var pushUpDetector: PushUpDetector
    
    @Before
    fun setUp() {
        pushUpDetector = PushUpDetector()
    }
    
    @Test
    fun `initial state should have zero reps`() {
        assertEquals(0, pushUpDetector.getRepCount())
        assertEquals(PushUpState.UNKNOWN, pushUpDetector.getCurrentState())
    }
    
    @Test
    fun `should detect down position with small arm angle`() {
        val pose = createMockPoseWithArmAngle(80.0) // Less than down threshold (90)
        
        val repCount = pushUpDetector.processPose(pose)
        
        assertEquals(0, repCount) // No rep counted yet
        assertEquals(PushUpState.DOWN_POSITION, pushUpDetector.getCurrentState())
    }
    
    @Test
    fun `should detect up position with large arm angle`() {
        val pose = createMockPoseWithArmAngle(170.0) // Greater than up threshold (160)
        
        val repCount = pushUpDetector.processPose(pose)
        
        assertEquals(0, repCount) // No rep counted yet
        assertEquals(PushUpState.UP_POSITION, pushUpDetector.getCurrentState())
    }
    
    @Test
    fun `should count rep when transitioning from down to up`() {
        // First, go to down position
        val downPose = createMockPoseWithArmAngle(80.0)
        pushUpDetector.processPose(downPose)
        assertEquals(PushUpState.DOWN_POSITION, pushUpDetector.getCurrentState())
        
        // Then, go to up position - should count a rep
        val upPose = createMockPoseWithArmAngle(170.0)
        val repCount = pushUpDetector.processPose(upPose)
        
        assertEquals(1, repCount)
        assertEquals(PushUpState.UP_POSITION, pushUpDetector.getCurrentState())
    }
    
    @Test
    fun `should count multiple reps correctly`() {
        // Complete first rep: down -> up
        pushUpDetector.processPose(createMockPoseWithArmAngle(80.0))
        assertEquals(0, pushUpDetector.processPose(createMockPoseWithArmAngle(170.0)))
        
        // Complete second rep: down -> up
        pushUpDetector.processPose(createMockPoseWithArmAngle(80.0))
        assertEquals(1, pushUpDetector.processPose(createMockPoseWithArmAngle(170.0)))
        
        // Complete third rep: down -> up
        pushUpDetector.processPose(createMockPoseWithArmAngle(80.0))
        assertEquals(2, pushUpDetector.processPose(createMockPoseWithArmAngle(170.0)))
        
        assertEquals(2, pushUpDetector.getRepCount())
    }
    
    @Test
    fun `should reset counter correctly`() {
        // Count some reps first
        pushUpDetector.processPose(createMockPoseWithArmAngle(80.0))
        pushUpDetector.processPose(createMockPoseWithArmAngle(170.0))
        assertEquals(1, pushUpDetector.getRepCount())
        
        // Reset
        pushUpDetector.reset()
        
        assertEquals(0, pushUpDetector.getRepCount())
        assertEquals(PushUpState.UNKNOWN, pushUpDetector.getCurrentState())
    }
    
    @Test
    fun `should not count rep when transitioning from up to down`() {
        // Start in up position
        pushUpDetector.processPose(createMockPoseWithArmAngle(170.0))
        assertEquals(PushUpState.UP_POSITION, pushUpDetector.getCurrentState())
        
        // Go to down position - should not count a rep
        val repCount = pushUpDetector.processPose(createMockPoseWithArmAngle(80.0))
        
        assertEquals(0, repCount)
        assertEquals(PushUpState.DOWN_POSITION, pushUpDetector.getCurrentState())
    }
    
    @Test
    fun `should handle missing landmarks gracefully`() {
        val pose = mockk<Pose>()
        every { pose.getPoseLandmark(any()) } returns null
        
        val repCount = pushUpDetector.processPose(pose)
        
        // Should not crash and should not count reps
        assertEquals(0, repCount)
    }
    
    @Test
    fun `processPoseWithAnalysis should return enhanced result`() {
        val pose = createMockPoseWithArmAngle(80.0) // Down position
        
        val result = pushUpDetector.processPoseWithAnalysis(pose)
        
        assertNotNull(result)
        assertEquals(0, result.repCount) // No reps yet
        assertEquals(PushUpState.DOWN_POSITION, result.currentState)
        assertNotNull(result.postureAnalysis)
    }
    
    @Test
    fun `should count rep with enhanced analysis`() {
        // First, go to down position
        val downPose = createMockPoseWithArmAngle(80.0)
        var result = pushUpDetector.processPoseWithAnalysis(downPose)
        assertEquals(PushUpState.DOWN_POSITION, result.currentState)
        
        // Then, go to up position - should count a rep
        val upPose = createMockPoseWithArmAngle(170.0)
        result = pushUpDetector.processPoseWithAnalysis(upPose)
        
        assertEquals(1, result.repCount)
        assertEquals(PushUpState.UP_POSITION, result.currentState)
        assertNotNull(result.postureAnalysis)
    }
    
    @Test
    fun `reset should clear enhanced state`() {
        // Process some poses to build state
        val downPose = createMockPoseWithArmAngle(80.0)
        val upPose = createMockPoseWithArmAngle(170.0)
        
        pushUpDetector.processPoseWithAnalysis(downPose)
        pushUpDetector.processPoseWithAnalysis(upPose)
        
        pushUpDetector.reset()
        
        val result = pushUpDetector.processPoseWithAnalysis(downPose)
        assertEquals(0, result.repCount)
        assertEquals(PushUpState.DOWN_POSITION, result.currentState)
    }
    
    private fun createMockPoseWithArmAngle(targetAngle: Double): Pose {
        val pose = mockk<Pose>()
        
        // Create mock landmarks with high confidence
        val leftShoulder = createMockLandmark(100f, 100f, 0.9f)
        val leftElbow = createMockLandmark(150f, 150f, 0.9f)
        val rightShoulder = createMockLandmark(200f, 100f, 0.9f)
        val rightElbow = createMockLandmark(250f, 150f, 0.9f)
        
        // Calculate wrist positions based on target angle
        val wristDistance = 50f
        val angleRad = Math.toRadians(targetAngle)
        
        val leftWrist = createMockLandmark(
            150f + (wristDistance * cos(angleRad)).toFloat(),
            150f + (wristDistance * sin(angleRad)).toFloat(),
            0.9f
        )
        
        val rightWrist = createMockLandmark(
            250f + (wristDistance * cos(angleRad)).toFloat(),
            150f + (wristDistance * sin(angleRad)).toFloat(),
            0.9f
        )
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns leftShoulder
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW) } returns leftElbow
        every { pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) } returns leftWrist
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) } returns rightShoulder
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW) } returns rightElbow
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) } returns rightWrist
        
        return pose
    }
    
    private fun createMockLandmark(x: Float, y: Float, confidence: Float): PoseLandmark {
        val landmark = mockk<PoseLandmark>()
        val position = mockk<com.google.mlkit.vision.common.PointF>()
        
        every { position.x } returns x
        every { position.y } returns y
        every { landmark.position } returns position
        every { landmark.inFrameLikelihood } returns confidence
        
        return landmark
    }
}
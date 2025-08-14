package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ModularPushUpDetectorTest {
    
    private lateinit var pushUpDetector: PushUpDetector
    
    @Before
    fun setUp() {
        pushUpDetector = PushUpDetector()
    }
    
    @Test
    fun `initial state should have zero reps and up phase`() {
        assertEquals(0, pushUpDetector.getRepCount())
        assertEquals(ExercisePhase.UP, pushUpDetector.getCurrentPhase())
        assertEquals(ExerciseType.PUSH_UP, pushUpDetector.exerciseType)
    }
    
    @Test
    fun `should detect down position with small arm angle`() {
        val pose = createMockPoseWithArmAngle(80.0) // Less than down threshold (90)
        
        repeat(3) { pushUpDetector.processPose(pose) } // Need confirmation frames
        
        assertEquals(ExercisePhase.DOWN, pushUpDetector.getCurrentPhase())
        assertEquals(0, pushUpDetector.getRepCount()) // No rep counted yet
    }
    
    @Test
    fun `should detect up position with large arm angle`() {
        val pose = createMockPoseWithArmAngle(170.0) // Greater than up threshold (160)
        
        repeat(3) { pushUpDetector.processPose(pose) }
        
        assertEquals(ExercisePhase.UP, pushUpDetector.getCurrentPhase())
        assertEquals(0, pushUpDetector.getRepCount()) // No rep counted yet
    }
    
    @Test
    fun `should count rep when transitioning from down to up`() {
        // First, go to down position
        val downPose = createMockPoseWithArmAngle(80.0)
        repeat(3) { pushUpDetector.processPose(downPose) }
        assertEquals(ExercisePhase.DOWN, pushUpDetector.getCurrentPhase())
        
        // Then, go to up position - should count a rep
        val upPose = createMockPoseWithArmAngle(170.0)
        repeat(3) { pushUpDetector.processPose(upPose) }
        
        assertEquals(1, pushUpDetector.getRepCount())
        assertEquals(ExercisePhase.UP, pushUpDetector.getCurrentPhase())
    }
    
    @Test
    fun `should count multiple reps correctly`() {
        // Complete first rep: down -> up
        repeat(3) { pushUpDetector.processPose(createMockPoseWithArmAngle(80.0)) }
        repeat(3) { pushUpDetector.processPose(createMockPoseWithArmAngle(170.0)) }
        assertEquals(1, pushUpDetector.getRepCount())
        
        // Complete second rep: down -> up
        repeat(3) { pushUpDetector.processPose(createMockPoseWithArmAngle(80.0)) }
        repeat(3) { pushUpDetector.processPose(createMockPoseWithArmAngle(170.0)) }
        assertEquals(2, pushUpDetector.getRepCount())
    }
    
    @Test
    fun `should reset counter correctly`() {
        // Count some reps first
        repeat(3) { pushUpDetector.processPose(createMockPoseWithArmAngle(80.0)) }
        repeat(3) { pushUpDetector.processPose(createMockPoseWithArmAngle(170.0)) }
        assertEquals(1, pushUpDetector.getRepCount())
        
        // Reset
        pushUpDetector.reset()
        
        assertEquals(0, pushUpDetector.getRepCount())
        assertEquals(ExercisePhase.UP, pushUpDetector.getCurrentPhase())
    }
    
    @Test
    fun `should handle missing landmarks gracefully`() {
        val pose = mockk<Pose>()
        every { pose.getPoseLandmark(any()) } returns null
        
        pushUpDetector.processPose(pose)
        
        // Should not crash and should indicate not tracking
        assertFalse(pushUpDetector.state.value.isTracking)
    }
    
    @Test
    fun `should calculate confidence correctly`() {
        val pose = createMockPoseWithArmAngle(80.0)
        
        pushUpDetector.processPose(pose)
        
        val confidence = pushUpDetector.state.value.confidence
        assertTrue("Confidence should be positive", confidence > 0f)
        assertTrue("Confidence should not exceed 1.0", confidence <= 1f)
    }
    
    @Test
    fun `should use elbow angle detection method`() {
        val pose = createMockPoseWithArmAngle(80.0)
        
        pushUpDetector.processPose(pose)
        
        assertEquals("elbow_angle", pushUpDetector.state.value.detectionMethod)
    }
    
    @Test
    fun `should adapt to fast movements`() {
        // Create poses with fast movement (large angle changes)
        val downPose = createMockPoseWithArmAngle(80.0)
        val upPose = createMockPoseWithArmAngle(170.0)
        
        // Alternate quickly between positions
        pushUpDetector.processPose(downPose)
        pushUpDetector.processPose(upPose)
        pushUpDetector.processPose(downPose)
        pushUpDetector.processPose(upPose)
        
        // Should handle fast movements and eventually count a rep
        // The adaptive thresholds should reduce confirmation requirements
        val finalCount = pushUpDetector.getRepCount()
        assertTrue("Should count at least one rep with fast movements", finalCount >= 1)
    }
    
    @Test
    fun `state should contain all required information`() {
        val pose = createMockPoseWithArmAngle(80.0)
        
        pushUpDetector.processPose(pose)
        
        val state = pushUpDetector.state.value
        assertNotNull(state.primaryAngle)
        assertTrue(state.confidence >= 0f)
        assertTrue(state.isTracking)
        assertEquals("elbow_angle", state.detectionMethod)
        assertEquals(ExercisePhase.TRANSITIONING, state.phase) // Initially transitioning
    }
    
    @Test
    fun instantiate() {
        val d = PushUpDetector()
        assertEquals(ExerciseType.PUSH_UP, d.exerciseType)
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
        val position = mockk<android.graphics.PointF>()
        every { position.x } returns x
        every { position.y } returns y
        every { landmark.position } returns position
        every { landmark.inFrameLikelihood } returns confidence
        
        return landmark
    }
}
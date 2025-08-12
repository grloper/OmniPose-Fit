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

class PullUpDetectorTest {
    
    private lateinit var pullUpDetector: PullUpDetector
    
    @Before
    fun setUp() {
        pullUpDetector = PullUpDetector()
    }
    
    @Test
    fun `initial state should have zero reps and up phase`() {
        assertEquals(0, pullUpDetector.getRepCount())
        assertEquals(ExercisePhase.UP, pullUpDetector.getCurrentPhase())
        assertEquals(ExerciseType.PULL_UP, pullUpDetector.exerciseType)
    }
    
    @Test
    fun `should detect hanging position with large elbow angle`() {
        val pose = createMockPoseWithElbowAngle(150.0) // Arms extended = hanging
        
        pullUpDetector.processPose(pose)
        
        assertEquals(ExercisePhase.DOWN, pullUpDetector.getCurrentPhase()) // Hanging is DOWN phase for pull-ups
        assertEquals(0, pullUpDetector.getRepCount()) // No rep counted yet
    }
    
    @Test
    fun `should detect pulled up position with small elbow angle`() {
        val pose = createMockPoseWithElbowAngle(50.0) // Arms bent = pulled up
        
        pullUpDetector.processPose(pose)
        
        assertEquals(ExercisePhase.UP, pullUpDetector.getCurrentPhase()) // Pulled up is UP phase
        assertEquals(0, pullUpDetector.getRepCount()) // No rep counted yet
    }
    
    @Test
    fun `should count rep when transitioning from hanging to pulled up`() {
        // First, go to hanging position (DOWN)
        val hangingPose = createMockPoseWithElbowAngle(150.0)
        repeat(3) { pullUpDetector.processPose(hangingPose) } // Need multiple frames for confirmation
        assertEquals(ExercisePhase.DOWN, pullUpDetector.getCurrentPhase())
        
        // Then, pull up (UP) - should count a rep
        val pulledUpPose = createMockPoseWithElbowAngle(50.0)
        repeat(3) { pullUpDetector.processPose(pulledUpPose) }
        
        assertEquals(1, pullUpDetector.getRepCount())
        assertEquals(ExercisePhase.UP, pullUpDetector.getCurrentPhase())
    }
    
    @Test
    fun `should count multiple reps correctly`() {
        // Complete first rep: hanging -> pulled up
        repeat(3) { pullUpDetector.processPose(createMockPoseWithElbowAngle(150.0)) }
        repeat(3) { pullUpDetector.processPose(createMockPoseWithElbowAngle(50.0)) }
        assertEquals(1, pullUpDetector.getRepCount())
        
        // Complete second rep: hanging -> pulled up
        repeat(3) { pullUpDetector.processPose(createMockPoseWithElbowAngle(150.0)) }
        repeat(3) { pullUpDetector.processPose(createMockPoseWithElbowAngle(50.0)) }
        assertEquals(2, pullUpDetector.getRepCount())
    }
    
    @Test
    fun `should reset counter correctly`() {
        // Count some reps first
        repeat(3) { pullUpDetector.processPose(createMockPoseWithElbowAngle(150.0)) }
        repeat(3) { pullUpDetector.processPose(createMockPoseWithElbowAngle(50.0)) }
        assertEquals(1, pullUpDetector.getRepCount())
        
        // Reset
        pullUpDetector.reset()
        
        assertEquals(0, pullUpDetector.getRepCount())
        assertEquals(ExercisePhase.UP, pullUpDetector.getCurrentPhase())
    }
    
    @Test
    fun `should handle missing landmarks gracefully`() {
        val pose = mockk<Pose>()
        every { pose.getPoseLandmark(any()) } returns null
        
        pullUpDetector.processPose(pose)
        
        // Should not crash and should indicate not tracking
        assertFalse(pullUpDetector.state.value.isTracking)
    }
    
    @Test
    fun `should use head to hands distance when elbow angle not available`() {
        // Create pose with head and wrists but no elbow data
        val pose = mockk<Pose>()
        
        // Mock head (nose) position
        val nose = createMockLandmark(200f, 100f, 0.9f) // Head high
        every { pose.getPoseLandmark(PoseLandmark.NOSE) } returns nose
        
        // Mock wrist positions (hands)
        val leftWrist = createMockLandmark(150f, 50f, 0.9f) // Hand higher than head
        val rightWrist = createMockLandmark(250f, 50f, 0.9f)
        every { pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) } returns leftWrist
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) } returns rightWrist
        
        // No elbow data
        every { pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns null
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW) } returns null
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) } returns null
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW) } returns null
        
        repeat(3) { pullUpDetector.processPose(pose) }
        
        // Should detect hanging position (head below hands)
        assertEquals(ExercisePhase.DOWN, pullUpDetector.getCurrentPhase())
        assertEquals("head_hands_distance", pullUpDetector.state.value.detectionMethod)
    }
    
    @Test
    fun `should calculate confidence correctly`() {
        val pose = createMockPoseWithElbowAngle(50.0)
        
        pullUpDetector.processPose(pose)
        
        val confidence = pullUpDetector.state.value.confidence
        assertTrue("Confidence should be positive", confidence > 0f)
        assertTrue("Confidence should not exceed 1.0", confidence <= 1f)
    }
    
    private fun createMockPoseWithElbowAngle(targetAngle: Double): Pose {
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
        
        // Mock head for distance calculation
        val nose = createMockLandmark(175f, 200f, 0.9f)
        every { pose.getPoseLandmark(PoseLandmark.NOSE) } returns nose
        
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
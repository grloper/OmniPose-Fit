package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PullUpDetectorTest {
    
    private lateinit var pullUpDetector: PullUpDetector
    
    @Before
    fun setUp() {
        pullUpDetector = PullUpDetector()
    }
    
    @Test
    fun `initial state should have zero reps`() {
        assertEquals(0, pullUpDetector.repCount)
        assertEquals(ExerciseState.UNKNOWN, pullUpDetector.currentState)
    }
    
    @Test
    fun `should detect top position with small elbow angle`() {
        val pose = createMockPoseWithElbowAngle(40.0) // Less than top threshold (50)
        
        val repCount = pullUpDetector.processPose(pose)
        
        assertEquals(0, repCount) // No rep counted yet
        assertEquals(ExerciseState.START_POSITION, pullUpDetector.currentState)
    }
    
    @Test
    fun `should detect bottom position with large elbow angle`() {
        val pose = createMockPoseWithElbowAngle(170.0) // Greater than bottom threshold (160)
        
        val repCount = pullUpDetector.processPose(pose)
        
        assertEquals(0, repCount) // No rep counted yet
        assertEquals(ExerciseState.END_POSITION, pullUpDetector.currentState)
    }
    
    @Test
    fun `should count rep when transitioning from bottom to top`() {
        // First, go to bottom position (elbows extended)
        val bottomPose = createMockPoseWithElbowAngle(170.0)
        pullUpDetector.processPose(bottomPose)
        assertEquals(ExerciseState.END_POSITION, pullUpDetector.currentState)
        
        // Then, go to top position (elbows bent) - should count a rep
        val topPose = createMockPoseWithElbowAngle(40.0)
        val repCount = pullUpDetector.processPose(topPose)
        
        assertEquals(1, repCount)
        assertEquals(ExerciseState.START_POSITION, pullUpDetector.currentState)
    }
    
    @Test
    fun `should count multiple reps correctly`() {
        // Complete first rep: bottom -> top
        pullUpDetector.processPose(createMockPoseWithElbowAngle(170.0))
        assertEquals(1, pullUpDetector.processPose(createMockPoseWithElbowAngle(40.0)))
        
        // Complete second rep: bottom -> top
        pullUpDetector.processPose(createMockPoseWithElbowAngle(170.0))
        assertEquals(2, pullUpDetector.processPose(createMockPoseWithElbowAngle(40.0)))
        
        assertEquals(2, pullUpDetector.repCount)
    }
    
    @Test
    fun `should reset counter correctly`() {
        // Count some reps first
        pullUpDetector.processPose(createMockPoseWithElbowAngle(170.0))
        pullUpDetector.processPose(createMockPoseWithElbowAngle(40.0))
        assertEquals(1, pullUpDetector.repCount)
        
        // Reset
        pullUpDetector.reset()
        
        assertEquals(0, pullUpDetector.repCount)
        assertEquals(ExerciseState.UNKNOWN, pullUpDetector.currentState)
    }
    
    @Test
    fun `should handle missing landmarks gracefully`() {
        val pose = mockk<Pose>()
        every { pose.getPoseLandmark(any()) } returns null
        
        val repCount = pullUpDetector.processPose(pose)
        
        // Should not crash and should not count reps
        assertEquals(0, repCount)
    }
    
    @Test
    fun `processPoseWithAnalysis should return enhanced result`() {
        val pose = createMockPoseWithElbowAngle(170.0) // Bottom position (elbows extended)
        
        val result = pullUpDetector.processPoseWithAnalysis(pose)
        
        assertNotNull(result)
        assertEquals(0, result.repCount) // No reps yet
        assertEquals(ExerciseState.END_POSITION, result.currentState)
        assertEquals(ExerciseType.PULL_UP, result.exerciseType)
        assertNotNull(result.postureAnalysis)
    }
    
    @Test
    fun `should not count rep when transitioning from top to bottom`() {
        // Start in top position (elbows bent)
        pullUpDetector.processPose(createMockPoseWithElbowAngle(40.0))
        assertEquals(ExerciseState.START_POSITION, pullUpDetector.currentState)
        
        // Go to bottom position (elbows extended) - should not count a rep
        val repCount = pullUpDetector.processPose(createMockPoseWithElbowAngle(170.0))
        
        assertEquals(0, repCount)
        assertEquals(ExerciseState.END_POSITION, pullUpDetector.currentState)
    }
    
    private fun createMockPoseWithElbowAngle(angle: Double): Pose {
        val pose = mockk<Pose>()
        
        // Create landmarks that will produce the desired elbow angle
        // For simplicity, we'll create a right angle scenario and adjust the wrist position
        val leftShoulder = createMockLandmark(100f, 100f, 0.9f)
        val leftElbow = createMockLandmark(100f, 150f, 0.9f) // 50 units below shoulder
        val rightShoulder = createMockLandmark(200f, 100f, 0.9f)  
        val rightElbow = createMockLandmark(200f, 150f, 0.9f) // 50 units below shoulder
        
        // Calculate wrist position to achieve desired angle
        val angleRad = Math.toRadians(angle)
        val elbowWristDistance = 50f // Fixed distance from elbow to wrist
        
        // For left arm: elbow is at (100, 150), calculate wrist position for desired angle
        val leftWristX = leftElbow.position.x + (elbowWristDistance * Math.cos(angleRad)).toFloat()
        val leftWristY = leftElbow.position.y + (elbowWristDistance * Math.sin(angleRad)).toFloat()
        val leftWrist = createMockLandmark(leftWristX, leftWristY, 0.9f)
        
        // For right arm: mirror the calculation
        val rightWristX = rightElbow.position.x + (elbowWristDistance * Math.cos(angleRad)).toFloat()
        val rightWristY = rightElbow.position.y + (elbowWristDistance * Math.sin(angleRad)).toFloat()
        val rightWrist = createMockLandmark(rightWristX, rightWristY, 0.9f)
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns leftShoulder
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW) } returns leftElbow
        every { pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) } returns leftWrist
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) } returns rightShoulder
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW) } returns rightElbow
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) } returns rightWrist
        
        return pose
    }
    
    private fun createMockPoseWithShoulderWristDistance(distance: Float): Pose {
        val pose = mockk<Pose>()
        
        // Create mock landmarks with high confidence
        val leftShoulder = createMockLandmark(100f, 100f, 0.9f)
        val leftWrist = createMockLandmark(100f, 100f + distance, 0.9f) // Distance below shoulder
        val rightShoulder = createMockLandmark(200f, 100f, 0.9f)
        val rightWrist = createMockLandmark(200f, 100f + distance, 0.9f) // Distance below shoulder
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns leftShoulder
        every { pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) } returns leftWrist
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) } returns rightShoulder
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
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
        assertEquals(0, pullUpDetector.getRepCount())
        assertEquals(ExerciseState.UNKNOWN, pullUpDetector.getCurrentState())
    }
    
    @Test
    fun `should detect top position with small shoulder-wrist distance`() {
        val pose = createMockPoseWithShoulderWristDistance(20.0f) // Less than top threshold (30)
        
        val repCount = pullUpDetector.processPose(pose)
        
        assertEquals(0, repCount) // No rep counted yet
        assertEquals(ExerciseState.START_POSITION, pullUpDetector.getCurrentState())
    }
    
    @Test
    fun `should detect bottom position with large shoulder-wrist distance`() {
        val pose = createMockPoseWithShoulderWristDistance(100.0f) // Greater than bottom threshold (80)
        
        val repCount = pullUpDetector.processPose(pose)
        
        assertEquals(0, repCount) // No rep counted yet
        assertEquals(ExerciseState.END_POSITION, pullUpDetector.getCurrentState())
    }
    
    @Test
    fun `should count rep when transitioning from bottom to top`() {
        // First, go to bottom position (hanging)
        val bottomPose = createMockPoseWithShoulderWristDistance(100.0f)
        pullUpDetector.processPose(bottomPose)
        assertEquals(ExerciseState.END_POSITION, pullUpDetector.getCurrentState())
        
        // Then, go to top position (chin up) - should count a rep
        val topPose = createMockPoseWithShoulderWristDistance(20.0f)
        val repCount = pullUpDetector.processPose(topPose)
        
        assertEquals(1, repCount)
        assertEquals(ExerciseState.START_POSITION, pullUpDetector.getCurrentState())
    }
    
    @Test
    fun `should count multiple reps correctly`() {
        // Complete first rep: bottom -> top
        pullUpDetector.processPose(createMockPoseWithShoulderWristDistance(100.0f))
        assertEquals(1, pullUpDetector.processPose(createMockPoseWithShoulderWristDistance(20.0f)))
        
        // Complete second rep: bottom -> top
        pullUpDetector.processPose(createMockPoseWithShoulderWristDistance(100.0f))
        assertEquals(2, pullUpDetector.processPose(createMockPoseWithShoulderWristDistance(20.0f)))
        
        assertEquals(2, pullUpDetector.getRepCount())
    }
    
    @Test
    fun `should reset counter correctly`() {
        // Count some reps first
        pullUpDetector.processPose(createMockPoseWithShoulderWristDistance(100.0f))
        pullUpDetector.processPose(createMockPoseWithShoulderWristDistance(20.0f))
        assertEquals(1, pullUpDetector.getRepCount())
        
        // Reset
        pullUpDetector.reset()
        
        assertEquals(0, pullUpDetector.getRepCount())
        assertEquals(ExerciseState.UNKNOWN, pullUpDetector.getCurrentState())
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
        val pose = createMockPoseWithShoulderWristDistance(100.0f) // Bottom position
        
        val result = pullUpDetector.processPoseWithAnalysis(pose)
        
        assertNotNull(result)
        assertEquals(0, result.repCount) // No reps yet
        assertEquals(ExerciseState.END_POSITION, result.currentState)
        assertEquals(ExerciseType.PULL_UP, result.exerciseType)
        assertNotNull(result.postureAnalysis)
    }
    
    @Test
    fun `should not count rep when transitioning from top to bottom`() {
        // Start in top position
        pullUpDetector.processPose(createMockPoseWithShoulderWristDistance(20.0f))
        assertEquals(ExerciseState.START_POSITION, pullUpDetector.getCurrentState())
        
        // Go to bottom position - should not count a rep
        val repCount = pullUpDetector.processPose(createMockPoseWithShoulderWristDistance(100.0f))
        
        assertEquals(0, repCount)
        assertEquals(ExerciseState.END_POSITION, pullUpDetector.getCurrentState())
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
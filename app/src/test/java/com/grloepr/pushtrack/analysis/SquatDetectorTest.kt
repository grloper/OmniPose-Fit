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

class SquatDetectorTest {
    
    private lateinit var squatDetector: SquatDetector
    
    @Before
    fun setUp() {
        squatDetector = SquatDetector()
    }
    
    @Test
    fun `initial state should have zero reps`() {
        assertEquals(0, squatDetector.getRepCount())
        assertEquals(ExerciseState.UNKNOWN, squatDetector.getCurrentState())
    }
    
    @Test
    fun `should detect squat down position with small knee angle`() {
        val pose = createMockPoseWithKneeAngle(80.0) // Less than down threshold (90)
        
        val repCount = squatDetector.processPose(pose)
        
        assertEquals(0, repCount) // No rep counted yet
        assertEquals(ExerciseState.END_POSITION, squatDetector.getCurrentState())
    }
    
    @Test
    fun `should detect standing position with large knee angle`() {
        val pose = createMockPoseWithKneeAngle(170.0) // Greater than up threshold (160)
        
        val repCount = squatDetector.processPose(pose)
        
        assertEquals(0, repCount) // No rep counted yet
        assertEquals(ExerciseState.START_POSITION, squatDetector.getCurrentState())
    }
    
    @Test
    fun `should count rep when transitioning from squat to standing`() {
        // First, go to squat position
        val squatPose = createMockPoseWithKneeAngle(80.0)
        squatDetector.processPose(squatPose)
        assertEquals(ExerciseState.END_POSITION, squatDetector.getCurrentState())
        
        // Then, go to standing position - should count a rep
        val standPose = createMockPoseWithKneeAngle(170.0)
        val repCount = squatDetector.processPose(standPose)
        
        assertEquals(1, repCount)
        assertEquals(ExerciseState.START_POSITION, squatDetector.getCurrentState())
    }
    
    @Test
    fun `should count multiple reps correctly`() {
        // Complete first rep: squat -> stand
        squatDetector.processPose(createMockPoseWithKneeAngle(80.0))
        assertEquals(1, squatDetector.processPose(createMockPoseWithKneeAngle(170.0)))
        
        // Complete second rep: squat -> stand
        squatDetector.processPose(createMockPoseWithKneeAngle(80.0))
        assertEquals(2, squatDetector.processPose(createMockPoseWithKneeAngle(170.0)))
        
        assertEquals(2, squatDetector.getRepCount())
    }
    
    @Test
    fun `should reset counter correctly`() {
        // Count some reps first
        squatDetector.processPose(createMockPoseWithKneeAngle(80.0))
        squatDetector.processPose(createMockPoseWithKneeAngle(170.0))
        assertEquals(1, squatDetector.getRepCount())
        
        // Reset
        squatDetector.reset()
        
        assertEquals(0, squatDetector.getRepCount())
        assertEquals(ExerciseState.UNKNOWN, squatDetector.getCurrentState())
    }
    
    @Test
    fun `should handle missing landmarks gracefully`() {
        val pose = mockk<Pose>()
        every { pose.getPoseLandmark(any()) } returns null
        
        val repCount = squatDetector.processPose(pose)
        
        // Should not crash and should not count reps
        assertEquals(0, repCount)
    }
    
    @Test
    fun `processPoseWithAnalysis should return enhanced result`() {
        val pose = createMockPoseWithKneeAngle(80.0) // Squat position
        
        val result = squatDetector.processPoseWithAnalysis(pose)
        
        assertNotNull(result)
        assertEquals(0, result.repCount) // No reps yet
        assertEquals(ExerciseState.END_POSITION, result.currentState)
        assertEquals(ExerciseType.SQUAT, result.exerciseType)
        assertNotNull(result.postureAnalysis)
    }
    
    private fun createMockPoseWithKneeAngle(targetAngle: Double): Pose {
        val pose = mockk<Pose>()
        
        // Create mock landmarks with high confidence
        val leftHip = createMockLandmark(100f, 100f, 0.9f)
        val leftKnee = createMockLandmark(100f, 200f, 0.9f)
        val rightHip = createMockLandmark(200f, 100f, 0.9f)
        val rightKnee = createMockLandmark(200f, 200f, 0.9f)
        
        // Calculate ankle positions based on target angle
        val legDistance = 100f
        val angleRad = Math.toRadians(targetAngle)
        
        val leftAnkle = createMockLandmark(
            100f + (legDistance * cos(angleRad)).toFloat(),
            200f + (legDistance * sin(angleRad)).toFloat(),
            0.9f
        )
        
        val rightAnkle = createMockLandmark(
            200f + (legDistance * cos(angleRad)).toFloat(),
            200f + (legDistance * sin(angleRad)).toFloat(),
            0.9f
        )
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_HIP) } returns leftHip
        every { pose.getPoseLandmark(PoseLandmark.LEFT_KNEE) } returns leftKnee
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE) } returns leftAnkle
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) } returns rightHip
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) } returns rightKnee
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE) } returns rightAnkle
        
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
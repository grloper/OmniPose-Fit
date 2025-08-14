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

class SquatDetectorTest {
    
    private lateinit var squatDetector: SquatDetector
    
    @Before
    fun setUp() {
        squatDetector = SquatDetector()
    }
    
    @Test
    fun `initial state should have zero reps and up phase`() {
        assertEquals(0, squatDetector.getRepCount())
        assertEquals(ExercisePhase.UP, squatDetector.getCurrentPhase())
        assertEquals(ExerciseType.SQUAT, squatDetector.exerciseType)
    }
    
    @Test
    fun `should detect standing position with large knee angle`() {
        val pose = createMockPoseWithKneeAngle(170.0) // Legs straight = standing
        
        repeat(3) { squatDetector.processPose(pose) }
        
        assertEquals(ExercisePhase.UP, squatDetector.getCurrentPhase())
        assertEquals(0, squatDetector.getRepCount()) // No rep counted yet
    }
    
    @Test
    fun `should detect squatting position with small knee angle`() {
        val pose = createMockPoseWithKneeAngle(100.0) // Knees bent = squatting
        
        repeat(3) { squatDetector.processPose(pose) }
        
        assertEquals(ExercisePhase.DOWN, squatDetector.getCurrentPhase())
        assertEquals(0, squatDetector.getRepCount()) // No rep counted yet
    }
    
    @Test
    fun `should count rep when transitioning from squatting to standing`() {
        // First, go to squatting position (DOWN)
        val squattingPose = createMockPoseWithKneeAngle(100.0)
        repeat(3) { squatDetector.processPose(squattingPose) }
        assertEquals(ExercisePhase.DOWN, squatDetector.getCurrentPhase())
        
        // Then, stand up (UP) - should count a rep
        val standingPose = createMockPoseWithKneeAngle(170.0)
        repeat(3) { squatDetector.processPose(standingPose) }
        
        assertEquals(1, squatDetector.getRepCount())
        assertEquals(ExercisePhase.UP, squatDetector.getCurrentPhase())
    }
    
    @Test
    fun `should count multiple reps correctly`() {
        // Complete first rep: squat -> stand
        repeat(3) { squatDetector.processPose(createMockPoseWithKneeAngle(100.0)) }
        repeat(3) { squatDetector.processPose(createMockPoseWithKneeAngle(170.0)) }
        assertEquals(1, squatDetector.getRepCount())
        
        // Complete second rep: squat -> stand
        repeat(3) { squatDetector.processPose(createMockPoseWithKneeAngle(100.0)) }
        repeat(3) { squatDetector.processPose(createMockPoseWithKneeAngle(170.0)) }
        assertEquals(2, squatDetector.getRepCount())
    }
    
    @Test
    fun `should reset counter correctly`() {
        // Count some reps first
        repeat(3) { squatDetector.processPose(createMockPoseWithKneeAngle(100.0)) }
        repeat(3) { squatDetector.processPose(createMockPoseWithKneeAngle(170.0)) }
        assertEquals(1, squatDetector.getRepCount())
        
        // Reset
        squatDetector.reset()
        
        assertEquals(0, squatDetector.getRepCount())
        assertEquals(ExercisePhase.UP, squatDetector.getCurrentPhase())
    }
    
    @Test
    fun `should handle missing landmarks gracefully`() {
        val pose = mockk<Pose>()
        every { pose.getPoseLandmark(any()) } returns null
        
        squatDetector.processPose(pose)
        
        // Should not crash and should indicate not tracking
        assertFalse(squatDetector.state.value.isTracking)
    }
    
    @Test
    fun `should use hip height when knee angle not available`() {
        // Create pose with hip data but no knee/ankle data
        val pose = mockk<Pose>()
        
        // Mock hip positions (high = standing)
        val leftHip = createMockLandmark(150f, 200f, 0.9f)
        val rightHip = createMockLandmark(250f, 200f, 0.9f)
        every { pose.getPoseLandmark(PoseLandmark.LEFT_HIP) } returns leftHip
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) } returns rightHip
        
        // No knee/ankle data
        every { pose.getPoseLandmark(PoseLandmark.LEFT_KNEE) } returns null
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE) } returns null
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) } returns null
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE) } returns null
        
        // Establish baseline
        repeat(5) { squatDetector.processPose(pose) }
        
        // Now create a pose with lower hip (squatting)
        val lowHipPose = mockk<Pose>()
        val lowLeftHip = createMockLandmark(150f, 300f, 0.9f) // Lower Y = squatting
        val lowRightHip = createMockLandmark(250f, 300f, 0.9f)
        every { lowHipPose.getPoseLandmark(PoseLandmark.LEFT_HIP) } returns lowLeftHip
        every { lowHipPose.getPoseLandmark(PoseLandmark.RIGHT_HIP) } returns lowRightHip
        every { lowHipPose.getPoseLandmark(PoseLandmark.LEFT_KNEE) } returns null
        every { lowHipPose.getPoseLandmark(PoseLandmark.LEFT_ANKLE) } returns null
        every { lowHipPose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) } returns null
        every { lowHipPose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE) } returns null
        
        repeat(3) { squatDetector.processPose(lowHipPose) }
        
        // Should detect squatting position using hip height
        assertEquals(ExercisePhase.DOWN, squatDetector.getCurrentPhase())
        assertEquals("hip_height", squatDetector.state.value.detectionMethod)
    }
    
    @Test
    fun `should calculate confidence correctly`() {
        val pose = createMockPoseWithKneeAngle(100.0)
        
        squatDetector.processPose(pose)
        
        val confidence = squatDetector.state.value.confidence
        assertTrue("Confidence should be positive", confidence > 0f)
        assertTrue("Confidence should not exceed 1.0", confidence <= 1f)
    }
    
    @Test
    fun `should prefer knee angle over hip height when both available`() {
        val pose = createMockPoseWithKneeAngleAndHip(100.0, 200f)
        
        squatDetector.processPose(pose)
        
        assertEquals("knee_angle", squatDetector.state.value.detectionMethod)
    }
    
    @Test
    fun instantiate() {
        val d = SquatDetector()
        assertEquals(ExerciseType.SQUAT, d.exerciseType)
    }
    
    private fun createMockPoseWithKneeAngle(targetAngle: Double): Pose {
        val pose = mockk<Pose>()
        
        // Create mock landmarks for knee angle calculation
        val leftHip = createMockLandmark(150f, 100f, 0.9f)
        val leftKnee = createMockLandmark(150f, 200f, 0.9f)
        val rightHip = createMockLandmark(250f, 100f, 0.9f)
        val rightKnee = createMockLandmark(250f, 200f, 0.9f)
        
        // Calculate ankle positions based on target angle
        val ankleDistance = 100f
        val angleRad = Math.toRadians(targetAngle)
        
        val leftAnkle = createMockLandmark(
            150f + (ankleDistance * cos(angleRad)).toFloat(),
            200f + (ankleDistance * sin(angleRad)).toFloat(),
            0.9f
        )
        
        val rightAnkle = createMockLandmark(
            250f + (ankleDistance * cos(angleRad)).toFloat(),
            200f + (ankleDistance * sin(angleRad)).toFloat(),
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
    
    private fun createMockPoseWithKneeAngleAndHip(targetAngle: Double, hipY: Float): Pose {
        val pose = createMockPoseWithKneeAngle(targetAngle)
        
        // Override hip positions
        val leftHip = createMockLandmark(150f, hipY, 0.9f)
        val rightHip = createMockLandmark(250f, hipY, 0.9f)
        every { pose.getPoseLandmark(PoseLandmark.LEFT_HIP) } returns leftHip
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) } returns rightHip
        
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
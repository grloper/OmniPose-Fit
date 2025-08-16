package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.domain.ExerciseState
import com.grloepr.pushtrack.domain.ExerciseType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
        assertEquals(ExerciseType.SQUAT, squatDetector.exerciseType)
        assertFalse(squatDetector.requiresCalibration())
        assertTrue(squatDetector.isCalibrated())
    }
    
    @Test
    fun `should detect standing position with large hip-knee angle`() {
        val pose = createMockPoseWithLegAngle(170.0) // Greater than standing threshold (160)
        
        val result = squatDetector.processPose(pose)
        
        assertEquals(0, result.repCount) // No rep counted yet
        assertEquals(ExerciseState.START_POSITION, result.currentState)
        assertNotNull(result.formQuality)
        assertEquals(0.8f, result.confidence)
        assertEquals("hip_knee_angle", result.detectionMethod)
    }
    
    @Test
    fun `should detect squat position with small hip-knee angle`() {
        val pose = createMockPoseWithLegAngle(110.0) // Less than squat threshold (120)
        
        val result = squatDetector.processPose(pose)
        
        assertEquals(0, result.repCount) // No rep counted yet
        assertEquals(ExerciseState.END_POSITION, result.currentState)
        assertNotNull(result.formQuality)
        assertTrue("Form quality should be good for deep squat", result.formQuality!!.score >= 80f)
    }
    
    @Test
    fun `should count rep when transitioning from standing to squat`() {
        // First, go to standing position
        val standingPose = createMockPoseWithLegAngle(170.0)
        squatDetector.processPose(standingPose)
        squatDetector.processPose(standingPose) // Confirm standing state
        assertEquals(ExerciseState.START_POSITION, squatDetector.getCurrentState())
        
        // Then, go to squat position - should count a rep
        val squatPose = createMockPoseWithLegAngle(110.0)
        val result = squatDetector.processPose(squatPose)
        squatDetector.processPose(squatPose) // Confirm squat state
        
        assertEquals(1, result.repCount)
        assertEquals(ExerciseState.END_POSITION, result.currentState)
    }
    
    @Test
    fun `should not count rep when transitioning from squat to standing`() {
        // Start in squat position
        val squatPose = createMockPoseWithLegAngle(110.0)
        squatDetector.processPose(squatPose)
        squatDetector.processPose(squatPose) // Confirm squat state
        assertEquals(ExerciseState.END_POSITION, squatDetector.getCurrentState())
        
        // Go to standing position - should not count a rep
        val standingPose = createMockPoseWithLegAngle(170.0)
        val result = squatDetector.processPose(standingPose)
        
        assertEquals(0, result.repCount)
        assertEquals(ExerciseState.START_POSITION, result.currentState)
    }
    
    @Test
    fun `should provide form feedback based on squat depth`() {
        // Good squat depth
        val deepSquatPose = createMockPoseWithLegAngle(100.0)
        val deepResult = squatDetector.processPose(deepSquatPose)
        assertEquals("Good depth!", deepResult.formQuality?.feedback)
        
        // Partial squat
        val partialSquatPose = createMockPoseWithLegAngle(140.0)
        val partialResult = squatDetector.processPose(partialSquatPose)
        assertEquals("Squat deeper", partialResult.formQuality?.feedback)
    }
    
    @Test
    fun `should reset correctly`() {
        // Count some reps first
        val standingPose = createMockPoseWithLegAngle(170.0)
        val squatPose = createMockPoseWithLegAngle(110.0)
        
        squatDetector.processPose(standingPose)
        squatDetector.processPose(standingPose)
        squatDetector.processPose(squatPose)
        squatDetector.processPose(squatPose)
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
        
        val result = squatDetector.processPose(pose)
        
        assertEquals(0, result.repCount)
        assertEquals(ExerciseState.UNKNOWN, result.currentState)
        assertNull(result.formQuality)
        assertEquals(0f, result.confidence)
    }
    
    private fun createMockPoseWithLegAngle(hipKneeAngleDegrees: Double): Pose {
        val pose = mockk<Pose>()
        
        // Create landmarks that form the desired angle
        val angleRadians = Math.toRadians(hipKneeAngleDegrees)
        
        val hip = mockk<PoseLandmark>().apply {
            every { position } returns mockk {
                every { x } returns 150f
                every { y } returns 100f
            }
            every { inFrameLikelihood } returns 0.9f
        }
        
        val knee = mockk<PoseLandmark>().apply {
            every { position } returns mockk {
                every { x } returns 150f
                every { y } returns 200f // Below hip
            }
            every { inFrameLikelihood } returns 0.9f
        }
        
        val ankle = mockk<PoseLandmark>().apply {
            every { position } returns mockk {
                // Calculate ankle position to create desired angle
                val x = 150f + (100f * kotlin.math.cos(angleRadians)).toFloat()
                every { x } returns x
                every { y } returns 300f // Below knee
            }
            every { inFrameLikelihood } returns 0.9f
        }
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_HIP) } returns hip
        every { pose.getPoseLandmark(PoseLandmark.LEFT_KNEE) } returns knee
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE) } returns ankle
        
        // Mock right leg with same values for simplicity
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) } returns hip
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) } returns knee
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE) } returns ankle
        
        return pose
    }
}
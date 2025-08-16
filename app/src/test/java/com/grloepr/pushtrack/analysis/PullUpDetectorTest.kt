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

class PullUpDetectorTest {
    
    private lateinit var pullUpDetector: PullUpDetector
    
    @Before
    fun setUp() {
        pullUpDetector = PullUpDetector()
    }
    
    @Test
    fun `initial state should have zero reps and require calibration`() {
        assertEquals(0, pullUpDetector.getRepCount())
        assertEquals(ExerciseState.UNKNOWN, pullUpDetector.getCurrentState())
        assertEquals(ExerciseType.PULL_UP, pullUpDetector.exerciseType)
        assertTrue(pullUpDetector.requiresCalibration())
        assertFalse(pullUpDetector.isCalibrated())
    }
    
    @Test
    fun `should calibrate successfully with valid pose`() {
        val pose = createMockPoseWithShoulders(100f, 100f)
        
        val result = pullUpDetector.calibrate(pose)
        
        assertTrue(result)
        assertTrue(pullUpDetector.isCalibrated())
    }
    
    @Test
    fun `should not detect reps without calibration`() {
        val pose = createMockPoseWithShoulders(100f, 100f)
        
        val result = pullUpDetector.processPose(pose)
        
        assertEquals(0, result.repCount)
        assertEquals(ExerciseState.UNKNOWN, result.currentState)
        assertEquals(0f, result.confidence)
    }
    
    @Test
    fun `should detect hanging position after calibration`() {
        // First calibrate
        val calibrationPose = createMockPoseWithShoulders(100f, 100f)
        pullUpDetector.calibrate(calibrationPose)
        
        // Then test hanging position (shoulders at baseline)
        val hangingPose = createMockPoseWithShoulders(100f, 100f)
        val result = pullUpDetector.processPose(hangingPose)
        
        // Should eventually detect start position (hanging)
        assertEquals(ExerciseState.START_POSITION, result.currentState)
    }
    
    @Test
    fun `should count rep when transitioning from hanging to pulled up`() {
        // Calibrate first
        val calibrationPose = createMockPoseWithShoulders(100f, 100f)
        pullUpDetector.calibrate(calibrationPose)
        
        // Start hanging
        val hangingPose = createMockPoseWithShoulders(100f, 100f)
        pullUpDetector.processPose(hangingPose)
        pullUpDetector.processPose(hangingPose) // Confirm hanging state
        
        // Pull up (shoulders significantly higher)
        val pulledUpPose = createMockPoseWithShoulders(20f, 20f) // Much higher
        val result = pullUpDetector.processPose(pulledUpPose)
        pullUpDetector.processPose(pulledUpPose) // Confirm pulled up state
        
        assertEquals(1, result.repCount)
    }
    
    @Test
    fun `should reset correctly`() {
        val pose = createMockPoseWithShoulders(100f, 100f)
        pullUpDetector.calibrate(pose)
        
        // Process some poses to create state
        pullUpDetector.processPose(pose)
        
        pullUpDetector.reset()
        
        assertEquals(0, pullUpDetector.getRepCount())
        assertEquals(ExerciseState.UNKNOWN, pullUpDetector.getCurrentState())
        // Should keep calibration after reset
        assertTrue(pullUpDetector.isCalibrated())
    }
    
    private fun createMockPoseWithShoulders(leftShoulderY: Float, rightShoulderY: Float): Pose {
        val pose = mockk<Pose>()
        
        val leftShoulder = mockk<PoseLandmark>().apply {
            every { position } returns mockk {
                every { x } returns 100f
                every { y } returns leftShoulderY
            }
            every { inFrameLikelihood } returns 0.9f
        }
        
        val rightShoulder = mockk<PoseLandmark>().apply {
            every { position } returns mockk {
                every { x } returns 200f
                every { y } returns rightShoulderY
            }
            every { inFrameLikelihood } returns 0.9f
        }
        
        val nose = mockk<PoseLandmark>().apply {
            every { position } returns mockk {
                every { x } returns 150f
                every { y } returns leftShoulderY - 50f // Above shoulders
            }
            every { inFrameLikelihood } returns 0.9f
        }
        
        every { pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns leftShoulder
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) } returns rightShoulder
        every { pose.getPoseLandmark(PoseLandmark.NOSE) } returns nose
        
        // Mock other landmarks as null for simplicity
        every { pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW) } returns null
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW) } returns null
        every { pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) } returns null
        every { pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) } returns null
        
        return pose
    }
}
package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExerciseManagerTest {
    
    private lateinit var exerciseManager: ExerciseManager
    
    @Before
    fun setUp() {
        exerciseManager = ExerciseManager()
    }
    
    @Test
    fun `should start with push-up as default exercise`() {
        assertEquals(ExerciseType.PUSH_UP, exerciseManager.getCurrentExerciseType())
    }
    
    @Test
    fun `should switch exercise types correctly`() {
        exerciseManager.setExerciseType(ExerciseType.SQUAT)
        assertEquals(ExerciseType.SQUAT, exerciseManager.getCurrentExerciseType())
        
        exerciseManager.setExerciseType(ExerciseType.PULL_UP)
        assertEquals(ExerciseType.PULL_UP, exerciseManager.getCurrentExerciseType())
    }
    
    @Test
    fun `should reset detector when switching exercise types`() {
        // Add some reps to push-up detector
        val pose = createMockPose()
        exerciseManager.processPose(pose)
        
        // Switch to squats
        exerciseManager.setExerciseType(ExerciseType.SQUAT)
        
        // Rep count should be reset
        assertEquals(0, exerciseManager.getRepCount())
    }
    
    @Test
    fun `should not reset when setting same exercise type`() {
        // Add some reps
        val pose = createMockPose()
        exerciseManager.processPose(pose)
        val initialCount = exerciseManager.getRepCount()
        
        // Set same exercise type
        exerciseManager.setExerciseType(ExerciseType.PUSH_UP)
        
        // Rep count should remain the same
        assertEquals(initialCount, exerciseManager.getRepCount())
    }
    
    @Test
    fun `should process pose with current detector`() {
        val pose = createMockPose()
        
        // Test with push-ups (default)
        val result1 = exerciseManager.processPoseWithAnalysis(pose)
        assertEquals(ExerciseType.PUSH_UP, result1.exerciseType)
        
        // Switch to squats
        exerciseManager.setExerciseType(ExerciseType.SQUAT)
        val result2 = exerciseManager.processPoseWithAnalysis(pose)
        assertEquals(ExerciseType.SQUAT, result2.exerciseType)
        
        // Switch to pull-ups
        exerciseManager.setExerciseType(ExerciseType.PULL_UP)
        val result3 = exerciseManager.processPoseWithAnalysis(pose)
        assertEquals(ExerciseType.PULL_UP, result3.exerciseType)
    }
    
    @Test
    fun `should return push-up result when in push-up mode`() {
        val pose = createMockPose()
        
        val result = exerciseManager.getPushUpResult(pose)
        assertNotNull(result)
        assertNotNull(result?.currentState)
    }
    
    @Test
    fun `should return null push-up result when not in push-up mode`() {
        val pose = createMockPose()
        exerciseManager.setExerciseType(ExerciseType.SQUAT)
        
        val result = exerciseManager.getPushUpResult(pose)
        assertNull(result)
    }
    
    @Test
    fun `should reset current detector only`() {
        val pose = createMockPose()
        
        // Add some reps to push-ups
        exerciseManager.processPose(pose)
        val pushUpCount = exerciseManager.getRepCount()
        
        // Switch to squats and add reps
        exerciseManager.setExerciseType(ExerciseType.SQUAT)
        exerciseManager.processPose(pose)
        val squatCount = exerciseManager.getRepCount()
        
        // Reset current (squat) detector
        exerciseManager.reset()
        assertEquals(0, exerciseManager.getRepCount())
        
        // Switch back to push-ups - count should still be there (not reset)
        exerciseManager.setExerciseType(ExerciseType.PUSH_UP)
        assertEquals(pushUpCount, exerciseManager.getRepCount())
    }
    
    @Test
    fun `should reset all detectors`() {
        val pose = createMockPose()
        
        // Add reps to all exercise types
        exerciseManager.setExerciseType(ExerciseType.PUSH_UP)
        exerciseManager.processPose(pose)
        
        exerciseManager.setExerciseType(ExerciseType.SQUAT)
        exerciseManager.processPose(pose)
        
        exerciseManager.setExerciseType(ExerciseType.PULL_UP)
        exerciseManager.processPose(pose)
        
        // Reset all
        exerciseManager.resetAll()
        
        // Check all are reset
        exerciseManager.setExerciseType(ExerciseType.PUSH_UP)
        assertEquals(0, exerciseManager.getRepCount())
        
        exerciseManager.setExerciseType(ExerciseType.SQUAT)
        assertEquals(0, exerciseManager.getRepCount())
        
        exerciseManager.setExerciseType(ExerciseType.PULL_UP)
        assertEquals(0, exerciseManager.getRepCount())
    }
    
    @Test
    fun `should return all available exercises`() {
        val exercises = exerciseManager.getAvailableExercises()
        
        assertEquals(3, exercises.size)
        assertTrue(exercises.contains(ExerciseType.PUSH_UP))
        assertTrue(exercises.contains(ExerciseType.SQUAT))
        assertTrue(exercises.contains(ExerciseType.PULL_UP))
    }
    
    @Test
    fun `should return correct exercise names`() {
        assertEquals("Push-Ups", exerciseManager.getExerciseName(ExerciseType.PUSH_UP))
        assertEquals("Squats", exerciseManager.getExerciseName(ExerciseType.SQUAT))
        assertEquals("Pull-Ups", exerciseManager.getExerciseName(ExerciseType.PULL_UP))
    }
    
    @Test
    fun `should provide direct access to detectors`() {
        val pushUpDetector = exerciseManager.getDetector(ExerciseType.PUSH_UP)
        val squatDetector = exerciseManager.getDetector(ExerciseType.SQUAT)
        val pullUpDetector = exerciseManager.getDetector(ExerciseType.PULL_UP)
        
        assertTrue(pushUpDetector is PushUpDetector)
        assertTrue(squatDetector is SquatDetector)
        assertTrue(pullUpDetector is PullUpDetector)
    }
    
    private fun createMockPose(): Pose {
        val pose = mockk<Pose>()
        every { pose.getPoseLandmark(any()) } returns null
        return pose
    }
}
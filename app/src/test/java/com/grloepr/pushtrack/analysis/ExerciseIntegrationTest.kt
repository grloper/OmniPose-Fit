package com.grloepr.pushtrack.analysis

import org.junit.Test
import org.junit.Assert.*

/**
 * Simple integration test to verify the exercise detection system works correctly
 */
class ExerciseIntegrationTest {
    
    @Test
    fun `exercise manager should start with push-up as default`() {
        val manager = ExerciseManager()
        assertEquals(ExerciseType.PUSH_UP, manager.getCurrentExerciseType())
        assertEquals("Push-Ups", manager.getExerciseName(ExerciseType.PUSH_UP))
    }
    
    @Test
    fun `exercise types should have correct names`() {
        val manager = ExerciseManager()
        assertEquals("Push-Ups", manager.getExerciseName(ExerciseType.PUSH_UP))
        assertEquals("Squats", manager.getExerciseName(ExerciseType.SQUAT))
        assertEquals("Pull-Ups", manager.getExerciseName(ExerciseType.PULL_UP))
    }
    
    @Test
    fun `all exercise detectors should be available`() {
        val manager = ExerciseManager()
        val exercises = manager.getAvailableExercises()
        
        assertEquals(3, exercises.size)
        assertTrue(exercises.contains(ExerciseType.PUSH_UP))
        assertTrue(exercises.contains(ExerciseType.SQUAT))
        assertTrue(exercises.contains(ExerciseType.PULL_UP))
    }
    
    @Test
    fun `exercise detector inheritance should work correctly`() {
        val pushUpDetector = PushUpDetector()
        val squatDetector = SquatDetector()
        val pullUpDetector = PullUpDetector()
        
        // All should extend ExerciseDetector
        assertTrue(pushUpDetector is ExerciseDetector)
        assertTrue(squatDetector is ExerciseDetector)
        assertTrue(pullUpDetector is ExerciseDetector)
        
        // Should have correct exercise types
        assertEquals(ExerciseType.PUSH_UP, pushUpDetector.exerciseType)
        assertEquals(ExerciseType.SQUAT, squatDetector.exerciseType)
        assertEquals(ExerciseType.PULL_UP, pullUpDetector.exerciseType)
    }
    
    @Test
    fun `exercise states should map correctly`() {
        // Test that exercise states make sense
        val states = ExerciseState.values()
        assertEquals(3, states.size)
        assertTrue(states.contains(ExerciseState.UNKNOWN))
        assertTrue(states.contains(ExerciseState.START_POSITION))
        assertTrue(states.contains(ExerciseState.END_POSITION))
    }
    
    @Test
    fun `exercise manager should handle detector switching correctly`() {
        val manager = ExerciseManager()
        
        // Start with push-ups
        assertEquals(ExerciseType.PUSH_UP, manager.getCurrentExerciseType())
        
        // Switch to squats
        manager.setExerciseType(ExerciseType.SQUAT)
        assertEquals(ExerciseType.SQUAT, manager.getCurrentExerciseType())
        
        // Switch to pull-ups
        manager.setExerciseType(ExerciseType.PULL_UP)
        assertEquals(ExerciseType.PULL_UP, manager.getCurrentExerciseType())
        
        // Switch back to push-ups
        manager.setExerciseType(ExerciseType.PUSH_UP)
        assertEquals(ExerciseType.PUSH_UP, manager.getCurrentExerciseType())
    }
}
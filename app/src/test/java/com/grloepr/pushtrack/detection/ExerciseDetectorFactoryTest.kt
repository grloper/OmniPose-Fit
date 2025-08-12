package com.grloepr.pushtrack.detection

import org.junit.Assert.*
import org.junit.Test

class ExerciseDetectorFactoryTest {
    
    @Test
    fun `should create PushUpDetector for PUSH_UP type`() {
        val detector = ExerciseDetectorFactory.createDetector(ExerciseType.PUSH_UP)
        
        assertTrue(detector is PushUpDetector)
        assertEquals(ExerciseType.PUSH_UP, detector.exerciseType)
    }
    
    @Test
    fun `should create PullUpDetector for PULL_UP type`() {
        val detector = ExerciseDetectorFactory.createDetector(ExerciseType.PULL_UP)
        
        assertTrue(detector is PullUpDetector)
        assertEquals(ExerciseType.PULL_UP, detector.exerciseType)
    }
    
    @Test
    fun `should create SquatDetector for SQUAT type`() {
        val detector = ExerciseDetectorFactory.createDetector(ExerciseType.SQUAT)
        
        assertTrue(detector is SquatDetector)
        assertEquals(ExerciseType.SQUAT, detector.exerciseType)
    }
    
    @Test
    fun `should return all supported exercise types`() {
        val supportedExercises = ExerciseDetectorFactory.getSupportedExercises()
        
        assertEquals(3, supportedExercises.size)
        assertTrue(supportedExercises.contains(ExerciseType.PUSH_UP))
        assertTrue(supportedExercises.contains(ExerciseType.PULL_UP))
        assertTrue(supportedExercises.contains(ExerciseType.SQUAT))
    }
    
    @Test
    fun `should provide correct display names`() {
        assertEquals("Push-Up", ExerciseDetectorFactory.getDisplayName(ExerciseType.PUSH_UP))
        assertEquals("Pull-Up", ExerciseDetectorFactory.getDisplayName(ExerciseType.PULL_UP))
        assertEquals("Squat", ExerciseDetectorFactory.getDisplayName(ExerciseType.SQUAT))
    }
    
    @Test
    fun `should provide meaningful descriptions`() {
        val pushUpDescription = ExerciseDetectorFactory.getDescription(ExerciseType.PUSH_UP)
        val pullUpDescription = ExerciseDetectorFactory.getDescription(ExerciseType.PULL_UP)
        val squatDescription = ExerciseDetectorFactory.getDescription(ExerciseType.SQUAT)
        
        assertFalse(pushUpDescription.isEmpty())
        assertFalse(pullUpDescription.isEmpty())
        assertFalse(squatDescription.isEmpty())
        
        // Verify descriptions contain relevant keywords
        assertTrue(pushUpDescription.contains("chest") || pushUpDescription.contains("upper"))
        assertTrue(pullUpDescription.contains("back") || pullUpDescription.contains("biceps"))
        assertTrue(squatDescription.contains("quadriceps") || squatDescription.contains("glutes"))
    }
    
    @Test
    fun `created detectors should have initial state`() {
        for (exerciseType in ExerciseType.values()) {
            val detector = ExerciseDetectorFactory.createDetector(exerciseType)
            
            assertEquals(0, detector.getRepCount())
            assertEquals(ExercisePhase.UP, detector.getCurrentPhase())
            assertNotNull(detector.state.value)
        }
    }
    
    @Test
    fun `created detectors should be independent instances`() {
        val detector1 = ExerciseDetectorFactory.createDetector(ExerciseType.PUSH_UP)
        val detector2 = ExerciseDetectorFactory.createDetector(ExerciseType.PUSH_UP)
        
        assertNotSame("Should create different instances", detector1, detector2)
    }
}
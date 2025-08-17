package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose

/**
 * Manages multiple exercise detectors and coordinates exercise selection
 */
class ExerciseManager {
    
    // Available exercise detectors
    private val pushUpDetector = PushUpDetector()
    private val squatDetector = SquatDetector()
    private val pullUpDetector = PullUpDetector()
    
    // Currently selected exercise
    private var currentExerciseType = ExerciseType.PUSH_UP
    
    /**
     * Get the current active detector
     */
    private fun getCurrentDetector(): ExerciseDetector {
        return when (currentExerciseType) {
            ExerciseType.PUSH_UP -> pushUpDetector
            ExerciseType.SQUAT -> squatDetector
            ExerciseType.PULL_UP -> pullUpDetector
        }
    }
    
    /**
     * Switch to a different exercise type
     */
    fun setExerciseType(exerciseType: ExerciseType) {
        if (currentExerciseType != exerciseType) {
            // Reset the previous detector
            getCurrentDetector().reset()
            currentExerciseType = exerciseType
        }
    }
    
    /**
     * Get the current exercise type
     */
    fun getCurrentExerciseType(): ExerciseType = currentExerciseType
    
    /**
     * Process a pose with the current exercise detector
     */
    fun processPoseWithAnalysis(pose: Pose): ExerciseResult {
        return getCurrentDetector().processPoseWithAnalysis(pose)
    }
    
    /**
     * Process a pose and return rep count (compatibility method)
     */
    fun processPose(pose: Pose): Int {
        return getCurrentDetector().processPose(pose)
    }
    
    /**
     * Get current rep count
     */
    fun getRepCount(): Int {
        return getCurrentDetector().getRepCount()
    }
    
    /**
     * Get current exercise state
     */
    fun getCurrentState(): ExerciseState {
        return getCurrentDetector().getCurrentState()
    }
    
    /**
     * Reset the current exercise detector
     */
    fun reset() {
        getCurrentDetector().reset()
    }
    
    /**
     * Reset all exercise detectors
     */
    fun resetAll() {
        pushUpDetector.reset()
        squatDetector.reset()
        pullUpDetector.reset()
    }
    
    /**
     * Get push-up specific result for backward compatibility
     */
    fun getPushUpResult(pose: Pose): PushUpResult? {
        return if (currentExerciseType == ExerciseType.PUSH_UP) {
            pushUpDetector.processPoseWithAnalysis(pose)
        } else {
            null
        }
    }
    
    /**
     * Get exercise detector for a specific type (for direct access if needed)
     */
    fun getDetector(exerciseType: ExerciseType): ExerciseDetector {
        return when (exerciseType) {
            ExerciseType.PUSH_UP -> pushUpDetector
            ExerciseType.SQUAT -> squatDetector
            ExerciseType.PULL_UP -> pullUpDetector
        }
    }
    
    /**
     * Get all available exercise types
     */
    fun getAvailableExercises(): List<ExerciseType> {
        return ExerciseType.values().toList()
    }
    
    /**
     * Get friendly name for exercise type
     */
    fun getExerciseName(exerciseType: ExerciseType): String {
        return when (exerciseType) {
            ExerciseType.PUSH_UP -> "Push-Ups"
            ExerciseType.SQUAT -> "Squats"
            ExerciseType.PULL_UP -> "Pull-Ups"
        }
    }
}
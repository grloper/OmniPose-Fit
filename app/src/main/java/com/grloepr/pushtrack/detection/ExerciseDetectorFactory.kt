package com.grloepr.pushtrack.detection

/**
 * Factory for creating exercise detectors
 * Enables easy extension and modular exercise detection
 */
object ExerciseDetectorFactory {
    
    /**
     * Create a detector for the specified exercise type
     * @param exerciseType The type of exercise to detect
     * @return ExerciseDetector implementation for the exercise type
     */
    fun createDetector(exerciseType: ExerciseType): ExerciseDetector {
        return when (exerciseType) {
            ExerciseType.PUSH_UP -> PushUpDetector()
            ExerciseType.PULL_UP -> PullUpDetector()
            ExerciseType.SQUAT -> SquatDetector()
        }
    }
    
    /**
     * Get all supported exercise types
     * @return List of all supported exercise types
     */
    fun getSupportedExercises(): List<ExerciseType> {
        return ExerciseType.values().toList()
    }
    
    /**
     * Get display name for exercise type
     * @param exerciseType The exercise type
     * @return Human-readable display name
     */
    fun getDisplayName(exerciseType: ExerciseType): String {
        return when (exerciseType) {
            ExerciseType.PUSH_UP -> "Push-Up"
            ExerciseType.PULL_UP -> "Pull-Up"
            ExerciseType.SQUAT -> "Squat"
        }
    }
    
    /**
     * Get description for exercise type
     * @param exerciseType The exercise type
     * @return Description of the exercise
     */
    fun getDescription(exerciseType: ExerciseType): String {
        return when (exerciseType) {
            ExerciseType.PUSH_UP -> "Upper body exercise targeting chest, shoulders, and triceps"
            ExerciseType.PULL_UP -> "Upper body exercise targeting back, biceps, and shoulders"
            ExerciseType.SQUAT -> "Lower body exercise targeting quadriceps, glutes, and hamstrings"
        }
    }
}
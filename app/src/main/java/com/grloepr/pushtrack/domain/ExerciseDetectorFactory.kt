package com.grloepr.pushtrack.domain

import com.grloepr.pushtrack.analysis.PushUpDetector
import com.grloepr.pushtrack.analysis.PullUpDetector
import com.grloepr.pushtrack.analysis.SquatDetector

/**
 * Factory for creating exercise detectors
 */
object ExerciseDetectorFactory {
    
    /**
     * Create a detector for the specified exercise type
     */
    fun createDetector(exerciseType: ExerciseType): ExerciseDetector {
        return when (exerciseType) {
            ExerciseType.PUSH_UP -> PushUpDetector()
            ExerciseType.PULL_UP -> PullUpDetector()
            ExerciseType.SQUAT -> SquatDetector()
        }
    }
    
    /**
     * Get all available exercise types
     */
    fun getSupportedExercises(): List<ExerciseType> {
        return ExerciseType.values().toList()
    }
}
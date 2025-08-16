package com.grloepr.pushtrack.domain

/**
 * Supported exercise types in PushTrack
 */
enum class ExerciseType {
    PUSH_UP,
    PULL_UP,
    SQUAT;
    
    /**
     * Human-readable display name for the exercise
     */
    val displayName: String
        get() = when (this) {
            PUSH_UP -> "Push-ups"
            PULL_UP -> "Pull-ups"
            SQUAT -> "Squats"
        }
    
    /**
     * Key landmarks required for this exercise type
     */
    val requiredLandmarks: List<String>
        get() = when (this) {
            PUSH_UP -> listOf("shoulders", "elbows", "wrists")
            PULL_UP -> listOf("shoulders", "elbows", "wrists", "nose")
            SQUAT -> listOf("hips", "knees", "ankles", "shoulders")
        }
}
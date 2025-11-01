package com.grloepr.pushtrack.exercise

/**
 * Represents the current state of an exercise
 */
sealed class ExerciseState {
    object Waiting : ExerciseState()
    object Down : ExerciseState()
    object Up : ExerciseState()
    object Resting : ExerciseState()
}


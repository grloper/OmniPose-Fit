package com.grloepr.pushtrack.domain

/**
 * Form quality assessment for an exercise
 */
data class FormQuality(
    val score: Float, // 0-100
    val hasGoodForm: Boolean,
    val feedback: String? = null
)

/**
 * Result of exercise detection for a single frame
 */
data class DetectionResult(
    val repCount: Int,
    val currentState: ExerciseState,
    val formQuality: FormQuality?,
    val confidence: Float = 0f,
    val lastAngle: Float? = null,
    val detectionMethod: String? = null
)

/**
 * Generic exercise states
 */
enum class ExerciseState {
    UNKNOWN,
    START_POSITION,    // e.g., up position for push-ups, hanging for pull-ups, standing for squats
    END_POSITION,      // e.g., down position for push-ups, top for pull-ups, bottom for squats
    TRANSITIONING
}
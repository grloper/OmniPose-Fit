package com.grloepr.pushtrack.domain

/**
 * Generic result data class for all exercise types
 */
data class DetectionResult(
    val repCount: Int,
    val currentState: ExerciseState,
    val formQuality: FormQuality? = null,
    val confidence: Float = 0f,
    val lastAngle: Float? = null,
    val detectionMethod: String = "none" // Added this field
)

package com.grloepr.pushtrack.domain.model

/**
 * Model class representing the state of a push-up
 */
data class PushUpState(
    val phase: PushUpPhase,
    val lastAngle: Float,
    val confidence: Float = 1.0f,
    val strategy: String = "Unknown"
)

/**
 * Push-up phases
 */
enum class PushUpPhase {
    UP,                // Full extension, arms straight
    TRANSITIONING_DOWN, // Moving from up to down
    DOWN,              // Bottom position, arms bent
    TRANSITIONING_UP   // Moving from down to up
}

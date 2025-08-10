package com.grloepr.pushtrack.counting

/**
 * Represents the different states of a push-up movement
 */
enum class PushUpState {
    /** Initial state - person is standing or in neutral position */
    NEUTRAL,
    
    /** Person is moving down towards the ground */
    DESCENDING,
    
    /** Person has reached the bottom position (chest near ground) */
    DOWN_POSITION,
    
    /** Person is pushing up from the bottom */
    ASCENDING,
    
    /** Person has reached the top position (arms extended) */
    UP_POSITION
}

/**
 * Data class representing a complete push-up movement
 */
data class PushUpMovement(
    val startTime: Long,
    val endTime: Long,
    val minElbowAngle: Float,
    val maxElbowAngle: Float,
    val quality: PushUpQuality
)

/**
 * Quality assessment of a push-up rep
 */
enum class PushUpQuality {
    EXCELLENT,  // Full range of motion, proper form
    GOOD,       // Good range of motion, minor form issues
    FAIR,       // Partial range of motion
    POOR        // Insufficient range of motion or poor form
}
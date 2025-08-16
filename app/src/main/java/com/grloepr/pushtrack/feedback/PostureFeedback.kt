package com.grloepr.pushtrack.feedback

/**
 * Represents specific posture feedback for exercise form
 */
enum class PostureFeedback {
    GOOD_FORM,       // General good form feedback
    LOWER_BODY,      // Need to lower body more (for push-ups/squats)
    RAISE_BODY,      // Need to raise body more (for push-ups/pull-ups)
    STRAIGHTEN_BACK, // Need to maintain straight back
    ALIGN_HANDS,     // Hands/limbs need better alignment
    SLOW_DOWN,       // Exercise tempo is too fast
    KEEP_GOING;      // Encouragement feedback
    
    /**
     * Convert feedback to display string
     */
    override fun toString(): String {
        return when (this) {
            GOOD_FORM -> "Good form!"
            LOWER_BODY -> "Lower your body more"
            RAISE_BODY -> "Push up higher"
            STRAIGHTEN_BACK -> "Keep your back straight"
            ALIGN_HANDS -> "Align your hands"
            SLOW_DOWN -> "Slow down"
            KEEP_GOING -> "Keep going!"
        }
    }
}

package com.grloepr.pushtrack.exercise

/**
 * Represents qualitative feedback about the athlete's current form. The overall score is in the
 * range \[0, 1], where 1 means flawless form and 0 means critical issues were found. Individual
 * signals highlight specific issues or strengths that contributed to the score.
 */
data class FormFeedback(
    val overallScore: Float,
    val headline: String,
    val signals: List<FormSignal> = emptyList(),
    val debugMetrics: Map<String, Float> = emptyMap()
) {
    val primarySignal: FormSignal?
        get() = signals.maxByOrNull { it.weight }

    companion object {
        fun neutral(message: String = "Hold steady for clean reps") =
            FormFeedback(overallScore = 0.6f, headline = message)

        fun lostPose() = FormFeedback(
            overallScore = 0f,
            headline = "I lost track of your pose",
            signals = listOf(
                FormSignal(
                    id = "pose",
                    label = "Pose",
                    score = 0f,
                    severity = FormSeverity.CRITICAL,
                    message = "Step fully into the frame so I can analyse your form"
                )
            )
        )
    }
}

data class FormSignal(
    val id: String,
    val label: String,
    val score: Float,
    val severity: FormSeverity,
    val message: String,
    val weight: Float = 1f
)

enum class FormSeverity(val colorKey: String) {
    INFO("info"),
    WARNING("warning"),
    CRITICAL("critical")
}

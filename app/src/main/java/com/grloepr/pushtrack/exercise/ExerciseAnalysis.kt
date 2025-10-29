package com.grloepr.pushtrack.exercise

data class ExerciseAnalysis(
    val state: ExerciseState,
    val repCount: Int,
    val repDelta: Int,
    val form: FormFeedback,
    val poseVisible: Boolean,
    val timestampMs: Long
)

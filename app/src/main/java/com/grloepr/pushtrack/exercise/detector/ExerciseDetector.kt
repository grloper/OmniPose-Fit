package com.grloepr.pushtrack.exercise.detector

import com.google.mlkit.vision.pose.Pose

interface ExerciseDetector {
    fun analyze(pose: Pose, timestampMs: Long): DetectorResult
    fun onPoseLost(timestampMs: Long)
    fun reset()
}

data class DetectorResult(
    val state: com.grloepr.pushtrack.exercise.ExerciseState,
    val repCompleted: Boolean,
    val formFeedback: com.grloepr.pushtrack.exercise.FormFeedback,
    val poseVisible: Boolean = true
)

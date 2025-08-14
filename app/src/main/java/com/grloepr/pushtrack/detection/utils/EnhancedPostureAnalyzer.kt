package com.grloepr.pushtrack.detection.utils

import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.detection.ExercisePhase
import com.grloepr.pushtrack.detection.ExerciseType

/**
 * Stubbed posture analyzer (all advanced logic removed).
 * TODO: Reintroduce form quality calculations if needed.
 */
class EnhancedPostureAnalyzer {
    fun analyzePose(
        pose: Pose,
        exerciseType: ExerciseType,
        currentPhase: ExercisePhase
    ): EnhancedPostureAnalysisResult = EnhancedPostureAnalysisResult(
        formQuality = 0f,
        averageFormQuality = 0f,
        feedback = null,
        hasGoodForm = false,
        exerciseType = exerciseType
    )

    fun reset() {}
    fun getStarRating(quality: Float): Int = 1
}

data class EnhancedPostureAnalysisResult(
    val formQuality: Float,
    val averageFormQuality: Float,
    val feedback: Any?,
    val hasGoodForm: Boolean,
    val exerciseType: ExerciseType
)
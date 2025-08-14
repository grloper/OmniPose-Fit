package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.detection.utils.AngleCalculator

/**
 * Minimal Squat detector stub.
 * TODO: Add hip height fallback & symmetry checks.
 */
class SquatDetector : BaseExerciseDetector(ExerciseType.SQUAT) {

    private val downKneeThreshold = 110f
    private val upKneeThreshold = 160f

    override fun calculatePrimaryAngle(pose: Pose): Float? {
        // TODO: Add median filtering / hip-height secondary
        return AngleCalculator.calculateAverageKneeAngle(pose)
    }

    override fun determinePhase(pose: Pose, primaryAngle: Float?): ExercisePhase {
        val a = primaryAngle ?: return ExercisePhase.TRANSITIONING
        return when {
            a < downKneeThreshold -> ExercisePhase.DOWN
            a > upKneeThreshold -> ExercisePhase.UP
            else -> ExercisePhase.TRANSITIONING
        }
    }

    override fun calculateConfidence(pose: Pose, primaryAngle: Float?): Float {
        // TODO: Multi-signal confidence
        return if (primaryAngle != null) 0.5f else 0f
    }

    override fun getDetectionMethod(): String = "stub_knee_angle"
}
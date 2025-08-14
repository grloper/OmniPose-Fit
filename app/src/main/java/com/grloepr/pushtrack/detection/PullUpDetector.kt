package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.detection.utils.AngleCalculator

/**
 * Minimal Pull-Up detector stub.
 * TODO: Implement shoulder height / chin-over-bar or distance metrics.
 */
class PullUpDetector : BaseExerciseDetector(ExerciseType.PULL_UP) {

    private val upElbowThreshold = 100f   // Bent (top)
    private val downElbowThreshold = 140f // Straighter (bottom)

    override fun calculatePrimaryAngle(pose: Pose): Float? {
        // TODO: Replace with shoulder vertical normalization
        return AngleCalculator.calculateAverageElbowAngle(pose)
    }

    override fun determinePhase(pose: Pose, primaryAngle: Float?): ExercisePhase {
        val a = primaryAngle ?: return ExercisePhase.TRANSITIONING
        return when {
            a < upElbowThreshold -> ExercisePhase.UP
            a > downElbowThreshold -> ExercisePhase.DOWN
            else -> ExercisePhase.TRANSITIONING
        }
    }

    override fun calculateConfidence(pose: Pose, primaryAngle: Float?): Float {
        // TODO: Use vertical displacement + elbow reliability
        return if (primaryAngle != null) 0.5f else 0f
    }

    override fun getDetectionMethod(): String = "stub_elbow_angle"
}
package com.grloepr.pushtrack.exercise.detector

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.exercise.ExerciseState
import com.grloepr.pushtrack.exercise.FormFeedback
import com.grloepr.pushtrack.exercise.FormSeverity
import com.grloepr.pushtrack.exercise.FormSignal
import com.grloepr.pushtrack.exercise.MeasurementSmoother
import com.grloepr.pushtrack.exercise.averageOrNull
import com.grloepr.pushtrack.exercise.averagePoint
import com.grloepr.pushtrack.exercise.calculateAngle
import com.grloepr.pushtrack.exercise.hasValidLandmarks
import com.grloepr.pushtrack.exercise.landmarkSetConfidence
import kotlin.math.abs

class SquatDetector : ExerciseDetector {
    private var currentState: ExerciseState = ExerciseState.Waiting
    private var lastStateChangeTime = 0L
    private val minStateDurationMs = 240L

    private val kneeAngleSmoother = MeasurementSmoother(6)
    private val hipDepthSmoother = MeasurementSmoother(6)
    private val torsoAngleSmoother = MeasurementSmoother(6)

    override fun analyze(pose: Pose, timestampMs: Long): DetectorResult {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (!hasValidLandmarks(leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle)) {
            return DetectorResult(
                state = ExerciseState.Waiting,
                repCompleted = false,
                formFeedback = FormFeedback.lostPose(),
                poseVisible = false
            )
        }

        val kneeAngles = listOf(
            calculateAngle(leftHip, leftKnee, leftAnkle),
            calculateAngle(rightHip, rightKnee, rightAnkle)
        )
        val kneeConfidence = listOf(
            landmarkSetConfidence(leftHip, leftKnee, leftAnkle),
            landmarkSetConfidence(rightHip, rightKnee, rightAnkle)
        )

        val selectedAngle = when {
            kneeAngles[0] == null -> kneeAngles[1]
            kneeAngles[1] == null -> kneeAngles[0]
            else -> if (kneeConfidence[0] >= kneeConfidence[1]) kneeAngles[0] else kneeAngles[1]
        }

        val smoothedKneeAngle = kneeAngleSmoother.add(selectedAngle)

        val hipPoint = averagePoint(listOf(leftHip, rightHip))
        val anklePoint = averagePoint(listOf(leftAnkle, rightAnkle))
        val shoulderPoint = averagePoint(listOf(leftShoulder, rightShoulder)) ?: hipPoint

        if (hipPoint == null || anklePoint == null) {
            return DetectorResult(
                state = ExerciseState.Waiting,
                repCompleted = false,
                formFeedback = FormFeedback.lostPose(),
                poseVisible = false
            )
        }

        val legLength = abs((shoulderPoint?.y ?: hipPoint.y) - anklePoint.y).coerceAtLeast(1f)
        val hipDepth = abs(hipPoint.y - anklePoint.y) / legLength
        val smoothedHipDepth = hipDepthSmoother.add(hipDepth.toDouble())

        val torsoAngles = listOfNotNull(
            calculateAngle(leftShoulder, leftHip, leftAnkle),
            calculateAngle(rightShoulder, rightHip, rightAnkle)
        )
        val smoothedTorsoAngle = torsoAngleSmoother.add(torsoAngles.averageOrNull())

        val downByAngle = smoothedKneeAngle != null && smoothedKneeAngle < 120.0
        val upByAngle = smoothedKneeAngle != null && smoothedKneeAngle > 145.0

        val downByDepth = smoothedHipDepth != null && smoothedHipDepth < 0.40
        val upByDepth = smoothedHipDepth != null && smoothedHipDepth > 0.48

        val candidateState = when {
            downByAngle || downByDepth -> ExerciseState.Down
            upByAngle || upByDepth -> ExerciseState.Up
            else -> ExerciseState.Waiting
        }

        val stabilizedState = stabilizeState(candidateState, timestampMs)
        val repCompleted = currentState == ExerciseState.Down && stabilizedState == ExerciseState.Up
        
        // Debug logging
        android.util.Log.d("SquatDetector",
            "hipDepth=$smoothedHipDepth, kneeAngle=$smoothedKneeAngle, " +
            "downByAngle=$downByAngle, upByAngle=$upByAngle, " +
            "downByDepth=$downByDepth, upByDepth=$upByDepth, " +
            "candidate=$candidateState, current=$currentState, stabilized=$stabilizedState, " +
            "repCompleted=$repCompleted")
        
        currentState = stabilizedState

        val depthScore = depthScore(smoothedHipDepth)
        val kneeScore = kneeScore(smoothedKneeAngle)
        val torsoScore = torsoScore(smoothedTorsoAngle)

        val signals = listOfNotNull(
            buildDepthSignal(depthScore),
            buildKneeSignal(kneeScore),
            buildTorsoSignal(torsoScore)
        )

        val headline = when {
            signals.isEmpty() -> "Find your rhythm"
            else -> signals.minBy { it.score }.message
        }

        val overallScore = signals.takeIf { it.isNotEmpty() }
            ?.let { list ->
                val weightSum = list.sumOf { it.weight.toDouble() }
                val weighted = list.sumOf { (it.weight * it.score).toDouble() }
                (weighted / weightSum).toFloat()
            } ?: 0.6f

        val debug = buildMap {
            smoothedHipDepth?.let { put("hipDepth", it.toFloat()) }
            smoothedKneeAngle?.let { put("kneeAngle", it.toFloat()) }
            smoothedTorsoAngle?.let { put("torsoAngle", it.toFloat()) }
        }

        val feedback = FormFeedback(
            overallScore = overallScore.coerceIn(0f, 1f),
            headline = headline,
            signals = signals,
            debugMetrics = debug
        )

        return DetectorResult(
            state = stabilizedState,
            repCompleted = repCompleted,
            formFeedback = feedback
        )
    }

    override fun onPoseLost(timestampMs: Long) {
        currentState = ExerciseState.Waiting
        lastStateChangeTime = timestampMs
        kneeAngleSmoother.clear()
        hipDepthSmoother.clear()
        torsoAngleSmoother.clear()
    }

    override fun reset() {
        currentState = ExerciseState.Waiting
        lastStateChangeTime = 0L
        kneeAngleSmoother.clear()
        hipDepthSmoother.clear()
        torsoAngleSmoother.clear()
    }

    private fun stabilizeState(candidate: ExerciseState, timestamp: Long): ExerciseState {
        if (candidate == currentState) return currentState
        if (candidate == ExerciseState.Waiting) return ExerciseState.Waiting
        val elapsed = timestamp - lastStateChangeTime
        return if (elapsed >= minStateDurationMs) {
            lastStateChangeTime = timestamp
            candidate
        } else {
            currentState
        }
    }

    private fun depthScore(hipDepth: Double?): Float {
        hipDepth ?: return 0.5f
        // hipDepth is smaller when athlete is lower. Target around 0.30.
        val normalized = ((0.44 - hipDepth) / (0.44 - 0.30)).coerceIn(0.0, 1.2)
        return normalized.toFloat().coerceIn(0f, 1f)
    }

    private fun kneeScore(kneeAngle: Double?): Float {
        kneeAngle ?: return 0.4f
        val normalized = ((155.0 - kneeAngle) / (155.0 - 95.0)).coerceIn(0.0, 1.2)
        return normalized.toFloat().coerceIn(0f, 1f)
    }

    private fun torsoScore(torsoAngle: Double?): Float {
        torsoAngle ?: return 0.6f
        val deviation = abs(180.0 - torsoAngle)
        val normalized = (1.0 - (deviation / 35.0)).coerceIn(0.0, 1.2)
        return normalized.toFloat().coerceIn(0f, 1f)
    }

    private fun buildDepthSignal(score: Float): FormSignal {
        val message = when {
            score > 0.85f -> "Excellent squat depth"
            score > 0.6f -> "Sit a bit lower for full depth"
            else -> "Drive hips back and squat deeper"
        }
        val severity = when {
            score > 0.75f -> FormSeverity.INFO
            score > 0.55f -> FormSeverity.WARNING
            else -> FormSeverity.CRITICAL
        }
        return FormSignal(
            id = "depth",
            label = "Depth",
            score = score,
            severity = severity,
            message = message,
            weight = 1.2f
        )
    }

    private fun buildKneeSignal(score: Float): FormSignal {
        val message = when {
            score > 0.85f -> "Controlled knee bend"
            score > 0.6f -> "Push knees out as you lower"
            else -> "Bend knees more to initiate the squat"
        }
        val severity = when {
            score > 0.75f -> FormSeverity.INFO
            score > 0.55f -> FormSeverity.WARNING
            else -> FormSeverity.CRITICAL
        }
        return FormSignal(
            id = "knees",
            label = "Knees",
            score = score,
            severity = severity,
            message = message,
            weight = 1f
        )
    }

    private fun buildTorsoSignal(score: Float): FormSignal {
        val message = when {
            score > 0.85f -> "Strong upright torso"
            score > 0.6f -> "Keep chest proud and back flat"
            else -> "Lift your chest to avoid collapsing"
        }
        val severity = when {
            score > 0.75f -> FormSeverity.INFO
            score > 0.55f -> FormSeverity.WARNING
            else -> FormSeverity.CRITICAL
        }
        return FormSignal(
            id = "torso",
            label = "Torso",
            score = score,
            severity = severity,
            message = message,
            weight = 0.9f
        )
    }
}

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
import com.grloepr.pushtrack.exercise.distance
import com.grloepr.pushtrack.exercise.hasValidLandmarks

class PushupDetector : ExerciseDetector {
    private var currentState: ExerciseState = ExerciseState.Waiting
    private var lastStateChangeTime = 0L
    private val minStateDurationMs = 220L

    private val elbowSmoother = MeasurementSmoother(6)
    private val gapSmoother = MeasurementSmoother(6)
    private val hipAlignmentSmoother = MeasurementSmoother(6)
    private var baselineGap: Double? = null

    override fun analyze(pose: Pose, timestampMs: Long): DetectorResult {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)

        if (!hasValidLandmarks(
                leftShoulder,
                rightShoulder,
                leftElbow,
                rightElbow,
                leftWrist,
                rightWrist,
                leftHip,
                rightHip
            )
        ) {
            return DetectorResult(
                state = ExerciseState.Waiting,
                repCompleted = false,
                formFeedback = FormFeedback.lostPose(),
                poseVisible = false
            )
        }

        val shoulderPoint = averagePoint(listOf(leftShoulder, rightShoulder))
        val hipPoint = averagePoint(listOf(leftHip, rightHip))
        val supportPoint = averagePoint(listOf(leftWrist, rightWrist))
            ?: averagePoint(listOf(leftElbow, rightElbow))

        if (shoulderPoint == null || hipPoint == null || supportPoint == null) {
            return DetectorResult(
                state = ExerciseState.Waiting,
                repCompleted = false,
                formFeedback = FormFeedback.lostPose(),
                poseVisible = false
            )
        }

        val torsoLength = distance(shoulderPoint, hipPoint).coerceAtLeast(1.0)
        val normalizedGap = ((supportPoint.y - shoulderPoint.y) / torsoLength).coerceIn(0.0, 2.0)
        val smoothedGap = gapSmoother.add(normalizedGap)

        val elbowAngles = listOfNotNull(
            calculateAngle(leftShoulder, leftElbow, leftWrist),
            calculateAngle(rightShoulder, rightElbow, rightWrist)
        )
        val smoothedElbow = elbowSmoother.add(elbowAngles.averageOrNull())

        val hipAlignment = run {
            val hipVsShoulder = (hipPoint.y - shoulderPoint.y) / torsoLength
            hipAlignmentSmoother.add(hipVsShoulder)
        }

        val baseline = baselineGap ?: smoothedGap
        val depthDelta = if (baseline != null && smoothedGap != null) baseline - smoothedGap else 0.0

        val elbowDown = smoothedElbow != null && smoothedElbow < 130.0
        val elbowUp = smoothedElbow != null && smoothedElbow > 150.0

        val depthDown = smoothedGap != null && baseline != null && depthDelta > 0.10
        val depthRecovered = smoothedGap != null && baseline != null && smoothedGap > baseline - 0.08

        val fallbackDown = smoothedGap != null && smoothedGap < 0.40
        val fallbackUp = smoothedGap != null && smoothedGap > 0.50

        val candidateState = when {
            (elbowDown || depthDown || fallbackDown) -> ExerciseState.Down
            (elbowUp || depthRecovered || fallbackUp) -> ExerciseState.Up
            else -> ExerciseState.Waiting
        }

        val stabilizedState = stabilizeState(candidateState, timestampMs)
        val repCompleted = currentState == ExerciseState.Down && stabilizedState == ExerciseState.Up

        if (stabilizedState == ExerciseState.Up && smoothedGap != null) {
            baselineGap = baseline?.let { existing -> existing * 0.8 + smoothedGap * 0.2 } ?: smoothedGap
        }

        // Debug logging
        android.util.Log.d("PushupDetector", 
            "gap=$smoothedGap, baseline=$baseline, depthDelta=$depthDelta, " +
            "elbow=$smoothedElbow, elbowDown=$elbowDown, elbowUp=$elbowUp, " +
            "depthDown=$depthDown, depthRecovered=$depthRecovered, " +
            "candidate=$candidateState, current=$currentState, stabilized=$stabilizedState, " +
            "repCompleted=$repCompleted")

        currentState = stabilizedState

        val depthScore = depthScore(depthDelta)
        val lockoutScore = lockoutScore(smoothedElbow)
        val coreScore = coreScore(hipAlignment)

        val signals = listOfNotNull(
            buildDepthSignal(depthScore, depthDelta),
            buildLockoutSignal(lockoutScore, smoothedElbow),
            buildCoreSignal(coreScore, hipAlignment)
        )

        val headline = when {
            signals.isEmpty() -> "Hold steady for clean reps"
            else -> signals.minBy { it.score }.message
        }

        val overallScore = signals.takeIf { it.isNotEmpty() }
            ?.let { list ->
                val weightSum = list.sumOf { it.weight.toDouble() }
                val weighted = list.sumOf { (it.weight * it.score).toDouble() }
                (weighted / weightSum).toFloat()
            } ?: 0.6f

        val formFeedback = FormFeedback(
            overallScore = overallScore.coerceIn(0f, 1f),
            headline = headline,
            signals = signals,
            debugMetrics = buildMap {
                smoothedGap?.let { put("gap", it.toFloat()) }
                depthDelta.takeIf { it != 0.0 }?.let { put("depthDelta", it.toFloat()) }
                smoothedElbow?.let { put("elbow", it.toFloat()) }
                hipAlignment?.let { put("hipAlign", it.toFloat()) }
            }
        )

        return DetectorResult(
            state = stabilizedState,
            repCompleted = repCompleted,
            formFeedback = formFeedback
        )
    }

    override fun onPoseLost(timestampMs: Long) {
        currentState = ExerciseState.Waiting
        lastStateChangeTime = timestampMs
        elbowSmoother.clear()
        gapSmoother.clear()
        hipAlignmentSmoother.clear()
    }

    override fun reset() {
        currentState = ExerciseState.Waiting
        lastStateChangeTime = 0L
        baselineGap = null
        elbowSmoother.clear()
        gapSmoother.clear()
        hipAlignmentSmoother.clear()
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

    private fun depthScore(depthDelta: Double): Float {
        val target = 0.16
        return depthDelta.takeIf { !it.isNaN() }?.let {
            (it / target).coerceIn(0.0, 1.2).toFloat().coerceIn(0f, 1f)
        } ?: 0.5f
    }

    private fun lockoutScore(elbowAngle: Double?): Float {
        elbowAngle ?: return 0.4f
        val normalized = ((elbowAngle - 140.0) / (168.0 - 140.0)).coerceIn(0.0, 1.1)
        return normalized.toFloat().coerceIn(0f, 1f)
    }

    private fun coreScore(hipAlignment: Double?): Float {
        hipAlignment ?: return 0.5f
        val deviation = kotlin.math.abs(hipAlignment)
        val normalized = (1.0 - (deviation / 0.3)).coerceIn(0.0, 1.2)
        return normalized.toFloat().coerceIn(0f, 1f)
    }

    private fun buildDepthSignal(score: Float, depthDelta: Double): FormSignal {
        val message = when {
            score > 0.85f -> "Solid push-up depth"
            score > 0.6f -> "Go a touch deeper to nail the rep"
            else -> "Lower your chest closer to the ground"
        }
        val severity = when {
            score > 0.75f -> FormSeverity.INFO
            score > 0.5f -> FormSeverity.WARNING
            else -> FormSeverity.CRITICAL
        }
        return FormSignal(
            id = "depth",
            label = "Depth",
            score = score,
            severity = severity,
            message = message,
            weight = 1.3f
        )
    }

    private fun buildLockoutSignal(score: Float, elbowAngle: Double?): FormSignal {
        val message = when {
            score > 0.85f -> "Full elbow lockout"
            score > 0.6f -> "Squeeze to fully extend at the top"
            else -> "Press harder—lock your elbows out"
        }
        val severity = when {
            score > 0.75f -> FormSeverity.INFO
            score > 0.55f -> FormSeverity.WARNING
            else -> FormSeverity.CRITICAL
        }
        return FormSignal(
            id = "lockout",
            label = "Lockout",
            score = score,
            severity = severity,
            message = message,
            weight = 1f
        )
    }

    private fun buildCoreSignal(score: Float, hipAlignment: Double?): FormSignal {
        val message = when {
            score > 0.85f -> "Strong plank line"
            score > 0.6f -> "Keep hips level with shoulders"
            else -> "Tighten your core—avoid hip sag"
        }
        val severity = when {
            score > 0.75f -> FormSeverity.INFO
            score > 0.55f -> FormSeverity.WARNING
            else -> FormSeverity.CRITICAL
        }
        return FormSignal(
            id = "core",
            label = "Core",
            score = score,
            severity = severity,
            message = message,
            weight = 0.9f
        )
    }
}

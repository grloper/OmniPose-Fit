package com.grloepr.pushtrack.exercise

import android.graphics.PointF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Analyzes pose data to detect exercise reps and track exercise state.
 * The analyzer uses joint angles, normalized limb distances, and temporal smoothing so it adapts
 * to front/side camera setups (e.g. a phone on the floor in selfie mode) and keeps noise low.
 */
class ExerciseAnalyzer(
    private val exerciseType: ExerciseType,
    private val repListener: (Int) -> Unit
) {

    private var currentState: ExerciseState = ExerciseState.Waiting
    private var previousState: ExerciseState = ExerciseState.Waiting
    private var repCount = 0
    private var lastStateChangeTime = 0L
    private var lastMeasurementTime = 0L

    private val minStateDurationMs = 250L
    private val poseLossTimeoutMs = 700L

    // Push-up metrics
    private val pushupElbowSmoother = MeasurementSmoother(6)
    private val pushupDepthSmoother = MeasurementSmoother(6)
    private var pushupBaselineGap: Double? = null

    // Squat metrics
    private val squatKneeSmoother = MeasurementSmoother(6)
    private val squatDepthSmoother = MeasurementSmoother(6)

    fun analyzePose(pose: Pose) {
        val timestamp = System.currentTimeMillis()

        if (!hasValidPose(pose)) {
            handlePoseLost(timestamp)
            return
        }

        lastMeasurementTime = timestamp
        val candidateState = determineState(pose)
        val stabilizedState = stabilizeState(candidateState, timestamp)

        if (stabilizedState != currentState) {
            if (isRepTransition(currentState, stabilizedState)) {
                repCount++
                repListener(repCount)
            }

            previousState = currentState
            currentState = stabilizedState
            lastStateChangeTime = timestamp

            if (exerciseType == ExerciseType.PUSHUP && stabilizedState == ExerciseState.Up) {
                pushupBaselineGap = pushupDepthSmoother.current()?.let { smoothed ->
                    pushupBaselineGap?.let { existing -> existing * 0.8 + smoothed * 0.2 } ?: smoothed
                }
            }
        }
    }

    private fun stabilizeState(candidate: ExerciseState, timestamp: Long): ExerciseState {
        if (candidate == currentState) return currentState
        if (candidate == ExerciseState.Waiting) return ExerciseState.Waiting
        if (currentState == ExerciseState.Waiting) return candidate
        val elapsed = timestamp - lastStateChangeTime
        return if (elapsed >= minStateDurationMs) candidate else currentState
    }

    private fun handlePoseLost(timestamp: Long) {
        if (timestamp - lastMeasurementTime > poseLossTimeoutMs) {
            currentState = ExerciseState.Waiting
            previousState = ExerciseState.Waiting
            lastStateChangeTime = timestamp
            resetSmoothers()
        }
    }

    private fun resetSmoothers() {
        when (exerciseType) {
            ExerciseType.PUSHUP -> {
                pushupElbowSmoother.clear()
                pushupDepthSmoother.clear()
                pushupBaselineGap = null
            }
            ExerciseType.SQUAT -> {
                squatKneeSmoother.clear()
                squatDepthSmoother.clear()
            }
            ExerciseType.PULLUP -> Unit
        }
    }

    private fun isRepTransition(from: ExerciseState, to: ExerciseState): Boolean {
        if (from == ExerciseState.Waiting) return false
        return from == ExerciseState.Down && to == ExerciseState.Up
    }

    private fun determineState(pose: Pose): ExerciseState {
        return when (exerciseType) {
            ExerciseType.PUSHUP -> detectPushupState(pose)
            ExerciseType.SQUAT -> detectSquatState(pose)
            ExerciseType.PULLUP -> detectPullupState(pose)
        }
    }

    private fun detectPushupState(pose: Pose): ExerciseState {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)

        if (!hasValidLandmarks(leftShoulder, rightShoulder, leftElbow, rightElbow, leftHip, rightHip)) {
            return ExerciseState.Waiting
        }

        val shoulderPoint = averagePoint(listOf(leftShoulder, rightShoulder)) ?: return ExerciseState.Waiting
        val hipPoint = averagePoint(listOf(leftHip, rightHip)) ?: return ExerciseState.Waiting
        val supportPoint = averagePoint(listOf(leftWrist, rightWrist))
            ?: averagePoint(listOf(leftElbow, rightElbow))
            ?: return ExerciseState.Waiting

        val torsoLength = distance(shoulderPoint, hipPoint).coerceAtLeast(1.0)
        val normalizedGap = ((supportPoint.y - shoulderPoint.y) / torsoLength).coerceIn(0.0, 2.0)
        val smoothedGap = pushupDepthSmoother.add(normalizedGap)

        val elbowAngles = listOf(
            calculateAngle(leftShoulder, leftElbow, leftWrist),
            calculateAngle(rightShoulder, rightElbow, rightWrist)
        ).filterNotNull()

        val smoothedElbow = pushupElbowSmoother.add(elbowAngles.averageOrNull())

        val baseline = pushupBaselineGap ?: smoothedGap
        val depthDelta = if (baseline != null && smoothedGap != null) baseline - smoothedGap else 0.0

        val elbowDown = smoothedElbow != null && smoothedElbow < 118.0
        val elbowUp = smoothedElbow != null && smoothedElbow > 158.0

        val depthDown = smoothedGap != null && baseline != null && depthDelta > 0.12
        val depthRecovered = smoothedGap != null && baseline != null && smoothedGap > baseline - 0.05

        val fallbackDown = smoothedGap != null && smoothedGap < 0.35
        val fallbackUp = smoothedGap != null && smoothedGap > 0.45

        return when {
            (elbowDown && (depthDown || fallbackDown)) -> ExerciseState.Down
            (elbowUp && (depthRecovered || fallbackUp)) -> ExerciseState.Up
            else -> ExerciseState.Waiting
        }
    }

    private fun detectSquatState(pose: Pose): ExerciseState {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (!hasValidLandmarks(leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle)) {
            return ExerciseState.Waiting
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

        val smoothedKnee = squatKneeSmoother.add(selectedAngle)

        val hipPoint = averagePoint(listOf(leftHip, rightHip)) ?: return ExerciseState.Waiting
        val anklePoint = averagePoint(listOf(leftAnkle, rightAnkle)) ?: return ExerciseState.Waiting
        val shoulderPoint = averagePoint(listOf(leftShoulder, rightShoulder)) ?: hipPoint

        val legLength = abs(shoulderPoint.y - anklePoint.y).coerceAtLeast(1f)
        val hipDepth = abs(hipPoint.y - anklePoint.y) / legLength
        val smoothedHipDepth = squatDepthSmoother.add(hipDepth.toDouble())

        val downByAngle = smoothedKnee != null && smoothedKnee < 110.0
        val upByAngle = smoothedKnee != null && smoothedKnee > 155.0

        val downByDepth = smoothedHipDepth != null && smoothedHipDepth < 0.32
        val upByDepth = smoothedHipDepth != null && smoothedHipDepth > 0.42

        return when {
            downByAngle && downByDepth -> ExerciseState.Down
            upByAngle && upByDepth -> ExerciseState.Up
            else -> ExerciseState.Waiting
        }
    }

    private fun detectPullupState(pose: Pose): ExerciseState {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (!hasValidLandmarks(nose, leftShoulder, rightShoulder)) {
            return ExerciseState.Waiting
        }

        val noseY = nose!!.position.y
        val shoulderY = (leftShoulder!!.position.y + rightShoulder!!.position.y) / 2f

        return when {
            noseY < shoulderY - 12f -> ExerciseState.Up
            noseY > shoulderY + 12f -> ExerciseState.Down
            else -> ExerciseState.Waiting
        }
    }

    private fun hasValidPose(pose: Pose): Boolean {
        val required = when (exerciseType) {
            ExerciseType.PUSHUP -> listOf(
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_ELBOW,
                PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.LEFT_WRIST,
                PoseLandmark.RIGHT_WRIST,
                PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_HIP
            )
            ExerciseType.SQUAT -> listOf(
                PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE,
                PoseLandmark.RIGHT_KNEE,
                PoseLandmark.LEFT_ANKLE,
                PoseLandmark.RIGHT_ANKLE
            )
            ExerciseType.PULLUP -> listOf(
                PoseLandmark.NOSE,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.RIGHT_SHOULDER
            )
        }

        val visible = required.count { pose.getPoseLandmark(it)?.inFrameLikelihood ?: 0f > 0.45f }
        return visible >= required.size * 0.7f
    }

    private fun hasValidLandmarks(vararg landmarks: PoseLandmark?): Boolean {
        return landmarks.all { it != null && it.inFrameLikelihood > 0.45f }
    }

    private fun calculateAngle(a: PoseLandmark?, b: PoseLandmark?, c: PoseLandmark?): Double? {
        if (!hasValidLandmarks(a, b, c)) return null
        val pointA = a!!.position
        val pointB = b!!.position
        val pointC = c!!.position

        val ab = atan2(pointA.y - pointB.y, pointA.x - pointB.x)
        val cb = atan2(pointC.y - pointB.y, pointC.x - pointB.x)

        var angle = abs(Math.toDegrees((ab - cb).toDouble()))
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }

    private fun landmarkSetConfidence(vararg landmarks: PoseLandmark?): Float {
        return landmarks.map { it?.inFrameLikelihood ?: 0f }.minOrNull() ?: 0f
    }

    private fun averagePoint(landmarks: List<PoseLandmark?>, minConfidence: Float = 0.45f): PointF? {
        val valid = landmarks.filter { it != null && it.inFrameLikelihood >= minConfidence }
        if (valid.isEmpty()) return null
        val sumX = valid.sumOf { it!!.position.x.toDouble() }
        val sumY = valid.sumOf { it!!.position.y.toDouble() }
        return PointF((sumX / valid.size).toFloat(), (sumY / valid.size).toFloat())
    }

    private fun distance(a: PointF, b: PointF): Double {
        return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
    }

    fun reset() {
        repCount = 0
        currentState = ExerciseState.Waiting
        previousState = ExerciseState.Waiting
        lastStateChangeTime = 0L
        lastMeasurementTime = 0L
        resetSmoothers()
    }

    fun getCurrentRepCount(): Int = repCount
    fun getCurrentState(): ExerciseState = currentState

    private class MeasurementSmoother(private val windowSize: Int) {
        private val samples = ArrayDeque<Double>()

        fun add(value: Double?): Double? {
            if (value == null || value.isNaN()) return current()
            if (samples.size == windowSize) samples.removeFirst()
            samples.addLast(value)
            return current()
        }

        fun current(): Double? = if (samples.isEmpty()) null else samples.sumOf { it } / samples.size

        fun clear() = samples.clear()
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else sumOf { it } / size


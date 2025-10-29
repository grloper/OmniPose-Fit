package com.grloepr.pushtrack.exercise

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.exercise.detector.DetectorResult
import com.grloepr.pushtrack.exercise.detector.ExerciseDetector
import com.grloepr.pushtrack.exercise.detector.PushupDetector
import com.grloepr.pushtrack.exercise.detector.SquatDetector

class ExerciseAnalyzer(
    private val exerciseType: ExerciseType,
    private val analysisListener: (ExerciseAnalysis) -> Unit
) {

    private val detector: ExerciseDetector = when (exerciseType) {
        ExerciseType.PUSHUP -> PushupDetector()
        ExerciseType.SQUAT -> SquatDetector()
        ExerciseType.PULLUP -> object : ExerciseDetector {
            override fun analyze(pose: Pose, timestampMs: Long): DetectorResult {
                val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
                val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
                val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

                if (!hasValidLandmarks(nose, leftShoulder, rightShoulder)) {
                    return DetectorResult(
                        state = ExerciseState.Waiting,
                        repCompleted = false,
                        formFeedback = FormFeedback.lostPose(),
                        poseVisible = false
                    )
                }

                val noseY = nose!!.position.y
                val shoulderY = (leftShoulder!!.position.y + rightShoulder!!.position.y) / 2f

                val state = when {
                    noseY < shoulderY - 12f -> ExerciseState.Up
                    noseY > shoulderY + 12f -> ExerciseState.Down
                    else -> ExerciseState.Waiting
                }

                return DetectorResult(
                    state = state,
                    repCompleted = false,
                    formFeedback = FormFeedback.neutral("Stay smooth on the bar")
                )
            }

            override fun onPoseLost(timestampMs: Long) {}
            override fun reset() {}
        }
    }

    private var repCount = 0
    private var lastPoseTimestamp = 0L
    private val poseLossTimeoutMs = 900L

    fun analyzePose(pose: Pose) {
        val timestamp = System.currentTimeMillis()

        val detectorResult = if (hasCoreLandmarks(pose)) {
            val result = detector.analyze(pose, timestamp)
            if (!result.poseVisible) detector.onPoseLost(timestamp)
            result
        } else {
            detector.onPoseLost(timestamp)
            DetectorResult(
                state = ExerciseState.Waiting,
                repCompleted = false,
                formFeedback = FormFeedback.lostPose(),
                poseVisible = false
            )
        }

        processResult(detectorResult, timestamp)
    }

    fun onPoseMissing() {
        val timestamp = System.currentTimeMillis()
        detector.onPoseLost(timestamp)
        processResult(
            DetectorResult(
                state = ExerciseState.Waiting,
                repCompleted = false,
                formFeedback = FormFeedback.lostPose(),
                poseVisible = false
            ),
            timestamp
        )
    }

    fun reset() {
        repCount = 0
        lastPoseTimestamp = 0L
        detector.reset()
        analysisListener(
            ExerciseAnalysis(
                state = ExerciseState.Waiting,
                repCount = 0,
                repDelta = 0,
                form = FormFeedback.neutral(),
                poseVisible = false,
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    private fun processResult(result: DetectorResult, timestamp: Long) {
        var repDelta = 0
        if (result.repCompleted) {
            repCount += 1
            repDelta = 1
        } else if (!result.poseVisible && timestamp - lastPoseTimestamp > poseLossTimeoutMs) {
            detector.reset()
        }

        if (result.poseVisible) {
            lastPoseTimestamp = timestamp
        }

        analysisListener(
            ExerciseAnalysis(
                state = result.state,
                repCount = repCount,
                repDelta = repDelta,
                form = result.formFeedback,
                poseVisible = result.poseVisible,
                timestampMs = timestamp
            )
        )
    }

    private fun hasCoreLandmarks(pose: Pose): Boolean {
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
}



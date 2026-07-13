package com.grloepr.pushtrack.engine

enum class AlignmentStatus {
    /** Not enough of the athlete is visible to judge the camera angle. */
    SEARCHING,

    /** The athlete is visible but the camera perspective degrades tracking accuracy. */
    MISALIGNED,

    /** The camera captures the exercise's optimal plane — high-accuracy tracking. */
    OPTIMAL
}

data class CameraAlignment(
    val status: AlignmentStatus,
    /** 0..1 — how close the current perspective is to the ideal plane. */
    val score: Float,
    val headline: String,
    val detail: String
) {
    companion object {
        fun searching() = CameraAlignment(
            status = AlignmentStatus.SEARCHING,
            score = 0f,
            headline = "Scanning for athlete…",
            detail = "Step fully into the frame"
        )
    }
}

/**
 * Wraps [PoseValidator] with temporal smoothing and switch hysteresis so the
 * guidance banner never flickers while the athlete moves between frames.
 */
class AlignmentMonitor(
    private val plane: CameraPlane,
    private val dwellMs: Long = 600L
) {
    private val ratioSmoother = MeasurementSmoother(8)
    private var status = AlignmentStatus.SEARCHING
    private var candidate: AlignmentStatus? = null
    private var candidateSince = 0L

    fun reset() {
        ratioSmoother.clear()
        status = AlignmentStatus.SEARCHING
        candidate = null
        candidateSince = 0L
    }

    fun update(pose: PoseSnapshot, timestampMs: Long): CameraAlignment {
        val rawRatio = when (plane) {
            CameraPlane.SAGITTAL -> PoseValidator.lateralCompressionRatio(pose)
            CameraPlane.FRONTAL -> PoseValidator.frontalSeparationRatio(pose)
            CameraPlane.ANY -> null
        }

        if (plane == CameraPlane.ANY) {
            status = AlignmentStatus.OPTIMAL
            return alignmentFor(score = 1f)
        }

        if (rawRatio == null) {
            ratioSmoother.clear()
            applyCandidate(AlignmentStatus.SEARCHING, timestampMs)
            return alignmentFor(score = 0f)
        }

        val ratio = ratioSmoother.add(rawRatio) ?: rawRatio
        val target: AlignmentStatus
        val score: Float
        when (plane) {
            CameraPlane.SAGITTAL -> {
                val threshold = PoseValidator.PROFILE_COMPRESSION_THRESHOLD.toDouble()
                // Hysteresis band: harder to lose OPTIMAL than to gain it.
                val limit = if (status == AlignmentStatus.OPTIMAL) threshold * 1.35 else threshold
                target = if (ratio < limit) AlignmentStatus.OPTIMAL else AlignmentStatus.MISALIGNED
                score = (1.0 - ratio / (threshold * 3.5)).toFloat().coerceIn(0f, 1f)
            }

            else -> {
                val threshold = PoseValidator.FRONTAL_SEPARATION_THRESHOLD.toDouble()
                val limit = if (status == AlignmentStatus.OPTIMAL) threshold * 0.75 else threshold
                target = if (ratio > limit) AlignmentStatus.OPTIMAL else AlignmentStatus.MISALIGNED
                score = (ratio / (threshold * 1.5)).toFloat().coerceIn(0f, 1f)
            }
        }

        applyCandidate(target, timestampMs)
        return alignmentFor(score)
    }

    /** Requires a status change to persist for [dwellMs] before committing it. */
    private fun applyCandidate(target: AlignmentStatus, timestampMs: Long) {
        if (target == status) {
            candidate = null
            return
        }
        if (candidate != target) {
            candidate = target
            candidateSince = timestampMs
        } else if (timestampMs - candidateSince >= dwellMs) {
            status = target
            candidate = null
        }
    }

    private fun alignmentFor(score: Float): CameraAlignment = when (status) {
        AlignmentStatus.SEARCHING -> CameraAlignment.searching()

        AlignmentStatus.MISALIGNED -> CameraAlignment(
            status = status,
            score = score,
            headline = "⚠️ Suboptimal angle for ${if (plane == CameraPlane.SAGITTAL) "depth" else "symmetry"} tracking.",
            detail = if (plane == CameraPlane.SAGITTAL) {
                "Please turn 90° to capture your side profile."
            } else {
                "Please face the camera straight on."
            }
        )

        AlignmentStatus.OPTIMAL -> CameraAlignment(
            status = status,
            score = score,
            headline = "Optimal tracking angle locked",
            detail = "High-accuracy ${if (plane == CameraPlane.SAGITTAL) "depth" else "symmetry"} capture active"
        )
    }
}

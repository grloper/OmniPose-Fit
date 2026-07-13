package com.grloepr.pushtrack.engine

import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.max

/**
 * Validates whether the camera perspective matches the plane an exercise needs.
 *
 * Geometry: in a true side-profile (sagittal) view the left/right joint pairs of
 * the hips and knees overlap along the horizontal axis, so their X separation is
 * tiny. Facing the camera spreads those pairs wide apart. We therefore measure
 * lateral "compression" — matched-pair X deltas normalised by torso length — and
 * compare it against per-plane thresholds.
 */
object PoseValidator {

    /** Max normalised L/R separation that still reads as a clean side profile. */
    const val PROFILE_COMPRESSION_THRESHOLD = 0.15f

    /** Min normalised L/R separation required to count as squarely facing the camera. */
    const val FRONTAL_SEPARATION_THRESHOLD = 0.35f

    /**
     * Original structural utility (see architecture package): expects coordinates
     * normalised to body scale. Low delta means matching joints overlap — the
     * signature of a side-profile view.
     */
    fun isSagittalPlaneOptimal(
        leftHipX: Float, rightHipX: Float,
        leftKneeX: Float, rightKneeX: Float
    ): Boolean {
        val hipDelta = abs(leftHipX - rightHipX)
        val kneeDelta = abs(leftKneeX - rightKneeX)

        // Low delta indicates profile view alignment, where matching joints obscure or overlap closely.
        return hipDelta < PROFILE_COMPRESSION_THRESHOLD && kneeDelta < PROFILE_COMPRESSION_THRESHOLD
    }

    /**
     * Lateral compression of the lower body (hips + knees), normalised by torso
     * length so the metric is invariant to how far the athlete stands from the
     * lens. ~0.05 in profile, ~0.5+ when facing the camera. Null when the joints
     * needed for the measurement aren't confidently visible.
     */
    fun lateralCompressionRatio(pose: PoseSnapshot): Double? {
        val leftHip = pose[PoseLandmark.LEFT_HIP]
        val rightHip = pose[PoseLandmark.RIGHT_HIP]
        val leftKnee = pose[PoseLandmark.LEFT_KNEE]
        val rightKnee = pose[PoseLandmark.RIGHT_KNEE]
        val torso = torsoLength(pose) ?: return null

        if (!leftHip.isConfident(MIN_CONFIDENCE) || !rightHip.isConfident(MIN_CONFIDENCE) ||
            !leftKnee.isConfident(MIN_CONFIDENCE) || !rightKnee.isConfident(MIN_CONFIDENCE)
        ) return null

        val hipDelta = abs(leftHip!!.x - rightHip!!.x) / torso
        val kneeDelta = abs(leftKnee!!.x - rightKnee!!.x) / torso
        return max(hipDelta, kneeDelta).toDouble()
    }

    /**
     * Lateral separation of the upper body (shoulders + hips) normalised by torso
     * length — the frontal-plane counterpart used for movements like pull-ups.
     */
    fun frontalSeparationRatio(pose: PoseSnapshot): Double? {
        val leftShoulder = pose[PoseLandmark.LEFT_SHOULDER]
        val rightShoulder = pose[PoseLandmark.RIGHT_SHOULDER]
        val leftHip = pose[PoseLandmark.LEFT_HIP]
        val rightHip = pose[PoseLandmark.RIGHT_HIP]
        val torso = torsoLength(pose) ?: return null

        if (!leftShoulder.isConfident(MIN_CONFIDENCE) || !rightShoulder.isConfident(MIN_CONFIDENCE) ||
            !leftHip.isConfident(MIN_CONFIDENCE) || !rightHip.isConfident(MIN_CONFIDENCE)
        ) return null

        val shoulderDelta = abs(leftShoulder!!.x - rightShoulder!!.x) / torso
        val hipDelta = abs(leftHip!!.x - rightHip!!.x) / torso
        return max(shoulderDelta, hipDelta).toDouble()
    }

    private fun torsoLength(pose: PoseSnapshot): Float? {
        val leftShoulder = pose[PoseLandmark.LEFT_SHOULDER]
        val rightShoulder = pose[PoseLandmark.RIGHT_SHOULDER]
        val leftHip = pose[PoseLandmark.LEFT_HIP]
        val rightHip = pose[PoseLandmark.RIGHT_HIP]
        if (!leftShoulder.isConfident(MIN_CONFIDENCE) || !rightShoulder.isConfident(MIN_CONFIDENCE) ||
            !leftHip.isConfident(MIN_CONFIDENCE) || !rightHip.isConfident(MIN_CONFIDENCE)
        ) return null

        val (sx, sy) = midpoint(leftShoulder!!, rightShoulder!!)
        val (hx, hy) = midpoint(leftHip!!, rightHip!!)
        val length = distance(Pair(sx, sy), Pair(hx, hy)).toFloat()
        return if (length < 1f) null else length
    }

    private const val MIN_CONFIDENCE = 0.4f
}

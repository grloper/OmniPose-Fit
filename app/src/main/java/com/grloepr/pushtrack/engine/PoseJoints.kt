package com.grloepr.pushtrack.engine

import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Bridges the human-readable joint identifiers used by exercise JSON schemas
 * (e.g. "LEFT_KNEE") to ML Kit [PoseLandmark] integer types.
 */
object PoseJoints {

    val byName: Map<String, Int> = mapOf(
        "NOSE" to PoseLandmark.NOSE,
        "LEFT_EYE_INNER" to PoseLandmark.LEFT_EYE_INNER,
        "LEFT_EYE" to PoseLandmark.LEFT_EYE,
        "LEFT_EYE_OUTER" to PoseLandmark.LEFT_EYE_OUTER,
        "RIGHT_EYE_INNER" to PoseLandmark.RIGHT_EYE_INNER,
        "RIGHT_EYE" to PoseLandmark.RIGHT_EYE,
        "RIGHT_EYE_OUTER" to PoseLandmark.RIGHT_EYE_OUTER,
        "LEFT_EAR" to PoseLandmark.LEFT_EAR,
        "RIGHT_EAR" to PoseLandmark.RIGHT_EAR,
        "LEFT_MOUTH" to PoseLandmark.LEFT_MOUTH,
        "RIGHT_MOUTH" to PoseLandmark.RIGHT_MOUTH,
        "LEFT_SHOULDER" to PoseLandmark.LEFT_SHOULDER,
        "RIGHT_SHOULDER" to PoseLandmark.RIGHT_SHOULDER,
        "LEFT_ELBOW" to PoseLandmark.LEFT_ELBOW,
        "RIGHT_ELBOW" to PoseLandmark.RIGHT_ELBOW,
        "LEFT_WRIST" to PoseLandmark.LEFT_WRIST,
        "RIGHT_WRIST" to PoseLandmark.RIGHT_WRIST,
        "LEFT_PINKY" to PoseLandmark.LEFT_PINKY,
        "RIGHT_PINKY" to PoseLandmark.RIGHT_PINKY,
        "LEFT_INDEX" to PoseLandmark.LEFT_INDEX,
        "RIGHT_INDEX" to PoseLandmark.RIGHT_INDEX,
        "LEFT_THUMB" to PoseLandmark.LEFT_THUMB,
        "RIGHT_THUMB" to PoseLandmark.RIGHT_THUMB,
        "LEFT_HIP" to PoseLandmark.LEFT_HIP,
        "RIGHT_HIP" to PoseLandmark.RIGHT_HIP,
        "LEFT_KNEE" to PoseLandmark.LEFT_KNEE,
        "RIGHT_KNEE" to PoseLandmark.RIGHT_KNEE,
        "LEFT_ANKLE" to PoseLandmark.LEFT_ANKLE,
        "RIGHT_ANKLE" to PoseLandmark.RIGHT_ANKLE,
        "LEFT_HEEL" to PoseLandmark.LEFT_HEEL,
        "RIGHT_HEEL" to PoseLandmark.RIGHT_HEEL,
        "LEFT_FOOT_INDEX" to PoseLandmark.LEFT_FOOT_INDEX,
        "RIGHT_FOOT_INDEX" to PoseLandmark.RIGHT_FOOT_INDEX,
    )

    /** Resolves a schema joint name, throwing a descriptive error for typos in schema files. */
    fun require(name: String): Int =
        byName[name.trim().uppercase()]
            ?: throw IllegalArgumentException("Unknown joint '$name' in exercise schema")
}

package com.grloepr.pushtrack.engine

import kotlin.math.abs

/** Canonical state names used by exercise schema files. */
object SchemaStates {
    const val START = "START"
    const val INFLECTION = "INFLECTION_POINT"
    const val END = "END"
}

/**
 * A named joint triple whose inner angle at [vertexId] is tracked
 * (e.g. HIP–KNEE–ANKLE for a squat's primary angle).
 */
data class JointAngleDefinition(
    val name: String,
    val startId: Int,
    val vertexId: Int,
    val endId: Int,
    val startName: String,
    val vertexName: String,
    val endName: String
) {
    val jointIds: List<Int> get() = listOf(startId, vertexId, endId)
}

/**
 * Declarative bound on a tracked angle. Supports the schema keys
 * `min`, `max`, `less_than`, `greater_than` — all optional, AND-combined.
 */
data class AngleConstraint(
    val min: Double? = null,
    val max: Double? = null,
    val lessThan: Double? = null,
    val greaterThan: Double? = null
) {
    fun isSatisfied(angle: Double): Boolean {
        if (min != null && angle < min) return false
        if (max != null && angle > max) return false
        if (lessThan != null && angle >= lessThan) return false
        if (greaterThan != null && angle <= greaterThan) return false
        return true
    }

    /** Representative boundary used to normalise movement progress. */
    val referenceValue: Double?
        get() = min ?: greaterThan ?: lessThan ?: max
}

/** One state of the movement state machine: every listed angle constraint must hold. */
data class SchemaState(
    val constraints: Map<String, AngleConstraint>
)

/**
 * A fully parsed, self-describing exercise definition. The tracking engine is
 * generic — it consumes these schemas to evaluate joint configurations for any
 * arbitrary movement, with no hardcoded routines.
 */
data class ExerciseSchema(
    val id: String,
    val displayName: String,
    val targetMuscles: List<String>,
    val trackingAngles: Map<String, JointAngleDefinition>,
    val states: Map<String, SchemaState>,
    val optimalPlane: CameraPlane,
    val requiredJointsVisible: List<Int>,
    val tempoPulseIntervalMs: Long,
    val masteryReps: Int
) {
    val primaryAngleName: String =
        if (trackingAngles.containsKey(PRIMARY_ANGLE)) PRIMARY_ANGLE else trackingAngles.keys.first()

    val primaryAngle: JointAngleDefinition
        get() = trackingAngles.getValue(primaryAngleName)

    /** Angle at the top of the movement (e.g. standing tall / arms locked out). */
    private val startReference: Double =
        states[SchemaStates.START]?.constraints?.get(primaryAngleName)?.referenceValue ?: 165.0

    /** Angle at the inflection point (e.g. bottom of the squat). */
    private val inflectionReference: Double =
        states[SchemaStates.INFLECTION]?.constraints?.get(primaryAngleName)
            ?.let { it.lessThan ?: it.max ?: it.greaterThan ?: it.min }
            ?: 90.0

    /**
     * Normalises the primary angle into movement progress: 0 at the START posture,
     * 1 at the INFLECTION_POINT. Works whether the angle decreases (squat, push-up)
     * or increases toward the inflection.
     */
    fun progressFor(primaryAngleValue: Double?): Float {
        primaryAngleValue ?: return 0f
        val span = startReference - inflectionReference
        if (abs(span) < 1e-3) return 0f
        return ((startReference - primaryAngleValue) / span).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val PRIMARY_ANGLE = "primary_angle"
    }
}

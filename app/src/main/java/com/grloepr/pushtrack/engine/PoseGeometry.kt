package com.grloepr.pushtrack.engine

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Shared geometric helpers for pose analysis. Pure Kotlin — no SDK types. */

const val DEFAULT_MIN_JOINT_CONFIDENCE = 0.45f

fun JointPoint?.isConfident(minConfidence: Float = DEFAULT_MIN_JOINT_CONFIDENCE): Boolean =
    this != null && confidence >= minConfidence

fun midpoint(a: JointPoint, b: JointPoint): Pair<Float, Float> =
    Pair((a.x + b.x) / 2f, (a.y + b.y) / 2f)

fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Double =
    hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble())

/**
 * Inner angle (0..180°) at vertex [b] formed by segments b→a and b→c.
 * Returns null when any joint is missing or below confidence.
 */
fun calculateAngle(
    a: JointPoint?,
    b: JointPoint?,
    c: JointPoint?,
    minConfidence: Float = DEFAULT_MIN_JOINT_CONFIDENCE
): Double? {
    if (!a.isConfident(minConfidence) || !b.isConfident(minConfidence) || !c.isConfident(minConfidence)) {
        return null
    }
    a!!; b!!; c!!

    val ab = atan2(a.y - b.y, a.x - b.x)
    val cb = atan2(c.y - b.y, c.x - b.x)

    var angle = abs(Math.toDegrees((ab - cb).toDouble()))
    if (angle > 180.0) angle = 360.0 - angle
    return angle
}

/** Rolling-average smoother that tolerates missing samples. */
class MeasurementSmoother(private val windowSize: Int) {
    private val samples = ArrayDeque<Double>()

    fun add(value: Double?): Double? {
        if (value == null || value.isNaN()) return current()
        if (samples.size == windowSize) samples.removeFirst()
        samples.addLast(value)
        return current()
    }

    fun current(): Double? = if (samples.isEmpty()) null else samples.sum() / samples.size

    fun clear() = samples.clear()
}

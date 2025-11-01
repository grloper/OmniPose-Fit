package com.grloepr.pushtrack.exercise

import android.graphics.PointF
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

fun hasValidLandmarks(vararg landmarks: PoseLandmark?, minConfidence: Float = 0.45f): Boolean {
    return landmarks.all { it != null && it.inFrameLikelihood >= minConfidence }
}

fun landmarkSetConfidence(vararg landmarks: PoseLandmark?): Float {
    return landmarks.map { it?.inFrameLikelihood ?: 0f }.minOrNull() ?: 0f
}

fun averagePoint(landmarks: List<PoseLandmark?>, minConfidence: Float = 0.45f): PointF? {
    val valid = landmarks.filter { it != null && it.inFrameLikelihood >= minConfidence }
    if (valid.isEmpty()) return null
    val sumX = valid.sumOf { it!!.position.x.toDouble() }
    val sumY = valid.sumOf { it!!.position.y.toDouble() }
    return PointF((sumX / valid.size).toFloat(), (sumY / valid.size).toFloat())
}

fun distance(a: PointF, b: PointF): Double {
    return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
}

fun calculateAngle(a: PoseLandmark?, b: PoseLandmark?, c: PoseLandmark?): Double? {
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

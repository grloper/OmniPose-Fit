package com.grloepr.pushtrack.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import com.grloepr.pushtrack.engine.EnginePhase
import com.grloepr.pushtrack.engine.ExerciseSchema
import com.grloepr.pushtrack.ui.theme.AchievementGold
import com.grloepr.pushtrack.ui.theme.ElectricCyan
import com.grloepr.pushtrack.ui.theme.NeonViolet
import com.grloepr.pushtrack.ui.theme.VoltLime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Camera-space overlay driven by the exercise schema: renders the full detected
 * skeleton faintly, then lights up exactly the joint chains the schema tracks,
 * with a live angle arc + readout at the primary vertex. Colors follow the
 * engine phase so the athlete "feels" the state machine.
 */
@Composable
fun SchemaTrackingOverlay(
    poseResult: PoseDetectionResult,
    schema: ExerciseSchema,
    phase: EnginePhase,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier,
    repFlash: Float = 0f
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val mapper = PoseCoordinateMapper(
            imageWidth = poseResult.imageWidth,
            imageHeight = poseResult.imageHeight,
            rotationDegrees = poseResult.rotationDegrees,
            isFrontCamera = isFrontCamera,
            canvasWidth = size.width,
            canvasHeight = size.height
        )

        drawBaseSkeleton(poseResult.pose, mapper)

        val accent = phaseAccent(phase)
        val flashAccent = lerpColor(accent, AchievementGold, repFlash.coerceIn(0f, 1f))
        drawTrackedChains(poseResult.pose, schema, mapper, flashAccent)
        drawPrimaryAngle(poseResult.pose, schema, mapper, flashAccent, textMeasurer)
    }
}

private fun phaseAccent(phase: EnginePhase): Color = when (phase) {
    EnginePhase.SEARCHING -> Color(0xFF6B7A99)
    EnginePhase.READY -> ElectricCyan
    EnginePhase.ECCENTRIC -> ElectricCyan
    EnginePhase.BOTTOM -> NeonViolet
    EnginePhase.CONCENTRIC -> VoltLime
}

private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t
)

private val BASE_CONNECTIONS = listOf(
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
    PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
    PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
    PoseLandmark.LEFT_HEEL to PoseLandmark.LEFT_FOOT_INDEX,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
    PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
    PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
    PoseLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_FOOT_INDEX
)

private const val MIN_LIKELIHOOD = 0.35f

private fun DrawScope.mapLandmark(
    pose: Pose,
    type: Int,
    mapper: PoseCoordinateMapper
): Offset? {
    val landmark = pose.getPoseLandmark(type) ?: return null
    if (landmark.inFrameLikelihood < MIN_LIKELIHOOD) return null
    return mapper.map(landmark.position.x, landmark.position.y)
}

private fun DrawScope.drawBaseSkeleton(pose: Pose, mapper: PoseCoordinateMapper) {
    val stroke = 1.5.dp.toPx()
    val boneColor = Color.White.copy(alpha = 0.35f)
    BASE_CONNECTIONS.forEach { (a, b) ->
        val start = mapLandmark(pose, a, mapper) ?: return@forEach
        val end = mapLandmark(pose, b, mapper) ?: return@forEach
        drawLine(boneColor, start, end, strokeWidth = stroke, cap = StrokeCap.Round)
    }
    BASE_CONNECTIONS.flatMap { listOf(it.first, it.second) }.distinct().forEach { type ->
        mapLandmark(pose, type, mapper)?.let { center ->
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 2.dp.toPx(), center = center)
        }
    }
}

private fun DrawScope.drawTrackedChains(
    pose: Pose,
    schema: ExerciseSchema,
    mapper: PoseCoordinateMapper,
    accent: Color
) {
    val stroke = 3.5.dp.toPx()
    schema.trackingAngles.values.forEachIndexed { index, definition ->
        val isPrimary = definition.name == schema.primaryAngleName
        val chainColor = if (isPrimary) accent else NeonViolet.copy(alpha = 0.8f)
        val a = mapLandmark(pose, definition.startId, mapper)
        val b = mapLandmark(pose, definition.vertexId, mapper)
        val c = mapLandmark(pose, definition.endId, mapper)
        if (a == null || b == null || c == null) return@forEachIndexed

        val chainStroke = if (isPrimary) stroke else stroke * 0.6f
        drawLine(chainColor, a, b, strokeWidth = chainStroke, cap = StrokeCap.Round)
        drawLine(chainColor, b, c, strokeWidth = chainStroke, cap = StrokeCap.Round)

        listOf(a, c).forEach { end ->
            drawGlowingJoint(end, chainColor, radius = 5.dp.toPx())
        }
        drawGlowingJoint(b, chainColor, radius = if (isPrimary) 7.dp.toPx() else 5.dp.toPx())
    }
}

private fun DrawScope.drawGlowingJoint(center: Offset, color: Color, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.5f), Color.Transparent),
            center = center,
            radius = radius * 3f
        ),
        radius = radius * 3f,
        center = center
    )
    drawCircle(color = color, radius = radius, center = center)
    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = radius * 0.4f, center = center)
}

private fun DrawScope.drawPrimaryAngle(
    pose: Pose,
    schema: ExerciseSchema,
    mapper: PoseCoordinateMapper,
    accent: Color,
    textMeasurer: TextMeasurer
) {
    val definition = schema.primaryAngle
    val a = mapLandmark(pose, definition.startId, mapper) ?: return
    val b = mapLandmark(pose, definition.vertexId, mapper) ?: return
    val c = mapLandmark(pose, definition.endId, mapper) ?: return

    val angleToA = Math.toDegrees(atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble()))
    val angleToC = Math.toDegrees(atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble()))

    var innerAngle = kotlin.math.abs(angleToA - angleToC)
    if (innerAngle > 180.0) innerAngle = 360.0 - innerAngle

    // Pick the sweep that traces the inner angle.
    var start = angleToA
    var sweep = angleToC - angleToA
    if (sweep > 180.0) sweep -= 360.0
    if (sweep < -180.0) sweep += 360.0

    val armLength = min(
        (a - b).getDistance(),
        (c - b).getDistance()
    )
    val radius = min(28.dp.toPx(), armLength * 0.45f)
    if (radius < 8.dp.toPx()) return

    drawArc(
        color = accent.copy(alpha = 0.22f),
        startAngle = start.toFloat(),
        sweepAngle = sweep.toFloat(),
        useCenter = true,
        topLeft = Offset(b.x - radius, b.y - radius),
        size = Size(radius * 2f, radius * 2f)
    )
    drawArc(
        color = accent,
        startAngle = start.toFloat(),
        sweepAngle = sweep.toFloat(),
        useCenter = false,
        topLeft = Offset(b.x - radius, b.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )

    // Degree badge along the bisector, pushed outside the arc.
    val bisector = Math.toRadians(start + sweep / 2.0)
    val labelAnchor = Offset(
        b.x + (radius * 2.1f) * cos(bisector).toFloat(),
        b.y + (radius * 2.1f) * sin(bisector).toFloat()
    )
    val label = "${innerAngle.roundToInt()}°"
    val layout = textMeasurer.measure(
        label,
        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    )
    val padding = 4.dp.toPx()
    val bgTopLeft = Offset(
        labelAnchor.x - layout.size.width / 2f - padding,
        labelAnchor.y - layout.size.height / 2f - padding
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = bgTopLeft,
        size = Size(layout.size.width + padding * 2, layout.size.height + padding * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            labelAnchor.x - layout.size.width / 2f,
            labelAnchor.y - layout.size.height / 2f
        )
    )
}

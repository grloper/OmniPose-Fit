package com.grloepr.pushtrack.anatomy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.ui.theme.ElectricCyan
import com.grloepr.pushtrack.ui.theme.NeonViolet
import com.grloepr.pushtrack.ui.theme.TextFaint
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Stylized vector anatomy model. Renders front + back skeletal figures and
 * paints muscle-group regions directly onto them — targeted muscles glow with
 * a breathing accent instead of being listed as text.
 *
 * Tap any region to inspect it via [onMuscleTapped].
 */
@Composable
fun AnatomyCanvas(
    highlighted: Set<MuscleGroup>,
    modifier: Modifier = Modifier,
    showBackView: Boolean = true,
    selected: MuscleGroup? = null,
    accent: Color = ElectricCyan,
    accentSecondary: Color = NeonViolet,
    highlightIntensity: Float = 1f,
    onMuscleTapped: ((MuscleGroup) -> Unit)? = null
) {
    val infinite = rememberInfiniteTransition(label = "musclePulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "musclePulseValue"
    )
    val textMeasurer = rememberTextMeasurer()

    val tapModifier = if (onMuscleTapped != null) {
        Modifier.pointerInput(showBackView) {
            detectTapGestures { tap ->
                hitTestMuscle(Size(size.width.toFloat(), size.height.toFloat()), tap, showBackView)
                    ?.let(onMuscleTapped)
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.then(tapModifier)) {
        val figures = figureLayouts(size, showBackView)
        figures.forEach { (view, rect) ->
            drawFigure(
                view = view,
                rect = rect,
                highlighted = highlighted,
                selected = selected,
                accent = accent,
                accentSecondary = accentSecondary,
                pulse = pulse,
                intensity = highlightIntensity.coerceIn(0f, 1f)
            )
            if (showBackView) {
                val label = if (view == FigureView.FRONT) "FRONT" else "BACK"
                val layout = textMeasurer.measure(
                    label,
                    TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                )
                drawText(
                    textLayoutResult = layout,
                    color = TextFaint,
                    topLeft = Offset(
                        rect.center.x - layout.size.width / 2f,
                        rect.bottom - layout.size.height
                    )
                )
            }
        }
    }
}

internal enum class FigureView { FRONT, BACK }

/**
 * A muscle region: an ellipse in figure space. Coordinates are expressed in
 * units of figure height, with x measured from the figure's vertical axis —
 * so shapes stay undistorted at any canvas size.
 */
private class MuscleBlob(
    val muscle: MuscleGroup,
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val rotationDeg: Float = 0f
)

/** Mirrors a blob across the figure's vertical axis. */
private fun MuscleBlob.mirrored() = MuscleBlob(muscle, -cx, cy, rx, ry, -rotationDeg)

private fun pair(
    muscle: MuscleGroup, cx: Float, cy: Float, rx: Float, ry: Float, rot: Float = 0f
): List<MuscleBlob> {
    val blob = MuscleBlob(muscle, cx, cy, rx, ry, rot)
    return listOf(blob, blob.mirrored())
}

private val FRONT_BLOBS: List<MuscleBlob> = buildList {
    addAll(pair(MuscleGroup.TRAPEZIUS, cx = 0.055f, cy = 0.150f, rx = 0.042f, ry = 0.020f, rot = 18f))
    addAll(pair(MuscleGroup.DELTOIDS, cx = 0.140f, cy = 0.190f, rx = 0.042f, ry = 0.052f, rot = 12f))
    addAll(pair(MuscleGroup.PECTORALS, cx = 0.062f, cy = 0.240f, rx = 0.058f, ry = 0.048f, rot = -8f))
    addAll(pair(MuscleGroup.BICEPS, cx = 0.160f, cy = 0.290f, rx = 0.030f, ry = 0.062f, rot = 14f))
    addAll(pair(MuscleGroup.FOREARMS, cx = 0.196f, cy = 0.410f, rx = 0.025f, ry = 0.062f, rot = 10f))
    add(MuscleBlob(MuscleGroup.CORE, cx = 0f, cy = 0.372f, rx = 0.055f, ry = 0.082f))
    addAll(pair(MuscleGroup.OBLIQUES, cx = 0.078f, cy = 0.385f, rx = 0.024f, ry = 0.062f, rot = -6f))
    addAll(pair(MuscleGroup.QUADRICEPS, cx = 0.068f, cy = 0.575f, rx = 0.045f, ry = 0.098f, rot = 2f))
    addAll(pair(MuscleGroup.CALVES, cx = 0.062f, cy = 0.775f, rx = 0.028f, ry = 0.070f, rot = 1f))
}

private val BACK_BLOBS: List<MuscleBlob> = buildList {
    add(MuscleBlob(MuscleGroup.TRAPEZIUS, cx = 0f, cy = 0.195f, rx = 0.075f, ry = 0.062f))
    addAll(pair(MuscleGroup.DELTOIDS, cx = 0.140f, cy = 0.190f, rx = 0.042f, ry = 0.052f, rot = 12f))
    addAll(pair(MuscleGroup.LATS, cx = 0.062f, cy = 0.315f, rx = 0.052f, ry = 0.088f, rot = -10f))
    addAll(pair(MuscleGroup.TRICEPS, cx = 0.160f, cy = 0.290f, rx = 0.030f, ry = 0.062f, rot = 14f))
    addAll(pair(MuscleGroup.FOREARMS, cx = 0.196f, cy = 0.410f, rx = 0.025f, ry = 0.062f, rot = 10f))
    add(MuscleBlob(MuscleGroup.LOWER_BACK, cx = 0f, cy = 0.425f, rx = 0.038f, ry = 0.050f))
    addAll(pair(MuscleGroup.GLUTES, cx = 0.048f, cy = 0.505f, rx = 0.046f, ry = 0.048f))
    addAll(pair(MuscleGroup.HAMSTRINGS, cx = 0.066f, cy = 0.630f, rx = 0.042f, ry = 0.090f, rot = 2f))
    addAll(pair(MuscleGroup.CALVES, cx = 0.062f, cy = 0.790f, rx = 0.030f, ry = 0.072f, rot = 1f))
}

private fun blobsFor(view: FigureView) =
    if (view == FigureView.FRONT) FRONT_BLOBS else BACK_BLOBS

/** Skeleton polyline segments in figure space (x from axis, y in figure heights). */
private val BONES: List<Pair<Pair<Float, Float>, Pair<Float, Float>>> = buildList {
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) = add(Pair(Pair(x1, y1), Pair(x2, y2)))
    // Spine + neck
    seg(0f, 0.118f, 0f, 0.175f)
    seg(0f, 0.175f, 0f, 0.455f)
    // Shoulder girdle
    seg(-0.135f, 0.185f, 0.135f, 0.185f)
    // Arms
    seg(-0.135f, 0.185f, -0.180f, 0.345f); seg(-0.180f, 0.345f, -0.210f, 0.475f)
    seg(0.135f, 0.185f, 0.180f, 0.345f); seg(0.180f, 0.345f, 0.210f, 0.475f)
    // Pelvis
    seg(-0.075f, 0.455f, 0.075f, 0.455f)
    // Legs
    seg(-0.075f, 0.455f, -0.070f, 0.675f); seg(-0.070f, 0.675f, -0.062f, 0.870f)
    seg(0.075f, 0.455f, 0.070f, 0.675f); seg(0.070f, 0.675f, 0.062f, 0.870f)
    // Feet
    seg(-0.062f, 0.870f, -0.095f, 0.895f)
    seg(0.062f, 0.870f, 0.095f, 0.895f)
}

private val JOINT_DOTS: List<Pair<Float, Float>> = listOf(
    Pair(-0.135f, 0.185f), Pair(0.135f, 0.185f),
    Pair(-0.180f, 0.345f), Pair(0.180f, 0.345f),
    Pair(-0.210f, 0.475f), Pair(0.210f, 0.475f),
    Pair(-0.075f, 0.455f), Pair(0.075f, 0.455f),
    Pair(-0.070f, 0.675f), Pair(0.070f, 0.675f),
    Pair(-0.062f, 0.870f), Pair(0.062f, 0.870f)
)

/** Width of a figure, expressed in figure heights. */
private const val FIGURE_ASPECT = 0.52f
private const val FIGURE_GAP_FRACTION = 0.10f

internal fun figureLayouts(size: Size, showBackView: Boolean): List<Pair<FigureView, Rect>> {
    if (size.width <= 0f || size.height <= 0f) return emptyList()
    val count = if (showBackView) 2 else 1
    val gap = if (showBackView) FIGURE_GAP_FRACTION else 0f
    // Figure height limited by canvas height and by available width per figure.
    val height = min(size.height, size.width / (count * FIGURE_ASPECT + gap))
    val width = height * FIGURE_ASPECT
    val totalWidth = count * width + gap * height
    val left = (size.width - totalWidth) / 2f
    val top = (size.height - height) / 2f
    return buildList {
        add(Pair(FigureView.FRONT, Rect(Offset(left, top), Size(width, height))))
        if (showBackView) {
            add(
                Pair(
                    FigureView.BACK,
                    Rect(Offset(left + width + gap * height, top), Size(width, height))
                )
            )
        }
    }
}

private fun Rect.mapPoint(x: Float, y: Float): Offset =
    Offset(center.x + x * height, top + y * height)

private fun DrawScope.drawFigure(
    view: FigureView,
    rect: Rect,
    highlighted: Set<MuscleGroup>,
    selected: MuscleGroup?,
    accent: Color,
    accentSecondary: Color,
    pulse: Float,
    intensity: Float
) {
    val h = rect.height
    val boneColor = TextFaint.copy(alpha = 0.55f)
    val boneStroke = h * 0.008f

    // Head
    val headCenter = rect.mapPoint(0f, 0.068f)
    drawCircle(
        color = boneColor,
        radius = h * 0.048f,
        center = headCenter,
        style = Stroke(width = boneStroke)
    )

    // Bones
    BONES.forEach { (a, b) ->
        drawLine(
            color = boneColor,
            start = rect.mapPoint(a.first, a.second),
            end = rect.mapPoint(b.first, b.second),
            strokeWidth = boneStroke,
            cap = StrokeCap.Round
        )
    }
    JOINT_DOTS.forEach { (x, y) ->
        drawCircle(color = boneColor, radius = h * 0.010f, center = rect.mapPoint(x, y))
    }

    // Muscle regions
    blobsFor(view).forEach { blob ->
        val center = rect.mapPoint(blob.cx, blob.cy)
        val rx = blob.rx * h
        val ry = blob.ry * h
        val isLit = blob.muscle in highlighted
        val isSelected = blob.muscle == selected

        rotate(degrees = blob.rotationDeg, pivot = center) {
            if (isLit && intensity > 0.01f) {
                val glowAlpha = (0.20f + 0.22f * pulse) * intensity
                // Outer glow halo
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent),
                        center = center,
                        radius = maxOf(rx, ry) * 1.9f
                    ),
                    topLeft = Offset(center.x - rx * 1.9f, center.y - ry * 1.9f),
                    size = Size(rx * 3.8f, ry * 3.8f)
                )
                // Vibrant body
                drawOval(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = (0.75f + 0.20f * pulse) * intensity),
                            accentSecondary.copy(alpha = (0.55f + 0.20f * pulse) * intensity)
                        ),
                        start = Offset(center.x - rx, center.y - ry),
                        end = Offset(center.x + rx, center.y + ry)
                    ),
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = Size(rx * 2f, ry * 2f)
                )
            } else {
                drawOval(
                    color = Color.White.copy(alpha = 0.045f),
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = Size(rx * 2f, ry * 2f)
                )
                drawOval(
                    color = boneColor.copy(alpha = 0.35f),
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = h * 0.004f)
                )
            }
            if (isSelected) {
                drawOval(
                    color = accent,
                    topLeft = Offset(center.x - rx * 1.18f, center.y - ry * 1.18f),
                    size = Size(rx * 2.36f, ry * 2.36f),
                    style = Stroke(width = h * 0.008f)
                )
            }
        }
    }
}

/** Same layout math as rendering, inverted: which muscle sits under [tap]? */
internal fun hitTestMuscle(size: Size, tap: Offset, showBackView: Boolean): MuscleGroup? {
    figureLayouts(size, showBackView).forEach { (view, rect) ->
        val h = rect.height
        // Topmost-drawn wins: iterate in reverse draw order.
        blobsFor(view).asReversed().forEach { blob ->
            val center = rect.mapPoint(blob.cx, blob.cy)
            val rad = Math.toRadians(-blob.rotationDeg.toDouble())
            val dx = tap.x - center.x
            val dy = tap.y - center.y
            val localX = (dx * cos(rad) - dy * sin(rad)).toFloat()
            val localY = (dx * sin(rad) + dy * cos(rad)).toFloat()
            val rx = blob.rx * h * 1.2f // small touch padding
            val ry = blob.ry * h * 1.2f
            val norm = (localX / rx) * (localX / rx) + (localY / ry) * (localY / ry)
            if (norm <= 1f) return blob.muscle
        }
    }
    return null
}

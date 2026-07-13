package com.grloepr.pushtrack.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.grloepr.pushtrack.ui.theme.ElectricCyan

/**
 * AI-scanner styling shown while the engine searches for an athlete: corner
 * brackets frame the stage and a soft beam sweeps vertically.
 */
@Composable
fun ScannerViewfinder(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "scanner")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scannerSweep"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val inset = 36.dp.toPx()
        val armLength = 26.dp.toPx()
        val stroke = 3.dp.toPx()
        val color = ElectricCyan.copy(alpha = 0.85f)

        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset

        fun corner(cx: Float, cy: Float, dirX: Float, dirY: Float) {
            drawLine(
                color = color,
                start = Offset(cx, cy),
                end = Offset(cx + armLength * dirX, cy),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(cx, cy),
                end = Offset(cx, cy + armLength * dirY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        corner(left, top, 1f, 1f)
        corner(right, top, -1f, 1f)
        corner(left, bottom, 1f, -1f)
        corner(right, bottom, -1f, -1f)

        // Sweeping detection beam
        val beamY = top + (bottom - top) * sweep
        val beamHeight = 60.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    ElectricCyan.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                startY = beamY - beamHeight / 2f,
                endY = beamY + beamHeight / 2f
            ),
            topLeft = Offset(left, beamY - beamHeight / 2f),
            size = androidx.compose.ui.geometry.Size(right - left, beamHeight)
        )
        drawLine(
            color = ElectricCyan.copy(alpha = 0.5f),
            start = Offset(left, beamY),
            end = Offset(right, beamY),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

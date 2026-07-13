package com.grloepr.pushtrack.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grloepr.pushtrack.ui.theme.AbyssBlack
import com.grloepr.pushtrack.ui.theme.ElectricCyan
import com.grloepr.pushtrack.ui.theme.NeonViolet
import com.grloepr.pushtrack.ui.theme.SurfaceHigh
import com.grloepr.pushtrack.ui.theme.TextMuted
import kotlin.math.sin

/**
 * Embedded technique-preview placeholder: a cinematic 16:9 stage with an
 * animated rep-motion curve, a travelling motion dot and a breathing play
 * button — the slot a real technique video/animation drops into later.
 */
@Composable
fun VideoPlaceholder(
    title: String,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "videoPreview")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "motionSweep"
    )
    val playPulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playPulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(SurfaceHigh, AbyssBlack)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
    ) {
        // Animated rep-motion curve with a travelling dot.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val amplitude = size.height * 0.16f
            val midY = size.height * 0.58f
            val path = Path()
            val samples = 64
            for (i in 0..samples) {
                val t = i / samples.toFloat()
                val x = t * size.width
                val y = midY + amplitude * sin((t * 2f + 0.25f) * Math.PI * 2).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = 0.35f),
                        NeonViolet.copy(alpha = 0.35f)
                    )
                ),
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
            )

            val dotX = sweep * size.width
            val dotY = midY + amplitude * sin((sweep * 2f + 0.25f) * Math.PI * 2).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ElectricCyan.copy(alpha = 0.6f), Color.Transparent),
                    center = Offset(dotX, dotY),
                    radius = 26.dp.toPx()
                ),
                radius = 26.dp.toPx(),
                center = Offset(dotX, dotY)
            )
            drawCircle(color = ElectricCyan, radius = 5.dp.toPx(), center = Offset(dotX, dotY))
        }

        // PREVIEW tag
        Text(
            text = "PREVIEW",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = ElectricCyan,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(AbyssBlack.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )

        // Play button
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .scale(playPulse)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ElectricCyan.copy(alpha = 0.28f), Color.Transparent)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(ElectricCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play technique preview",
                    tint = AbyssBlack,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Bottom bar: title + faux timeline
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, AbyssBlack.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "$title · technique",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.width(140.dp),
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(10.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
            ) {
                drawLine(
                    color = Color.White.copy(alpha = 0.18f),
                    start = Offset(0f, center.y),
                    end = Offset(size.width, center.y),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = ElectricCyan,
                    start = Offset(0f, center.y),
                    end = Offset(size.width * sweep, center.y),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

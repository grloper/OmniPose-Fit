package com.grloepr.pushtrack.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grloepr.pushtrack.ui.theme.ElectricCyan
import com.grloepr.pushtrack.ui.theme.TextMuted

/**
 * Smooth tempo pacer: a ripple ring expands from a breathing core once per
 * [intervalMs], giving the athlete a metronome to sync reps against. Pairs
 * with [com.grloepr.pushtrack.audio.TempoTickPlayer] for the audio pip.
 */
@Composable
fun TempoPulseIndicator(
    intervalMs: Long,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    avgRepDurationMs: Long? = null
) {
    val infinite = rememberInfiniteTransition(label = "tempo")
    val cycle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = intervalMs.toInt().coerceAtLeast(600), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tempoCycle"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(52.dp)) {
                val maxRadius = size.minDimension / 2f
                val coreColor = if (active) ElectricCyan else TextMuted

                if (active) {
                    // Expanding ripple — restarts every tempo interval.
                    val rippleRadius = maxRadius * (0.35f + 0.65f * cycle)
                    val rippleAlpha = (1f - cycle) * 0.55f
                    drawCircle(
                        color = ElectricCyan.copy(alpha = rippleAlpha),
                        radius = rippleRadius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }

                // Breathing core dot
                val corePulse = if (active) {
                    0.85f + 0.30f * kotlin.math.sin(cycle * Math.PI).toFloat()
                } else {
                    0.8f
                }
                drawCircle(
                    color = coreColor.copy(alpha = 0.25f),
                    radius = maxRadius * 0.38f * corePulse
                )
                drawCircle(
                    color = coreColor,
                    radius = maxRadius * 0.20f * corePulse
                )
            }
        }
        Text(
            text = "TEMPO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing
        )
        Text(
            text = avgRepDurationMs?.let { formatSeconds(it) } ?: "${formatSeconds(intervalMs)} beat",
            style = MaterialTheme.typography.labelSmall,
            color = if (avgRepDurationMs != null) Color.White else TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp)
        )
    }
}

private fun formatSeconds(ms: Long): String {
    val seconds = ms / 1000.0
    return if (seconds >= 10.0) "${seconds.toInt()}s" else String.format(java.util.Locale.US, "%.1fs", seconds)
}

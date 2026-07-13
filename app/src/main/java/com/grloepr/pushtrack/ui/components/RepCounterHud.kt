package com.grloepr.pushtrack.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.ui.theme.ElectricCyan
import com.grloepr.pushtrack.ui.theme.NeonViolet
import com.grloepr.pushtrack.ui.theme.TextMuted
import com.grloepr.pushtrack.ui.theme.VoltLime

/**
 * The hero rep dial: a circular movement-depth gauge wrapping a large animated
 * rep count. The ring fills as the athlete descends toward the inflection
 * point and drains on the way up, making depth *felt* in real time.
 */
@Composable
fun RepCounterDial(
    repCount: Int,
    goalReps: Int,
    progress: Float,
    modifier: Modifier = Modifier,
    dialSize: Dp = 132.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "depthProgress"
    )
    val goalReached = goalReps > 0 && repCount >= goalReps

    Box(modifier = modifier.size(dialSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(dialSize)) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            // Track
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Depth fill
            if (animatedProgress > 0.005f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(ElectricCyan, NeonViolet, ElectricCyan),
                        center = center
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = repCount,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(240)) { height -> height } +
                        fadeIn(tween(240)) + scaleIn(initialScale = 0.75f, animationSpec = tween(240)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(180)) { height -> -height } +
                                fadeOut(tween(140))
                        )
                },
                label = "repCount"
            ) { count ->
                Text(
                    text = "$count",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    color = if (goalReached) VoltLime else Color.White
                )
            }
            Text(
                text = if (goalReps > 0) "of $goalReps reps" else "reps",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
        }
    }
}

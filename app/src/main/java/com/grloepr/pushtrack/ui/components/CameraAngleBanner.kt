package com.grloepr.pushtrack.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grloepr.pushtrack.engine.AlignmentStatus
import com.grloepr.pushtrack.engine.CameraAlignment
import com.grloepr.pushtrack.ui.theme.DeepSpace
import com.grloepr.pushtrack.ui.theme.SignalAmber
import com.grloepr.pushtrack.ui.theme.TextMuted
import com.grloepr.pushtrack.ui.theme.VoltLime
import kotlinx.coroutines.delay

/**
 * Intelligent spatial guidance banner. Misalignment surfaces as a prominent but
 * friendly amber overlay with a phone-rotation hint; when the angle becomes
 * optimal it morphs into a confirmation glow that then collapses into a slim
 * "locked" pill so it never crowds the workout.
 */
@Composable
fun CameraAngleBanner(
    alignment: CameraAlignment,
    modifier: Modifier = Modifier
) {
    // The optimal banner shows expanded for a moment, then collapses to a pill.
    var optimalExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(alignment.status) {
        if (alignment.status == AlignmentStatus.OPTIMAL) {
            optimalExpanded = true
            delay(2400)
            optimalExpanded = false
        }
    }

    AnimatedContent(
        targetState = alignment.status,
        transitionSpec = {
            (fadeIn(tween(260)) + scaleIn(initialScale = 0.92f, animationSpec = tween(260)))
                .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.95f, animationSpec = tween(180)))
        },
        label = "angleBanner",
        modifier = modifier
    ) { status ->
        when (status) {
            AlignmentStatus.SEARCHING -> SearchingPill(alignment)
            AlignmentStatus.MISALIGNED -> MisalignedBanner(alignment)
            AlignmentStatus.OPTIMAL -> OptimalBanner(alignment, expanded = optimalExpanded)
        }
    }
}

@Composable
private fun SearchingPill(alignment: CameraAlignment) {
    GlassPanel(shape = RoundedCornerShape(50)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = alignment.headline,
                style = MaterialTheme.typography.labelLarge,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun MisalignedBanner(alignment: CameraAlignment) {
    val infinite = rememberInfiniteTransition(label = "rotateHint")
    val wobble by infinite.animateFloat(
        initialValue = -12f,
        targetValue = 65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotateWobble"
    )
    val glow by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warnGlow"
    )

    Box(
        modifier = Modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SignalAmber.copy(alpha = 0.28f),
                        DeepSpace.copy(alpha = 0.92f)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .border(1.5.dp, SignalAmber.copy(alpha = glow), RoundedCornerShape(20.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ScreenRotation,
                contentDescription = null,
                tint = SignalAmber,
                modifier = Modifier
                    .size(30.dp)
                    .rotate(wobble)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = alignment.headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = alignment.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun OptimalBanner(alignment: CameraAlignment, expanded: Boolean) {
    val infinite = rememberInfiniteTransition(label = "optimalGlow")
    val glow by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "optimalGlowValue"
    )

    Box(
        modifier = Modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        VoltLime.copy(alpha = if (expanded) 0.24f else 0.14f),
                        DeepSpace.copy(alpha = 0.9f)
                    )
                ),
                RoundedCornerShape(if (expanded) 20.dp else 50.dp)
            )
            .border(
                1.5.dp,
                VoltLime.copy(alpha = glow),
                RoundedCornerShape(if (expanded) 20.dp else 50.dp)
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = if (expanded) 14.dp else 8.dp
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = VoltLime,
                modifier = Modifier.size(if (expanded) 26.dp else 16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (expanded) {
                Column {
                    Text(
                        text = alignment.headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = alignment.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            } else {
                Text(
                    text = "Angle locked · ${(alignment.score * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = VoltLime
                )
            }
        }
    }
}

/**
 * Full-screen ambient confirmation: soft accent light bleeding in from every
 * screen edge while the tracking angle is optimal.
 */
@Composable
fun OptimalEdgeGlow(
    visible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = VoltLime
) {
    val infinite = rememberInfiniteTransition(label = "edgeGlow")
    val pulse by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edgeGlowPulse"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(700)),
        exit = fadeOut(tween(500)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val depth = size.minDimension * 0.16f
                    val tint = color.copy(alpha = 0.22f * pulse)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(tint, Color.Transparent),
                            endY = depth
                        )
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, tint),
                            startY = size.height - depth
                        )
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(tint, Color.Transparent),
                            endX = depth
                        )
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, tint),
                            startX = size.width - depth
                        )
                    )
                }
        )
    }
}

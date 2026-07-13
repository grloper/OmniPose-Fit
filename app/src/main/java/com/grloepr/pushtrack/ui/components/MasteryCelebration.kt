package com.grloepr.pushtrack.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.ui.theme.AbyssBlack
import com.grloepr.pushtrack.ui.theme.AchievementGold
import com.grloepr.pushtrack.ui.theme.ElectricCyan
import com.grloepr.pushtrack.ui.theme.GoldDeep
import com.grloepr.pushtrack.ui.theme.NeonViolet
import com.grloepr.pushtrack.ui.theme.TextMuted
import com.grloepr.pushtrack.ui.theme.VoltLime
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class ConfettiParticle(
    val angleRad: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float
)

/**
 * Full-screen achievement moment fired when a skill's mastery goal is hit:
 * gold burst, confetti, XP chip and a route back to the tree.
 */
@Composable
fun MasteryCelebration(
    visible: Boolean,
    skillTitle: String,
    xpReward: Int,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350)),
        exit = fadeOut(tween(250)),
        modifier = modifier
    ) {
        val particles = remember {
            val palette = listOf(AchievementGold, ElectricCyan, NeonViolet, VoltLime)
            List(30) {
                ConfettiParticle(
                    angleRad = Random.nextFloat() * 2f * Math.PI.toFloat(),
                    speed = 0.35f + Random.nextFloat() * 0.65f,
                    size = 4f + Random.nextFloat() * 7f,
                    color = palette[Random.nextInt(palette.size)],
                    spin = Random.nextFloat() * 360f
                )
            }
        }
        val burst = remember { Animatable(0f) }
        LaunchedEffect(visible) {
            if (visible) {
                burst.snapTo(0f)
                burst.animateTo(1f, animationSpec = tween(1700, easing = FastOutSlowInEasing))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AbyssBlack.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            // Confetti burst
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = burst.value
                if (t > 0f) {
                    val reach = size.minDimension * 0.48f
                    particles.forEach { p ->
                        val dist = reach * p.speed * t
                        val x = center.x + dist * cos(p.angleRad)
                        val y = center.y + dist * sin(p.angleRad) + size.minDimension * 0.10f * t * t
                        val alpha = (1f - t).coerceIn(0f, 1f)
                        withTransform({
                            rotate(degrees = p.spin + 260f * t, pivot = Offset(x, y))
                        }) {
                            drawRoundRect(
                                color = p.color.copy(alpha = alpha),
                                topLeft = Offset(x - p.size, y - p.size / 2f),
                                size = androidx.compose.ui.geometry.Size(p.size * 2f, p.size),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(p.size / 3f)
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = scaleIn(
                        initialScale = 0.4f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn()
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        AchievementGold.copy(alpha = 0.45f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(AchievementGold, GoldDeep)
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MilitaryTech,
                                contentDescription = null,
                                tint = AbyssBlack,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = "SKILL MASTERED",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = AchievementGold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = skillTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "+$xpReward XP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VoltLime,
                    modifier = Modifier
                        .background(VoltLime.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "New paths unlocked on your skill tree",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AchievementGold,
                        contentColor = AbyssBlack
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Back to the tree",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

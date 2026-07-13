package com.grloepr.pushtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.grloepr.pushtrack.ui.theme.DeepSpace
import com.grloepr.pushtrack.ui.theme.SurfaceRaised

/**
 * Frosted glassmorphism container used by every floating HUD element:
 * translucent gradient fill, hairline gradient border, rounded silhouette.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    borderAccent: Color = Color.White.copy(alpha = 0.16f),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SurfaceRaised.copy(alpha = 0.82f),
                        DeepSpace.copy(alpha = 0.88f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(borderAccent, Color.White.copy(alpha = 0.04f))
                ),
                shape = shape
            ),
        content = content
    )
}

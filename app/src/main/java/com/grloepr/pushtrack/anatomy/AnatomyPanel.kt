package com.grloepr.pushtrack.anatomy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grloepr.pushtrack.ui.theme.ElectricCyan
import com.grloepr.pushtrack.ui.theme.SurfaceHigh
import com.grloepr.pushtrack.ui.theme.TextMuted

/**
 * Interactive anatomy inspector: front + back figures with targeted muscles lit,
 * plus a live caption chip that reacts to taps ("Quadriceps — targeted").
 */
@Composable
fun InteractiveAnatomyPanel(
    targeted: Set<MuscleGroup>,
    modifier: Modifier = Modifier,
    figureHeight: androidx.compose.ui.unit.Dp = 220.dp
) {
    var selected by remember { mutableStateOf<MuscleGroup?>(null) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnatomyCanvas(
            highlighted = targeted,
            selected = selected,
            showBackView = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(figureHeight),
            onMuscleTapped = { selected = if (selected == it) null else it }
        )
        Spacer(modifier = Modifier.height(10.dp))
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "muscleCaption"
        ) { muscle ->
            if (muscle == null) {
                Text(
                    text = "Tap a muscle to inspect · glowing = targeted",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            } else {
                val isTargeted = muscle in targeted
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(SurfaceHigh, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isTargeted) ElectricCyan else TextMuted,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTargeted) {
                            "${muscle.displayName} — targeted by this skill"
                        } else {
                            "${muscle.displayName} — supporting role"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

package com.grloepr.pushtrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grloepr.pushtrack.engine.EnginePhase
import com.grloepr.pushtrack.ui.theme.ElectricCyan
import com.grloepr.pushtrack.ui.theme.NeonViolet
import com.grloepr.pushtrack.ui.theme.TextFaint
import com.grloepr.pushtrack.ui.theme.TextMuted
import com.grloepr.pushtrack.ui.theme.VoltLime

/**
 * Visualises the schema state machine as a vertical journey:
 * Start → Descent → Bottom → Ascent, with the live phase lit up.
 * Hold schemas collapse it to Set → Holding.
 */
@Composable
fun StateMachineRibbon(
    phase: EnginePhase,
    modifier: Modifier = Modifier,
    isHold: Boolean = false
) {
    val steps = if (isHold) {
        listOf(EnginePhase.READY, EnginePhase.BOTTOM)
    } else {
        listOf(
            EnginePhase.READY,
            EnginePhase.ECCENTRIC,
            EnginePhase.BOTTOM,
            EnginePhase.CONCENTRIC
        )
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        steps.forEachIndexed { index, step ->
            PhaseStep(
                label = if (isHold) holdLabel(step) else step.label,
                color = stepColor(step),
                isActive = phase == step,
                isIdle = phase == EnginePhase.SEARCHING
            )
            if (index != steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .width(2.dp)
                        .height(10.dp)
                        .background(TextFaint.copy(alpha = 0.4f))
                )
            }
        }
    }
}

private fun holdLabel(phase: EnginePhase): String = when (phase) {
    EnginePhase.BOTTOM -> "Holding"
    else -> "Set"
}

private fun stepColor(phase: EnginePhase): Color = when (phase) {
    EnginePhase.BOTTOM -> NeonViolet
    EnginePhase.CONCENTRIC -> VoltLime
    else -> ElectricCyan
}

@Composable
private fun PhaseStep(
    label: String,
    color: Color,
    isActive: Boolean,
    isIdle: Boolean
) {
    val dotColor by animateColorAsState(
        targetValue = when {
            isActive -> color
            else -> TextFaint.copy(alpha = if (isIdle) 0.35f else 0.55f)
        },
        animationSpec = tween(220),
        label = "phaseDotColor"
    )
    val dotScale by animateFloatAsState(
        targetValue = if (isActive) 1.25f else 1f,
        animationSpec = tween(220),
        label = "phaseDotScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(dotScale)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) Color.White else TextMuted
        )
    }
}

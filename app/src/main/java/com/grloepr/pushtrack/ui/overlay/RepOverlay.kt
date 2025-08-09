package com.grloepr.pushtrack.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.domain.PushUpCounter

/**
 * Overlay that displays the current rep count and phase
 */
@Composable
fun RepOverlay(
    repCount: Int,
    phase: PushUpCounter.Phase,
    lastAngle: Float?,
    isTracking: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Rep count
            Text(
                text = "Reps: $repCount",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Current phase
            Text(
                text = when (phase) {
                    PushUpCounter.Phase.UP -> "UP"
                    PushUpCounter.Phase.DOWN -> "DOWN"
                },
                color = when (phase) {
                    PushUpCounter.Phase.UP -> Color.Green
                    PushUpCounter.Phase.DOWN -> Color.Red
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            // Debug info (if tracking)
            if (isTracking && lastAngle != null) {
                Text(
                    text = "Angle: ${lastAngle.toInt()}°",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            } else if (!isTracking) {
                Text(
                    text = "Pose not detected",
                    color = Color.Yellow,
                    fontSize = 12.sp
                )
            }
        }
    }
}
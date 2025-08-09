package com.grloepr.pushtrack.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.domain.PushUpCounter

/**
 * Optimized rep counter overlay for better performance
 */
@Composable
fun RepOverlay(
    repCount: Int,
    phase: PushUpCounter.Phase,
    lastAngle: Float?,
    isTracking: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rep count
            Text(
                text = "REPS: $repCount",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Show current phase with color highlight
            val phaseColor = when (phase) {
                PushUpCounter.Phase.DOWN -> Color(0xFFFF9800) // Orange for DOWN
                PushUpCounter.Phase.UP -> Color(0xFF4CAF50)   // Green for UP
            }
            
            Text(
                text = "PHASE: ${phase.name}",
                color = phaseColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Angle and tracking information
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Tracking status indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (isTracking) Color.Green else Color.Red,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Show current angle when tracking
                Text(
                    text = if (isTracking) {
                        "Elbow: ${String.format("%.1f°", lastAngle ?: 0f)}"
                    } else {
                        "Not Tracking"
                    },
                    color = if (isTracking) Color.White else Color.Red,
                    fontSize = 14.sp
                )
            }
        }
    }
}
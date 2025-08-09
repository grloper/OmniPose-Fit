package com.grloepr.pushtrack.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.domain.model.PushUpPhase

/**
 * Enhanced rep counter overlay with additional information
 */
@Composable
fun EnhancedRepOverlay(
    repCount: Int,
    phase: PushUpPhase,
    lastAngle: Float?,
    isTracking: Boolean,
    activeStrategy: String,
    confidence: Float,
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
            // Rep counter
            Text(
                text = "Reps: $repCount",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Phase indicator
            val phaseText = when (phase) {
                PushUpPhase.UP -> "UP"
                PushUpPhase.DOWN -> "DOWN"
                PushUpPhase.TRANSITIONING_UP -> "RISING"
                PushUpPhase.TRANSITIONING_DOWN -> "LOWERING"
            }
            
            val phaseColor = when (phase) {
                PushUpPhase.UP -> Color.Green
                PushUpPhase.DOWN -> Color.Red
                PushUpPhase.TRANSITIONING_UP -> Color.Cyan
                PushUpPhase.TRANSITIONING_DOWN -> Color.Yellow
            }
            
            Text(
                text = "Phase: $phaseText",
                color = phaseColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tracking status and angle
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isTracking) "Tracking" else "No Pose",
                    color = if (isTracking) Color.Green else Color.Red,
                    fontSize = 14.sp
                )
                
                lastAngle?.let {
                    Text(
                        text = "Angle: ${it.toInt()}°",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Active detection strategy
            Text(
                text = "Detection: $activeStrategy",
                color = Color.White,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Confidence indicator
            LinearProgressIndicator(
                progress = confidence,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = when {
                    confidence > 0.7f -> Color.Green
                    confidence > 0.4f -> Color.Yellow
                    else -> Color.Red
                },
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )
        }
    }
}

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
import com.grloepr.pushtrack.domain.GroundPositionPushUpDetector

/**
 * ULTRA-OPTIMIZED Rep overlay for ground-position push-up tracking
 * Shows comprehensive detection information for maximum transparency
 */
@Composable
fun GroundPositionRepOverlay(
    repCount: Int,
    phase: GroundPositionPushUpDetector.PushUpPhase,
    confidence: Float,
    detectionMethod: String,
    lastAngle: Float?,
    isTracking: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rep count (primary display)
            Text(
                text = "REPS: $repCount",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Phase indicator with color coding
            val phaseColor = when (phase) {
                GroundPositionPushUpDetector.PushUpPhase.UP -> Color.Green
                GroundPositionPushUpDetector.PushUpPhase.DOWN -> Color.Red
                GroundPositionPushUpDetector.PushUpPhase.TRANSITIONING -> Color.Yellow
            }
            
            Text(
                text = phase.name,
                color = phaseColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Confidence and tracking status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Conf: ${(confidence * 100).toInt()}%",
                    color = if (confidence > 0.7f) Color.Green else if (confidence > 0.4f) Color.Yellow else Color.Red,
                    fontSize = 14.sp
                )
                
                Text(
                    text = if (isTracking) "TRACKING" else "LOST",
                    color = if (isTracking) Color.Green else Color.Red,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Detection method and angle info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Method: ${detectionMethod.uppercase()}",
                    color = Color.Cyan,
                    fontSize = 12.sp
                )
                
                if (lastAngle != null) {
                    Text(
                        text = "Angle: ${lastAngle.toInt()}°",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * MINIMAL version for competitive training (less screen space)
 */
@Composable
fun MinimalGroundPositionRepOverlay(
    repCount: Int,
    phase: GroundPositionPushUpDetector.PushUpPhase,
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
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rep count
            Text(
                text = "$repCount",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Phase indicator (colored dot)
            val phaseColor = when (phase) {
                GroundPositionPushUpDetector.PushUpPhase.UP -> Color.Green
                GroundPositionPushUpDetector.PushUpPhase.DOWN -> Color.Red
                GroundPositionPushUpDetector.PushUpPhase.TRANSITIONING -> Color.Yellow
            }
            
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(phaseColor, RoundedCornerShape(6.dp))
            )
            
            // Tracking status
            if (!isTracking) {
                Text(
                    text = "LOST",
                    color = Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
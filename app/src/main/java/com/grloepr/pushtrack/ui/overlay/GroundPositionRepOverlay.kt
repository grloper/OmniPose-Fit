package com.grloepr.pushtrack.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.domain.GroundPositionPushUpDetector.PushUpPhase

/**
 * Enhanced Rep Counter overlay optimized for ground-position push-ups
 * Shows detailed info about the current rep state with confidence
 */
@Composable
fun GroundPositionRepOverlay(
    repCount: Int,
    phase: PushUpPhase,
    confidence: Float,
    detectionMethod: String,
    lastAngle: Float?,
    isTracking: Boolean,
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Push-Up Counter",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Reps: $repCount",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Phase indicator
            val phaseColor = when (phase) {
                PushUpPhase.UP -> Color.Green
                PushUpPhase.DOWN -> Color.Red
                PushUpPhase.TRANSITIONING -> Color.Yellow
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Phase:",
                    color = Color.White,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(phaseColor, RoundedCornerShape(8.dp))
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = when(phase) {
                        PushUpPhase.UP -> "Up"
                        PushUpPhase.DOWN -> "Down"
                        PushUpPhase.TRANSITIONING -> "Moving"
                    },
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            
            // Only show technical details if tracking
            if (isTracking) {
                Spacer(modifier = Modifier.height(4.dp))
                
                // Angle info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Angle:",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = lastAngle?.toInt()?.toString() ?: "N/A",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Detection method and confidence
                    val methodColor = when(detectionMethod) {
                        "elbow_angle" -> Color.Green
                        "head_height" -> Color.Yellow
                        "shoulder_width" -> Color.Cyan
                        else -> Color.Gray
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(methodColor, RoundedCornerShape(4.dp))
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Reset button
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.8f)
                ),
                modifier = Modifier.size(width = 80.dp, height = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset counter",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

package com.grloepr.pushtrack.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.domain.*

@Composable
fun EnhancedExerciseCounter(
    exerciseType: ExerciseType,
    repCount: Int,
    currentState: ExerciseState,
    formQuality: FormQuality?,
    confidence: Float,
    lastAngle: Float?,
    isTracking: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulse animation for tracking indicator
    val infiniteTransition = rememberInfiniteTransition(label = "tracking")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Card(
        modifier = modifier
            .width(200.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2D2D3F).copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Exercise type header
            Text(
                text = exerciseType.displayName.uppercase(),
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Rep count
            Text(
                text = repCount.toString(),
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "REPS",
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // State indicator with progress
            val stateColor = when (currentState) {
                ExerciseState.START_POSITION -> Color(0xFF4ECCA3) // Green
                ExerciseState.END_POSITION -> Color(0xFFFC5185) // Red  
                ExerciseState.TRANSITIONING -> Color(0xFFFCAA5E) // Orange
                ExerciseState.UNKNOWN -> Color(0xFF666666) // Gray
            }
            
            LinearProgressIndicator(
                progress = { confidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = stateColor,
                trackColor = Color(0xFF2D2D3F)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // State text
            Text(
                text = when (currentState) {
                    ExerciseState.START_POSITION -> getStartPositionText(exerciseType)
                    ExerciseState.END_POSITION -> getEndPositionText(exerciseType)
                    ExerciseState.TRANSITIONING -> "MOVING"
                    ExerciseState.UNKNOWN -> "READY"
                },
                color = stateColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Form quality indicator
            formQuality?.let { quality ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Form:",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    
                    val qualityColor = when {
                        quality.score >= 80 -> Color(0xFF4ECCA3)
                        quality.score >= 60 -> Color(0xFFFCAA5E)
                        else -> Color(0xFFFC5185)
                    }
                    
                    Text(
                        text = "${quality.score.toInt()}%",
                        color = qualityColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Technical details
            if (isTracking) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tracking status with pulse animation
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(pulseScale)
                                .background(Color(0xFF4ECCA3), CircleShape)
                        )
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        Text(
                            text = "TRACKING",
                            color = Color(0xFFCCCCCC),
                            fontSize = 10.sp
                        )
                    }
                    
                    // Confidence
                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        color = Color(0xFFCCCCCC),
                        fontSize = 10.sp
                    )
                }
                
                // Angle display (if available)
                lastAngle?.let { angle ->
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ANGLE: ${angle.toInt()}°",
                            color = Color(0xFFAAAAAA),
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reset button
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF424255)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset counter",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RESET",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun getStartPositionText(exerciseType: ExerciseType): String {
    return when (exerciseType) {
        ExerciseType.PUSH_UP -> "UP"
        ExerciseType.PULL_UP -> "HANGING"
        ExerciseType.SQUAT -> "STANDING"
    }
}

private fun getEndPositionText(exerciseType: ExerciseType): String {
    return when (exerciseType) {
        ExerciseType.PUSH_UP -> "DOWN"
        ExerciseType.PULL_UP -> "PULLED UP"
        ExerciseType.SQUAT -> "SQUATTING"
    }
}
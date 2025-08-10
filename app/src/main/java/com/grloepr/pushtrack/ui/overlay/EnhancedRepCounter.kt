package com.grloepr.pushtrack.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.domain.GroundPositionPushUpDetector.PushUpPhase
import kotlinx.coroutines.delay

/**
 * Enhanced and visually appealing rep counter overlay
 */
@Composable
fun EnhancedRepCounter(
    repCount: Int,
    phase: PushUpPhase,
    confidence: Float,
    lastAngle: Float?,
    isTracking: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation for rep count changes
    var prevCount by remember { mutableIntStateOf(0) }
    val animatedScale = remember { Animatable(1f) }
    
    LaunchedEffect(repCount) {
        if (repCount > prevCount) {
            animatedScale.snapTo(1.5f)
            animatedScale.animateTo(1f, animationSpec = tween(300))
        }
        prevCount = repCount
    }
    
    // Progress indicator animation for push-up phase
    val progress by animateFloatAsState(
        targetValue = when(phase) {
            PushUpPhase.UP -> 1f
            PushUpPhase.DOWN -> 0f
            PushUpPhase.TRANSITIONING -> 0.5f
        },
        animationSpec = tween(200)
    )
    
    // Pulse animation for tracking indicator
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with modern design
            Text(
                text = "PUSH-UP TRACKER",
                color = Color(0xFF4ECCA3),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Animated rep counter
            AnimatedContent(
                targetState = repCount,
                transitionSpec = {
                    slideInVertically { height -> height } + fadeIn() togetherWith
                    slideOutVertically { height -> -height } + fadeOut()
                },
                label = "countAnimation"
            ) { count ->
                Text(
                    text = count.toString(),
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.scale(animatedScale.value)
                )
            }
            
            Text(
                text = "REPS",
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Phase indicator with progress
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when (phase) {
                    PushUpPhase.UP -> Color(0xFF4ECCA3) // Green
                    PushUpPhase.DOWN -> Color(0xFFFC5185) // Red
                    PushUpPhase.TRANSITIONING -> Color(0xFFFCAA5E) // Orange
                },
                trackColor = Color(0xFF2D2D3F)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Phase text
            Text(
                text = when (phase) {
                    PushUpPhase.UP -> "UP"
                    PushUpPhase.DOWN -> "DOWN"
                    PushUpPhase.TRANSITIONING -> "MOVING"
                },
                color = when (phase) {
                    PushUpPhase.UP -> Color(0xFF4ECCA3)
                    PushUpPhase.DOWN -> Color(0xFFFC5185)
                    PushUpPhase.TRANSITIONING -> Color(0xFFFCAA5E)
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Technical details in a more compact form
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
                                .size(10.dp)
                                .scale(pulseScale)
                                .background(Color(0xFF4ECCA3), CircleShape)
                        )
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        Text(
                            text = "TRACKING",
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp
                        )
                    }
                    
                    // Confidence meter as percentage
                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp
                    )
                }
                
                // Angle display (if available)
                lastAngle?.let {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ANGLE: ${lastAngle.toInt()}°",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reset button with modern design
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF424255)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset counter",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "RESET",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

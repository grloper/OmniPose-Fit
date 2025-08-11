package com.grloepr.pushtrack.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Enhanced push-up counter with animations and form quality indicator
 */
@Composable
fun EnhancedPushUpCounter(
    repCount: Int,
    formQuality: Float, // 0-100
    averageFormQuality: Float,
    hasGoodForm: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var animatedCount by remember { mutableStateOf(0) }
    var animatedQuality by remember { mutableStateOf(0f) }
    
    // Animate count changes
    LaunchedEffect(repCount) {
        if (repCount > animatedCount) {
            while (animatedCount < repCount) {
                animatedCount++
                delay(100)
            }
        } else {
            animatedCount = repCount
        }
    }
    
    // Animate form quality
    LaunchedEffect(formQuality) {
        val animationDuration = 500
        val steps = 20
        val stepDelay = animationDuration / steps
        val stepSize = (formQuality - animatedQuality) / steps
        
        repeat(steps) {
            animatedQuality += stepSize
            delay(stepDelay.toLong())
        }
        animatedQuality = formQuality
    }
    
    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App title
            Text(
                text = "PushTrack",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Animated rep counter
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$animatedCount",
                    color = if (hasGoodForm) Color(0xFF4ECCA3) else Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (animatedCount == 1) "REP" else "REPS",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Form quality indicator
            FormQualityIndicator(
                quality = animatedQuality,
                averageQuality = averageFormQuality,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Controls row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset button
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * Form quality indicator with star rating and progress bar
 */
@Composable
private fun FormQualityIndicator(
    quality: Float,
    averageQuality: Float,
    modifier: Modifier = Modifier
) {
    val starRating = when {
        quality >= 90 -> 5
        quality >= 75 -> 4
        quality >= 60 -> 3
        quality >= 40 -> 2
        else -> 1
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Star rating
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(5) { index ->
                Icon(
                    imageVector = if (index < starRating) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (index < starRating) Color(0xFFFFD700) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Quality percentage
        Text(
            text = "Form: ${quality.toInt()}%",
            color = when {
                quality >= 75 -> Color(0xFF4ECCA3)
                quality >= 50 -> Color(0xFFFFD700)
                else -> Color(0xFFFF6B6B)
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Gray.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(quality / 100f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            quality >= 75 -> Color(0xFF4ECCA3)
                            quality >= 50 -> Color(0xFFFFD700)
                            else -> Color(0xFFFF6B6B)
                        }
                    )
            )
        }
        
        // Average quality
        if (averageQuality > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Avg: ${averageQuality.toInt()}%",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Voice feedback settings card
 */
@Composable
fun VoiceSettingsCard(
    isVoiceEnabled: Boolean,
    speechRate: Float,
    onVoiceToggle: (Boolean) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Voice toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isVoiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Voice feedback",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Voice Feedback",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Switch(
                    checked = isVoiceEnabled,
                    onCheckedChange = onVoiceToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4ECCA3),
                        checkedTrackColor = Color(0xFF4ECCA3).copy(alpha = 0.5f)
                    )
                )
            }
            
            if (isVoiceEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Speech rate slider
                Text(
                    text = "Speech Rate",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Slider(
                    value = speechRate,
                    onValueChange = onSpeechRateChange,
                    valueRange = 0.5f..2.0f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4ECCA3),
                        activeTrackColor = Color(0xFF4ECCA3),
                        inactiveTrackColor = Color.Gray
                    )
                )
                
                Text(
                    text = "${(speechRate * 100).toInt()}%",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Posture feedback display
 */
@Composable
fun PostureFeedbackDisplay(
    feedbackMessage: String?,
    isGoodForm: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = feedbackMessage != null,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        feedbackMessage?.let { message ->
            Card(
                modifier = modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGoodForm) 
                        Color(0xFF4ECCA3).copy(alpha = 0.9f) 
                    else 
                        Color(0xFFFF6B6B).copy(alpha = 0.9f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isGoodForm) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
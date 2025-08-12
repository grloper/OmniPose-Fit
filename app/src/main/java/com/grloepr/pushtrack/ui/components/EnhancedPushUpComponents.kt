package com.grloepr.pushtrack.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.grloepr.pushtrack.detection.ExerciseType
import com.grloepr.pushtrack.detection.ExerciseDetectorFactory
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Enhanced exercise counter with animations and form quality indicator (supports any exercise type)
 */
@Composable
fun EnhancedExerciseCounter(
    exerciseType: ExerciseType,
    repCount: Int,
    formQuality: Float,
    averageFormQuality: Float,
    hasGoodForm: Boolean,
    detectionConfidence: Float = 0f,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false // NEW
) {
    // Auto-compact if device height < 700dp
    val autoCompact = LocalConfiguration.current.screenHeightDp < 700
    val useCompact = compact || autoCompact

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
        modifier = modifier
            .padding(if (useCompact) 4.dp else 8.dp)
            .widthIn(max = if (useCompact) 200.dp else 260.dp),
        shape = RoundedCornerShape(if (useCompact) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = if (useCompact) 0.55f else 0.65f)
        ),
        elevation = CardDefaults.cardElevation(if (useCompact) 2.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(if (useCompact) 8.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Exercise type indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (useCompact) 4.dp else 8.dp)
            ) {
                Icon(
                    imageVector = getExerciseIcon(exerciseType),
                    contentDescription = null,
                    tint = Color(0xFF4ECCA3),
                    modifier = Modifier.size(if (useCompact) 18.dp else 24.dp)
                )
                Text(
                    text = ExerciseDetectorFactory.getDisplayName(exerciseType),
                    color = Color.White,
                    fontSize = if (useCompact) 14.sp else 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(if (useCompact) 4.dp else 8.dp))

            // Animated rep counter
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$animatedCount",
                    color = if (hasGoodForm) Color(0xFF4ECCA3) else Color.White,
                    fontSize = if (useCompact) 30.sp else 36.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = if (animatedCount == 1) "REP" else "REPS",
                    color = Color.Gray,
                    fontSize = if (useCompact) 10.sp else 12.sp,
                    modifier = Modifier.padding(bottom = if (useCompact) 4.dp else 6.dp)
                )
            }
            // Inline minimal tip
            Text(
                text = when (exerciseType) {
                    ExerciseType.PUSH_UP -> "Straight line"
                    ExerciseType.PULL_UP -> "Full hang"
                    ExerciseType.SQUAT -> "Chest up"
                },
                color = Color(0xFFAAAAAA),
                fontSize = if (useCompact) 9.sp else 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(if (useCompact) 4.dp else 8.dp))

            // Detection confidence indicator
            if (detectionConfidence > 0f && !useCompact) {
                DetectionConfidenceIndicator(
                    confidence = detectionConfidence,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Form quality indicator
            FormQualityIndicator(
                quality = animatedQuality,
                averageQuality = averageFormQuality,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            )

            Spacer(modifier = Modifier.height(if (useCompact) 6.dp else 10.dp))

            // Controls row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset button
                OutlinedButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(
                        horizontal = if (useCompact) 8.dp else 12.dp,
                        vertical = if (useCompact) 4.dp else 6.dp
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                    modifier = Modifier.height(if (useCompact) 30.dp else 34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = Color.White,
                        modifier = Modifier.size(if (useCompact) 14.dp else 16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Reset", fontSize = if (useCompact) 10.sp else 12.sp, color = Color.White)
                }
            }
        }
    }
}

/**
 * Get appropriate icon for exercise type
 */
private fun getExerciseIcon(exerciseType: ExerciseType): ImageVector {
    return when (exerciseType) {
        ExerciseType.PUSH_UP -> Icons.Default.FitnessCenter
        ExerciseType.PULL_UP -> Icons.Default.Accessibility
        ExerciseType.SQUAT -> Icons.Default.DirectionsRun
    }
}

/**
 * Detection confidence indicator with signal strength display
 */
@Composable
private fun DetectionConfidenceIndicator(
    confidence: Float, // 0-1
    modifier: Modifier = Modifier
) {
    val confidencePercent = (confidence * 100).toInt()
    val signalColor = when {
        confidence >= 0.8f -> Color(0xFF4ECCA3) // Green - Strong
        confidence >= 0.5f -> Color(0xFFFFD700) // Yellow - Moderate
        else -> Color(0xFFFF6B6B) // Red - Weak
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Signal strength icon
            Icon(
                imageVector = when {
                    confidence >= 0.8f -> Icons.Default.CheckCircle
                    confidence >= 0.5f -> Icons.Default.Warning
                    else -> Icons.Default.Error
                },
                contentDescription = null,
                tint = signalColor,
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = "Detection: $confidencePercent%",
                color = signalColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Confidence bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Gray.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(confidence)
                    .clip(RoundedCornerShape(2.dp))
                    .background(signalColor)
            )
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
                    modifier = Modifier.size(16.dp)
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
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Gray.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(quality / 100f)
                    .clip(RoundedCornerShape(2.dp))
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
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        feedbackMessage?.let { msg ->
            Card(
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .defaultMinSize(minHeight = 28.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = (if (isGoodForm) Color(0xFF4ECCA3) else Color(0xFFFF6B6B))
                        .copy(alpha = 0.85f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isGoodForm) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Exercise selector card with smart mode toggle
 */
@Composable
fun ExerciseSelectorCard(
    currentExerciseType: ExerciseType,
    smartModeEnabled: Boolean,
    onExerciseSelected: (ExerciseType) -> Unit,
    onSmartModeToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = Color(0xFF4ECCA3),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Exercise Type",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Smart mode toggle
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
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Smart mode",
                        tint = if (smartModeEnabled) Color(0xFF4ECCA3) else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Smart Mode",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Switch(
                    checked = smartModeEnabled,
                    onCheckedChange = onSmartModeToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4ECCA3),
                        checkedTrackColor = Color(0xFF4ECCA3).copy(alpha = 0.5f)
                    )
                )
            }
            
            if (smartModeEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Auto-detects exercise type",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Exercise type selection
            Text(
                text = "Manual Selection",
                color = if (smartModeEnabled) Color.Gray else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Exercise type buttons
            ExerciseType.values().forEach { exerciseType ->
                ExerciseTypeButton(
                    exerciseType = exerciseType,
                    isSelected = currentExerciseType == exerciseType && !smartModeEnabled,
                    isEnabled = !smartModeEnabled,
                    onClick = { onExerciseSelected(exerciseType) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Individual exercise type selection button
 */
@Composable
private fun ExerciseTypeButton(
    exerciseType: ExerciseType,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(enabled = isEnabled) { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> Color(0xFF4ECCA3).copy(alpha = 0.3f)
                isEnabled -> Color.Gray.copy(alpha = 0.2f)
                else -> Color.Gray.copy(alpha = 0.1f)
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, Color(0xFF4ECCA3))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = getExerciseIcon(exerciseType),
                contentDescription = null,
                tint = when {
                    isSelected -> Color(0xFF4ECCA3)
                    isEnabled -> Color.White
                    else -> Color.Gray
                },
                modifier = Modifier.size(24.dp)
            )
            
            Column {
                Text(
                    text = ExerciseDetectorFactory.getDisplayName(exerciseType),
                    color = when {
                        isSelected -> Color(0xFF4ECCA3)
                        isEnabled -> Color.White
                        else -> Color.Gray
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = ExerciseDetectorFactory.getDescription(exerciseType),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * Camera positioning guidance card that provides exercise-specific camera placement tips
 */
@Composable
fun CameraGuidanceCard(
    exerciseType: ExerciseType,
    detectionConfidence: Float,
    modifier: Modifier = Modifier
) {
    val guidance = getCameraGuidance(exerciseType)
    val isLowConfidence = detectionConfidence < 0.5f
    
    AnimatedVisibility(
        visible = isLowConfidence,
        enter = slideInHorizontally() + fadeIn(),
        exit = slideOutHorizontally() + fadeOut()
    ) {
        Card(
            modifier = modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFF6B6B).copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Camera Positioning",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Exercise specific guidance
                Text(
                    text = guidance.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = guidance.description,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                
                // Camera mode suggestion
                if (guidance.preferredCamera != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Use ${guidance.preferredCamera} camera",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Data class for camera positioning guidance
 */
data class CameraGuidance(
    val title: String,
    val description: String,
    val preferredCamera: String? = null
)

/**
 * Get camera guidance for specific exercise type
 */
private fun getCameraGuidance(exerciseType: ExerciseType): CameraGuidance {
    return when (exerciseType) {
        ExerciseType.PUSH_UP -> CameraGuidance(
            title = "For Push-Ups",
            description = "Position camera in front of you (selfie mode) to see your full body horizontally. Ensure your entire body from head to feet is visible.",
            preferredCamera = "front"
        )
        ExerciseType.PULL_UP -> CameraGuidance(
            title = "For Pull-Ups", 
            description = "Position camera behind you to capture the full hanging motion. Make sure your arms and head are clearly visible when extended.",
            preferredCamera = "back"
        )
        ExerciseType.SQUAT -> CameraGuidance(
            title = "For Squats",
            description = "Position camera to the side or in front to see knee bending motion. Ensure your legs and upper body are fully visible.",
            preferredCamera = "front or side"
        )
    }
}
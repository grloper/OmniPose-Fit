package com.grloepr.pushtrack.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Workout summary data
 */
data class WorkoutSummary(
    val totalReps: Int,
    val averageFormQuality: Float,
    val duration: Long, // in milliseconds
    val goodFormReps: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Workout summary screen showing statistics and achievements
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    summary: WorkoutSummary,
    onStartNewWorkout: () -> Unit,
    onBackToCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4ECCA3)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Trophy",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Workout Complete!",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = formatDuration(summary.duration),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp
                    )
                }
            }
        }
        
        item {
            // Main statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total reps
                StatCard(
                    title = "Total Reps",
                    value = "${summary.totalReps}",
                    icon = Icons.Default.FitnessCenter,
                    backgroundColor = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
                
                // Form quality
                StatCard(
                    title = "Avg Form",
                    value = "${summary.averageFormQuality.toInt()}%",
                    icon = Icons.Default.Star,
                    backgroundColor = getFormQualityColor(summary.averageFormQuality),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        item {
            // Performance details
            PerformanceDetailsCard(summary)
        }
        
        item {
            // Achievement badges
            AchievementBadges(summary)
        }
        
        item {
            // Action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartNewWorkout,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4ECCA3)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start new workout",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start New Workout",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                OutlinedButton(
                    onClick = onBackToCamera,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Back to camera",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Back to Camera",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PerformanceDetailsCard(summary: WorkoutSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Performance Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            PerformanceRow(
                label = "Good Form Reps",
                value = "${summary.goodFormReps}/${summary.totalReps}",
                percentage = if (summary.totalReps > 0) summary.goodFormReps.toFloat() / summary.totalReps else 0f
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            PerformanceRow(
                label = "Form Consistency",
                value = "${summary.averageFormQuality.toInt()}%",
                percentage = summary.averageFormQuality / 100f
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val repsPerMinute = if (summary.duration > 0) {
                (summary.totalReps * 60000f / summary.duration).toInt()
            } else 0
            
            PerformanceRow(
                label = "Reps per Minute",
                value = "$repsPerMinute",
                percentage = (repsPerMinute / 30f).coerceAtMost(1f) // Max 30 rpm for full bar
            )
        }
    }
}

@Composable
private fun PerformanceRow(
    label: String,
    value: String,
    percentage: Float
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = percentage.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Color(0xFF4ECCA3),
            trackColor = Color.Gray.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun AchievementBadges(summary: WorkoutSummary) {
    val achievements = mutableListOf<Achievement>()
    
    // Add achievements based on performance
    if (summary.totalReps >= 10) achievements.add(Achievement("Strong Start", "10+ reps", Icons.Default.Star))
    if (summary.totalReps >= 25) achievements.add(Achievement("Getting Strong", "25+ reps", Icons.Default.EmojiEvents))
    if (summary.totalReps >= 50) achievements.add(Achievement("Push-up Pro", "50+ reps", Icons.Default.MilitaryTech))
    if (summary.averageFormQuality >= 80) achievements.add(Achievement("Perfect Form", "80%+ form", Icons.Default.CheckCircle))
    if (summary.goodFormReps == summary.totalReps && summary.totalReps > 0) {
        achievements.add(Achievement("Flawless", "All good form", Icons.Default.Diamond))
    }
    
    if (achievements.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Achievements",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                achievements.forEach { achievement ->
                    AchievementBadge(achievement)
                    if (achievement != achievements.last()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(achievement: Achievement) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = achievement.icon,
            contentDescription = achievement.title,
            tint = Color(0xFFFFD700),
            modifier = Modifier.size(24.dp)
        )
        
        Column {
            Text(
                text = achievement.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = achievement.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

private data class Achievement(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun formatDuration(durationMs: Long): String {
    val minutes = durationMs / 60000
    val seconds = (durationMs % 60000) / 1000
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

private fun getFormQualityColor(quality: Float): Color {
    return when {
        quality >= 80 -> Color(0xFF4CAF50)
        quality >= 60 -> Color(0xFFFFD700)
        quality >= 40 -> Color(0xFFFF9800)
        else -> Color(0xFFFF5722)
    }
}
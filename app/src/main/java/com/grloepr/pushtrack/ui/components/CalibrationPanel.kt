package com.grloepr.pushtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.calibration.CalibrationMode
import com.grloepr.pushtrack.calibration.CalibrationState

// Define Orange color
private val Orange = Color(0xFFFFA500)

@Composable
fun CalibrationPanel(
    calibrationState: CalibrationState,
    onStartCalibration: () -> Unit,
    onStopCalibration: () -> Unit,
    onBeginRecording: () -> Unit,
    onAnalyzeData: () -> Unit,
    onResetCalibration: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (calibrationState.mode) {
                CalibrationMode.DISABLED -> MaterialTheme.colorScheme.surface
                CalibrationMode.WARMUP -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                CalibrationMode.RECORDING -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                CalibrationMode.ANALYZING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                CalibrationMode.COMPLETE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 Calibration Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                StatusIndicator(calibrationState.mode)
            }
            
            // Current message
            if (calibrationState.message.isNotEmpty()) {
                Text(
                    text = calibrationState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }
            
            // Progress information
            when (calibrationState.mode) {
                CalibrationMode.RECORDING -> {
                    ProgressDisplay(calibrationState)
                }
                CalibrationMode.ANALYZING -> {
                    AnalysisProgress()
                }
                CalibrationMode.COMPLETE -> {
                    CompletionSummary(calibrationState)
                }
                else -> {}
            }
            
            // Action buttons
            ActionButtons(
                calibrationState = calibrationState,
                onStartCalibration = onStartCalibration,
                onStopCalibration = onStopCalibration,
                onBeginRecording = onBeginRecording,
                onAnalyzeData = onAnalyzeData,
                onResetCalibration = onResetCalibration
            )
            
            // Voice commands help
            if (calibrationState.mode != CalibrationMode.DISABLED) {
                VoiceCommandsHelp()
            }
        }
    }
}

@Composable
private fun StatusIndicator(mode: CalibrationMode) {
    val (icon, color, text) = when (mode) {
        CalibrationMode.DISABLED -> Triple(Icons.Filled.PlayArrow, Color.Gray, "Ready")
        CalibrationMode.WARMUP -> Triple(Icons.Filled.Schedule, Orange, "Warmup") // fixed
        CalibrationMode.RECORDING -> Triple(Icons.Filled.FiberManualRecord, Color.Red, "Recording")
        CalibrationMode.ANALYZING -> Triple(Icons.Filled.Analytics, Color.Blue, "Analyzing")
        CalibrationMode.COMPLETE -> Triple(Icons.Filled.CheckCircle, Color.Green, "Complete")
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProgressDisplay(state: CalibrationState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Rep progress
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Reps: ${state.currentRep}/${state.targetReps}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Valid frames: ${state.validFrames}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Progress bar
        LinearProgressIndicator(
            progress = { state.currentRep.toFloat() / state.targetReps.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
        )
        
        // Session info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Exercise: ${state.exerciseType.name.replace("_", " ")}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Frames: ${state.frameCount}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AnalysisProgress() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Processing your movement data...",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "This may take a few seconds",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompletionSummary(state: CalibrationState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Calibration Complete!",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Text(
                text = "Successfully collected ${state.currentRep} reps with ${state.validFrames} valid data points.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            if (state.confidence > 0) {
                Text(
                    text = "Quality Score: ${(state.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    calibrationState: CalibrationState,
    onStartCalibration: () -> Unit,
    onStopCalibration: () -> Unit,
    onBeginRecording: () -> Unit,
    onAnalyzeData: () -> Unit,
    onResetCalibration: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (calibrationState.mode) {
            CalibrationMode.DISABLED -> {
                Button(
                    onClick = onStartCalibration,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start Calibration")
                }
            }
            
            CalibrationMode.WARMUP -> {
                Button(
                    onClick = onBeginRecording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange
                    )
                ) {
                    Icon(Icons.Filled.FiberManualRecord, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Begin Recording")
                }
                
                OutlinedButton(
                    onClick = onStopCalibration,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel")
                }
            }
            
            CalibrationMode.RECORDING -> {
                OutlinedButton(
                    onClick = onStopCalibration,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stop Recording")
                }
            }
            
            CalibrationMode.ANALYZING -> {
                // No buttons during analysis
            }
            
            CalibrationMode.COMPLETE -> {
                Button(
                    onClick = onResetCalibration,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("New Calibration")
                }
            }
        }
    }
}

@Composable
private fun VoiceCommandsHelp() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Voice commands",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Voice Commands:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            val commands = listOf(
                "\"Start calibration\" - Begin new session",
                "\"Begin recording\" - Start data collection",
                "\"Stop calibration\" - End session",
                "\"Reset calibration\" - Clear and restart"
            )
            
            commands.forEach { command ->
                Text(
                    text = "• $command",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

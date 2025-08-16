package com.grloepr.pushtrack.ui.screen

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.grloepr.pushtrack.domain.ExerciseType

@Composable
fun ExerciseSelectionDialog(
    currentExercise: ExerciseType,
    onExerciseSelected: (ExerciseType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D3F)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Exercise",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Exercise options
                ExerciseType.values().forEach { exerciseType ->
                    ExerciseOptionCard(
                        exerciseType = exerciseType,
                        isSelected = exerciseType == currentExercise,
                        onClick = { onExerciseSelected(exerciseType) }
                    )
                    
                    if (exerciseType != ExerciseType.values().last()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Cancel button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color.White)
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ExerciseOptionCard(
    exerciseType: ExerciseType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF4ECCA3) else Color(0xFF424255)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getExerciseIcon(exerciseType),
                contentDescription = exerciseType.displayName,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = exerciseType.displayName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = getExerciseDescription(exerciseType),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun getExerciseIcon(exerciseType: ExerciseType): ImageVector {
    return when (exerciseType) {
        ExerciseType.PUSH_UP -> Icons.Default.FitnessCenter
        ExerciseType.PULL_UP -> Icons.Default.SportsGymnastics
        ExerciseType.SQUAT -> Icons.Default.Sports
    }
}

private fun getExerciseDescription(exerciseType: ExerciseType): String {
    return when (exerciseType) {
        ExerciseType.PUSH_UP -> "Upper body strength exercise"
        ExerciseType.PULL_UP -> "Requires calibration • Pull up to the bar"
        ExerciseType.SQUAT -> "Lower body strength exercise"
    }
}
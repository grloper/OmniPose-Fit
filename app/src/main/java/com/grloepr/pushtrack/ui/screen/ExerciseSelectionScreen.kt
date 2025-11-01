package com.grloepr.pushtrack.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.exercise.ExerciseType

@Composable
fun ExerciseSelectionScreen(
    onExerciseSelected: (ExerciseType) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Select Exercise",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        ExerciseCard(
            title = "Push-ups",
            description = "Upper body strength training",
            onClick = { onExerciseSelected(ExerciseType.PUSHUP) }
        )
        
        ExerciseCard(
            title = "Squats",
            description = "Lower body workout",
            onClick = { onExerciseSelected(ExerciseType.SQUAT) }
        )
        
        ExerciseCard(
            title = "Pull-ups",
            description = "Back and arms strength",
            onClick = { onExerciseSelected(ExerciseType.PULLUP) }
        )
    }
}

@Composable
fun ExerciseCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


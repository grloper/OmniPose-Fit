package com.grloepr.pushtrack.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.R
import com.grloepr.pushtrack.detection.ExerciseType
import com.grloepr.pushtrack.detection.ExercisePhase

/**
 * Exercise image resources - default placeholders until real images are added
 */
object ExerciseImages {
    // Push-ups
    val PUSH_UP_START = R.drawable.ic_launcher_foreground
    val PUSH_UP_DOWN = R.drawable.ic_launcher_foreground
    val PUSH_UP_UP = R.drawable.ic_launcher_foreground
    
    // Pull-ups
    val PULL_UP_START = R.drawable.ic_launcher_foreground
    val PULL_UP_DOWN = R.drawable.ic_launcher_foreground
    val PULL_UP_UP = R.drawable.ic_launcher_foreground
    
    // Squats
    val SQUAT_START = R.drawable.ic_launcher_foreground
    val SQUAT_DOWN = R.drawable.ic_launcher_foreground
    val SQUAT_UP = R.drawable.ic_launcher_foreground
}

/**
 * Component that displays exercise guidance images for different phases
 */
@Composable
fun ExerciseGuidanceImages(
    exerciseType: ExerciseType,
    phase: ExercisePhase? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Choose which image to show based on exercise type and phase
    val imageData: Pair<Int, String> = when (exerciseType) {
        ExerciseType.PUSH_UP -> {
            when (phase) {
                ExercisePhase.DOWN -> Pair(ExerciseImages.PUSH_UP_DOWN, "Arms bent, chest near ground")
                ExercisePhase.UP -> Pair(ExerciseImages.PUSH_UP_UP, "Arms extended, body straight")
                else -> Pair(ExerciseImages.PUSH_UP_START, "Starting position: Arms extended, body straight")
            }
        }
        ExerciseType.PULL_UP -> {
            when (phase) {
                ExercisePhase.DOWN -> Pair(ExerciseImages.PULL_UP_DOWN, "Arms extended, hanging position")
                ExercisePhase.UP -> Pair(ExerciseImages.PULL_UP_UP, "Chin above bar, arms bent")
                else -> Pair(ExerciseImages.PULL_UP_START, "Grab bar with overhand grip, shoulder-width apart")
            }
        }
        ExerciseType.SQUAT -> {
            when (phase) {
                ExercisePhase.DOWN -> Pair(ExerciseImages.SQUAT_DOWN, "Hips back and down, knees at 90°")
                ExercisePhase.UP -> Pair(ExerciseImages.SQUAT_UP, "Standing tall, knees and hips extended")
                else -> Pair(ExerciseImages.SQUAT_START, "Feet shoulder-width apart, toes slightly out")
            }
        }
    }
    
    val (imageResId, description) = imageData
    
    Card(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E28)
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${exerciseType.name.replace('_', ' ')} Form Guide",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF2A2A36), RoundedCornerShape(8.dp))
            ) {
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = "Exercise form guidance",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            }
            
            Text(
                text = description,
                color = Color(0xFF4ECCA3),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * Component that displays all phases of an exercise for guidance
 */
@Composable
fun ExercisePhaseGuidance(
    exerciseType: ExerciseType,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Exercise Form Guide",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        ExerciseGuidanceImages(
            exerciseType = exerciseType,
            phase = ExercisePhase.UP,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        ExerciseGuidanceImages(
            exerciseType = exerciseType,
            phase = ExercisePhase.DOWN,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        ExerciseGuidanceImages(
            exerciseType = exerciseType,
            phase = null  // Starting position
        )
    }
}

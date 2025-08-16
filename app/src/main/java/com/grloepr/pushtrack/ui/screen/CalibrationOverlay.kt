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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.domain.CalibrationState

@Composable
fun CalibrationOverlay(
    state: CalibrationState,
    modifier: Modifier = Modifier
) {
    // Pulse animation for countdown
    val infiniteTransition = rememberInfiniteTransition(label = "calibration")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D3F)
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    state.isCalibrating -> {
                        // Calibration in progress
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Calibrating",
                            tint = Color(0xFF4ECCA3),
                            modifier = Modifier
                                .size(64.dp)
                                .scale(if (state.countdown > 0) pulseScale else 1f)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (state.countdown > 0) {
                            Text(
                                text = state.countdown.toString(),
                                color = Color(0xFF4ECCA3),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Text(
                            text = "Calibration",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = state.instruction,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    }
                    
                    state.isComplete -> {
                        // Calibration complete
                        Icon(
                            imageVector = if (state.isSuccessful) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = if (state.isSuccessful) "Success" else "Failed",
                            tint = if (state.isSuccessful) Color(0xFF4ECCA3) else Color(0xFFFF6B6B),
                            modifier = Modifier.size(64.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = if (state.isSuccessful) "Calibration Complete!" else "Calibration Failed",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = if (state.isSuccessful) {
                                "You can now start your workout!"
                            } else {
                                "Please try again. Make sure you're in the correct position."
                            },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
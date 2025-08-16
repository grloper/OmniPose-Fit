package com.grloepr.pushtrack.ui.screen

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.settings.SettingsManager

@Composable
fun SettingsOverlay(
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val voiceEnabled by settingsManager.voiceEnabled.collectAsState()
    val speechRate by settingsManager.speechRate.collectAsState()
    val showDebugInfo by settingsManager.showDebugInfo.collectAsState()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D3F)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = { settingsManager.toggleSettings() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close settings",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Voice feedback toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice Feedback",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    
                    Switch(
                        checked = voiceEnabled,
                        onCheckedChange = { settingsManager.setVoiceEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4ECCA3),
                            checkedTrackColor = Color(0xFF4ECCA3).copy(alpha = 0.5f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Speech rate slider
                if (voiceEnabled) {
                    Text(
                        text = "Speech Rate",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    
                    Slider(
                        value = speechRate,
                        onValueChange = { settingsManager.setSpeechRate(it) },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF4ECCA3),
                            activeTrackColor = Color(0xFF4ECCA3)
                        )
                    )
                    
                    Text(
                        text = "${(speechRate * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Debug info toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Debug Information",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    
                    Switch(
                        checked = showDebugInfo,
                        onCheckedChange = { settingsManager.setShowDebugInfo(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4ECCA3),
                            checkedTrackColor = Color(0xFF4ECCA3).copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    }
}
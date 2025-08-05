package com.grloepr.pushtrack.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grloepr.pushtrack.permission.CameraPermissionHelper
import com.grloepr.pushtrack.ui.camera.CameraPreview
import com.grloepr.pushtrack.viewmodel.PushUpCounterViewModel

/**
 * Main screen for push-up counting with camera preview
 */
@Composable
fun PushUpCounterScreen(
    viewModel: PushUpCounterViewModel = viewModel()
) {
    var hasCameraPermission by remember { mutableStateOf(false) }
    
    CameraPermissionHelper.RequestCameraPermission(
        onPermissionGranted = { hasCameraPermission = true },
        onPermissionDenied = { hasCameraPermission = false }
    )
    
    if (hasCameraPermission) {
        CameraScreenContent(viewModel = viewModel)
    } else {
        PermissionDeniedContent()
    }
}

/**
 * Content shown when camera permission is granted
 */
@Composable
private fun CameraScreenContent(
    viewModel: PushUpCounterViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview background
        CameraPreview()
        
        // Rep counter overlay
        RepCounterOverlay(
            repCount = viewModel.repCount,
            isCountingActive = viewModel.isCountingActive,
            onStartStop = { viewModel.toggleCounting() },
            onReset = { viewModel.resetCount() },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Overlay showing rep count and controls
 */
@Composable
private fun RepCounterOverlay(
    repCount: Int,
    isCountingActive: Boolean,
    onStartStop: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Rep count display
        Text(
            text = repCount.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Push-ups",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Start/Stop button
        Button(
            onClick = onStartStop,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = if (isCountingActive) "Stop" else "Start",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        // Reset button
        Button(onClick = onReset) {
            Text(
                text = "Reset",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Content shown when camera permission is denied
 */
@Composable
private fun PermissionDeniedContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "This app needs camera access to count your push-ups. Please grant camera permission to continue.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}
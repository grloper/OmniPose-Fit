package com.grloepr.pushtrack.ui.screen

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.grloepr.pushtrack.camera.bindCameraPreview
import com.grloepr.pushtrack.camera.rememberCameraProvider
import com.grloepr.pushtrack.permission.CameraPermissionDeniedContent
import com.grloepr.pushtrack.permission.CameraPermissionRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushUpCameraScreen() {
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    
    when {
        permissionDenied -> {
            CameraPermissionDeniedContent()
        }
        permissionGranted -> {
            CameraPreviewScreen()
        }
        else -> {
            CameraPermissionRequest(
                onPermissionGranted = { permissionGranted = true },
                onPermissionDenied = { permissionDenied = true }
            )
        }
    }
}

@Composable
private fun CameraPreviewScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProvider = rememberCameraProvider()
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    // Set scale type to fill the view
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                cameraProvider?.let { provider ->
                    bindCameraPreview(
                        cameraProvider = provider,
                        previewView = previewView,
                        lifecycleOwner = lifecycleOwner
                    )
                }
            }
        )
        
        // Overlay UI
        PushUpOverlay(
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun PushUpOverlay(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Push-Up Counter",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Reps: 0",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
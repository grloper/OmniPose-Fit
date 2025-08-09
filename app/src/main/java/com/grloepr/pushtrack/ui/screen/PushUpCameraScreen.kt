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
import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.analysis.ImageAnalyzer
import com.grloepr.pushtrack.camera.bindCameraWithAnalysis
import com.grloepr.pushtrack.camera.rememberCameraProvider
import com.grloepr.pushtrack.permission.CameraPermissionDeniedContent
import com.grloepr.pushtrack.permission.CameraPermissionRequest
import com.grloepr.pushtrack.pose.PoseDetectorClient
import com.grloepr.pushtrack.ui.overlay.PoseOverlay
import kotlinx.coroutines.flow.collectLatest

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
    
    // Initialize pose detection components
    val poseDetectorClient = remember { 
        PoseDetectorClient().apply { initialize() }
    }
    val imageAnalyzer = remember { ImageAnalyzer(poseDetectorClient) }
    
    // State for current pose
    var currentPose by remember { mutableStateOf<Pose?>(null) }
    
    // Collect pose results
    LaunchedEffect(imageAnalyzer) {
        imageAnalyzer.poseResults.collectLatest { pose ->
            currentPose = pose
        }
    }
    
    // Clean up pose detector when screen is disposed
    DisposableEffect(poseDetectorClient) {
        onDispose {
            poseDetectorClient.close()
        }
    }
    
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
                    bindCameraWithAnalysis(
                        cameraProvider = provider,
                        previewView = previewView,
                        lifecycleOwner = lifecycleOwner,
                        imageAnalyzer = imageAnalyzer
                    )
                }
            }
        )
        
        // Pose overlay
        PoseOverlay(
            pose = currentPose,
            modifier = Modifier.fillMaxSize()
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
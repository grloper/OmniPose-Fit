package com.grloepr.pushtrack.ui.screen

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.analysis.ImageAnalyzer
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import com.grloepr.pushtrack.analysis.CombinedDetectionResult
import com.grloepr.pushtrack.camera.bindCameraWithAnalysis
import com.grloepr.pushtrack.camera.rememberCameraProvider
import com.grloepr.pushtrack.permission.CameraPermissionDeniedContent
import com.grloepr.pushtrack.permission.CameraPermissionRequest
import com.grloepr.pushtrack.pose.PoseDetectorClient
import com.grloepr.pushtrack.face.FaceDetectorClient
import com.grloepr.pushtrack.ui.components.CameraControls
import com.grloepr.pushtrack.ui.components.MinimalCameraControls
import com.grloepr.pushtrack.ui.overlay.PoseOverlay
import com.grloepr.pushtrack.ui.overlay.FaceOverlay
import com.grloepr.pushtrack.ui.overlay.RepOverlay
import com.grloepr.pushtrack.viewmodel.PushUpCounterViewModel
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
    
    // ViewModel for managing push-up counter state
    val viewModel: PushUpCounterViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    // Ultra-Performance: Initialize pose and face detection components
    val poseDetectorClient = remember { 
        PoseDetectorClient().apply { initialize() }
    }
    val faceDetectorClient = remember { 
        FaceDetectorClient().apply { initialize() }
    }
    val imageAnalyzer = remember { ImageAnalyzer(poseDetectorClient, faceDetectorClient) }
    
    // Ultra-Performance: Collect combined detection results and process them through ViewModel
    LaunchedEffect(imageAnalyzer) {
        // Prioritize combined results for maximum accuracy
        imageAnalyzer.combinedDetectionResults.collectLatest { combinedResult ->
            viewModel.processCombinedDetectionResult(combinedResult)
        }
    }
    
    // Fallback: Collect legacy pose results for compatibility
    LaunchedEffect(imageAnalyzer) {
        imageAnalyzer.poseResults.collectLatest { poseResult ->
            viewModel.processPoseResult(poseResult)
        }
    }
    
    // Ultra-Performance: Clean up both detectors when screen is disposed
    DisposableEffect(poseDetectorClient, faceDetectorClient) {
        onDispose {
            poseDetectorClient.close()
            faceDetectorClient.close()
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
                        imageAnalyzer = imageAnalyzer,
                        cameraSelector = uiState.cameraSelector
                    )
                }
            }
        )
        
        // Ultra-Precise pose overlay with perfect coordinate transformation
        uiState.currentPoseFrame?.let { poseFrame ->
            PoseOverlay(
                poseFrameResult = poseFrame,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Ultra-Enhanced face overlay for improved accuracy and debugging
        uiState.currentCombinedResult?.let { combinedResult ->
            FaceOverlay(
                combinedResult = combinedResult,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Rep counter overlay
        RepOverlay(
            repCount = uiState.repCount,
            phase = uiState.phase,
            lastAngle = uiState.lastAngle,
            isTracking = uiState.isTracking,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
        )
        
        // Camera controls
        MinimalCameraControls(
            onCameraFlip = { viewModel.toggleCamera() },
            onReset = { viewModel.reset() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

// Legacy components for backward compatibility
@Composable
private fun PushUpOverlay(
    repCount: Int,
    onReset: () -> Unit,
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
                text = "Reps: $repCount",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Reset button
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.8f)
                ),
                modifier = Modifier.size(width = 80.dp, height = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset counter",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraControls(
    onCameraSwitch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Camera flip button
            IconButton(
                onClick = onCameraSwitch,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
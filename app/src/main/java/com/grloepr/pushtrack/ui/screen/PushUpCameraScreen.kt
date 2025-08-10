package com.grloepr.pushtrack.ui.screen

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.analysis.ImageAnalyzer
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import com.grloepr.pushtrack.camera.bindCameraWithAnalysis
import com.grloepr.pushtrack.camera.rememberCameraProvider
import com.grloepr.pushtrack.counting.PushUpCounter
import com.grloepr.pushtrack.counting.PushUpState
import com.grloepr.pushtrack.counting.PushUpQuality
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
    
    // Camera selector state (front/back camera)
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    
    // Initialize pose detection components with performance optimization
    val poseDetectorClient = remember { 
        PoseDetectorClient().apply { 
            initialize(
                useAccurateModel = false, // Use faster model for real-time performance
                enableGpuAcceleration = true // Enable GPU acceleration
            )
        }
    }
    val imageAnalyzer = remember { 
        ImageAnalyzer(
            poseDetectorClient = poseDetectorClient,
            targetFps = 30f, // Target 30 FPS
            enablePerformanceMonitoring = true
        )
    }
    
    // Initialize push-up counter
    val pushUpCounter = remember { PushUpCounter() }
    
    // State for current pose detection result and performance metrics
    var currentPoseResult by remember { mutableStateOf<PoseDetectionResult?>(null) }
    var showPerformanceStats by remember { mutableStateOf(false) }
    
    // Collect push-up counter state
    val repCount by pushUpCounter.repCountFlow.collectAsState()
    val currentState by pushUpCounter.currentStateFlow.collectAsState()
    val lastMovement by pushUpCounter.lastMovementFlow.collectAsState()
    val performanceMetrics by imageAnalyzer.performanceMetrics.collectAsState(initial = null)
    
    // Collect pose results and update counter
    LaunchedEffect(imageAnalyzer) {
        imageAnalyzer.poseResults.collectLatest { poseResult ->
            currentPoseResult = poseResult
            // Update push-up counter with new pose
            pushUpCounter.processPose(poseResult.pose)
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
                        imageAnalyzer = imageAnalyzer,
                        cameraSelector = cameraSelector
                    )
                }
            }
        )
        
        // Pose overlay with proper coordinate transformation
        currentPoseResult?.let { poseResult ->
            PoseOverlay(
                pose = poseResult.pose,
                imageWidth = poseResult.imageWidth,
                imageHeight = poseResult.imageHeight,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Overlay UI
        PushUpOverlay(
            repCount = repCount,
            currentState = currentState,
            lastMovement = lastMovement,
            currentElbowAngle = pushUpCounter.getCurrentElbowAngle(),
            onReset = { pushUpCounter.resetCounter() },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // Performance stats overlay (optional)
        if (showPerformanceStats) {
            performanceMetrics?.let { metrics ->
                PerformanceStatsOverlay(
                    metrics = metrics,
                    processingTime = currentPoseResult?.processingTimeMs ?: 0,
                    onDismiss = { showPerformanceStats = false },
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
        
        // Camera controls
        CameraControls(
            onCameraSwitch = { 
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
            },
            onToggleStats = { showPerformanceStats = !showPerformanceStats },
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun PushUpOverlay(
    repCount: Int,
    currentState: PushUpState,
    lastMovement: com.grloepr.pushtrack.counting.PushUpMovement?,
    currentElbowAngle: Float?,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
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
            
            // Rep count with prominent display
            Text(
                text = "Reps: $repCount",
                color = Color.Green,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Current state indicator
            Text(
                text = "State: ${currentState.name.replace('_', ' ')}",
                color = getStateColor(currentState),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            // Elbow angle display (for debugging/calibration)
            currentElbowAngle?.let { angle ->
                Text(
                    text = "Elbow: ${angle.toInt()}°",
                    color = Color.Cyan,
                    fontSize = 12.sp
                )
            }
            
            // Last movement quality
            lastMovement?.let { movement ->
                Text(
                    text = "Last: ${movement.quality.name}",
                    color = getQualityColor(movement.quality),
                    fontSize = 12.sp
                )
            }
            
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
private fun getStateColor(state: PushUpState): Color {
    return when (state) {
        PushUpState.NEUTRAL -> Color.Gray
        PushUpState.DESCENDING -> Color.Yellow
        PushUpState.DOWN_POSITION -> Color.Red
        PushUpState.ASCENDING -> Color.Blue
        PushUpState.UP_POSITION -> Color.Green
    }
}

@Composable
private fun getQualityColor(quality: PushUpQuality): Color {
    return when (quality) {
        PushUpQuality.EXCELLENT -> Color.Green
        PushUpQuality.GOOD -> Color.Yellow
        PushUpQuality.FAIR -> Color(0xFFFF8C00) // Orange
        PushUpQuality.POOR -> Color.Red
    }
}

@Composable
private fun PerformanceStatsOverlay(
    metrics: com.grloepr.pushtrack.analysis.PerformanceMetrics,
    processingTime: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(16.dp)
            .clickable { onDismiss() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Performance Stats",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "FPS: ${metrics.actualFps.toInt()}/${metrics.targetFps.toInt()}",
                color = if (metrics.actualFps >= metrics.targetFps * 0.8f) Color.Green else Color.Yellow,
                fontSize = 12.sp
            )
            
            Text(
                text = "Avg: ${metrics.averageProcessingTimeMs.toInt()}ms",
                color = Color.Cyan,
                fontSize = 12.sp
            )
            
            Text(
                text = "Last: ${processingTime}ms",
                color = Color.White,
                fontSize = 12.sp
            )
            
            Text(
                text = "Skipped: ${metrics.framesSkipped}",
                color = if (metrics.framesSkipped > metrics.framesProcessed) Color.Red else Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CameraControls(
    onCameraSwitch: () -> Unit,
    onToggleStats: () -> Unit,
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
            
            // Performance stats toggle
            IconButton(
                onClick = onToggleStats,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh, // Using available icon, would be better with stats icon
                    contentDescription = "Toggle performance stats",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
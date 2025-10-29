package com.grloepr.pushtrack.ui.screen

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.grloepr.pushtrack.analysis.ImageAnalyzer
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import com.grloepr.pushtrack.camera.bindCameraWithAnalysis
import com.grloepr.pushtrack.camera.rememberCameraProvider
import com.grloepr.pushtrack.exercise.ExerciseAnalyzer
import com.grloepr.pushtrack.exercise.ExerciseType
import com.grloepr.pushtrack.permission.CameraPermissionDeniedContent
import com.grloepr.pushtrack.permission.CameraPermissionRequest
import com.grloepr.pushtrack.pose.PoseDetectorClient
import com.grloepr.pushtrack.ui.overlay.PoseOverlay
import com.grloepr.pushtrack.ui.overlay.PoseOverlayStyle

/**
 * Main camera screen with pose detection overlay and exercise tracking
 */
@Composable
fun PushUpCameraScreen() {
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var selectedExercise by remember { mutableStateOf<ExerciseType?>(null) }
    
    when {
        permissionDenied -> {
            CameraPermissionDeniedContent()
        }
        permissionGranted -> {
            if (selectedExercise == null) {
                ExerciseSelectionScreen(
                    onExerciseSelected = { exercise ->
                        selectedExercise = exercise
                    },
                    onBack = { permissionGranted = false }
                )
            } else {
                CameraPreviewScreen(
                    exerciseType = selectedExercise!!,
                    onExerciseFinished = {
                        selectedExercise = null
                    }
                )
            }
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
private fun CameraPreviewScreen(
    exerciseType: ExerciseType,
    onExerciseFinished: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProvider = rememberCameraProvider()
    
    // Camera selector - default to FRONT for selfie mode
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    
    // Initialize pose detection components
    val poseDetectorClient = remember { 
        PoseDetectorClient().apply { initialize() }
    }
    val imageAnalyzer = remember { ImageAnalyzer(poseDetectorClient) }
    
    // State for current pose detection result
    var currentPoseResult by remember { mutableStateOf<PoseDetectionResult?>(null) }
    
    // Exercise analyzer and rep counting
    var repCount by remember { mutableStateOf(0) }
    val exerciseAnalyzer = remember {
        ExerciseAnalyzer(exerciseType) { newRepCount ->
            repCount = newRepCount
        }
    }
    
    // Collect pose results and analyze for exercise
    LaunchedEffect(imageAnalyzer) {
        imageAnalyzer.poseResults.collect { poseResult ->
            currentPoseResult = poseResult
            // Analyze pose for exercise detection
            exerciseAnalyzer.analyzePose(poseResult.pose)
        }
    }
    
    // Clean up when screen is disposed
    DisposableEffect(poseDetectorClient) {
        onDispose {
            poseDetectorClient.close()
        }
    }

    val density = LocalDensity.current
    val overlayStyle = remember(density) {
        PoseOverlayStyle(
            headRadius = with(density) { 5.dp.toPx() },
            upperBodyRadius = with(density) { 6.dp.toPx() },
            lowerBodyRadius = with(density) { 5.dp.toPx() },
            connectionStrokeWidth = with(density) { 2.dp.toPx() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
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
        
        // Pose overlay
        currentPoseResult?.let { poseResult ->
            PoseOverlay(
                poseResult = poseResult,
                isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA,
                modifier = Modifier.fillMaxSize(),
                style = overlayStyle
            )
        }
        
        // Rep counter display at top
        RepCounterCard(
            exerciseType = exerciseType,
            repCount = repCount,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        )
        
        // Bottom controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Camera switch button
            FloatingActionButton(
                onClick = {
                    cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                },
                containerColor = Color.White.copy(alpha = 0.8f)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.Black
                )
            }
            
            // Reset button
            FloatingActionButton(
                onClick = {
                    exerciseAnalyzer.reset()
                    repCount = 0
                },
                containerColor = Color.Red.copy(alpha = 0.8f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset counter",
                    tint = Color.White
                )
            }
            
            // Back button
            FloatingActionButton(
                onClick = onExerciseFinished,
                containerColor = Color.White.copy(alpha = 0.8f)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to exercises",
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
fun RepCounterCard(
    exerciseType: ExerciseType,
    repCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = getExerciseName(exerciseType),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$repCount",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = "reps",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getExerciseName(exerciseType: ExerciseType): String {
    return when (exerciseType) {
        ExerciseType.PUSHUP -> "Push-ups"
        ExerciseType.SQUAT -> "Squats"
        ExerciseType.PULLUP -> "Pull-ups"
    }
}

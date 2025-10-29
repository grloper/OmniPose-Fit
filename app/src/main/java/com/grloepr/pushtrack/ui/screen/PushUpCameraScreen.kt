package com.grloepr.pushtrack.ui.screen

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.grloepr.pushtrack.exercise.ExerciseAnalysis
import com.grloepr.pushtrack.exercise.ExerciseState
import com.grloepr.pushtrack.exercise.ExerciseType
import com.grloepr.pushtrack.exercise.FormFeedback
import com.grloepr.pushtrack.exercise.FormSeverity
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
    
    // Exercise analyzer and rich analysis
    var latestAnalysis by remember {
        mutableStateOf(
            ExerciseAnalysis(
                state = ExerciseState.Waiting,
                repCount = 0,
                repDelta = 0,
                form = FormFeedback.neutral(),
                poseVisible = false,
                timestampMs = 0L
            )
        )
    }
    val exerciseAnalyzer = remember(exerciseType) {
        ExerciseAnalyzer(exerciseType) { analysis ->
            latestAnalysis = analysis
        }
    }

    LaunchedEffect(exerciseAnalyzer) {
        exerciseAnalyzer.reset()
    }
    
    // Collect pose results and analyze for exercise
    LaunchedEffect(imageAnalyzer) {
        imageAnalyzer.poseResults.collect { poseResult ->
            currentPoseResult = poseResult
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
            repCount = latestAnalysis.repCount,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        )

        FormFeedbackPanel(
            feedback = latestAnalysis.form,
            poseVisible = latestAnalysis.poseVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .width(260.dp)
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

@Composable
private fun FormFeedbackPanel(
    feedback: FormFeedback,
    poseVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = feedback.primarySignal
    val accentColor = when (primary?.severity ?: FormSeverity.INFO) {
        FormSeverity.INFO -> MaterialTheme.colorScheme.primary
        FormSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
        FormSeverity.CRITICAL -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (poseVisible) feedback.headline else "Step fully into frame",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = feedback.overallScore.coerceIn(0f, 1f),
                trackColor = Color.White.copy(alpha = 0.2f),
                color = accentColor,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            val issues = feedback.signals.sortedBy { it.score }
            if (issues.isEmpty()) {
                Text(
                    text = "Smooth form detected",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
            } else {
                issues.take(3).forEach { signal ->
                    val tagColor = when (signal.severity) {
                        FormSeverity.INFO -> Color(0xFF4CAF50)
                        FormSeverity.WARNING -> Color(0xFFFFA000)
                        FormSeverity.CRITICAL -> Color(0xFFE53935)
                    }
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = signal.label,
                            color = tagColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = signal.message,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
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

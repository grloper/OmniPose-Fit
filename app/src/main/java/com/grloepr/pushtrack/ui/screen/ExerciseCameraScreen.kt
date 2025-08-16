package com.grloepr.pushtrack.ui.screen

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.pose.Pose
import com.grloepr.pushtrack.analysis.*
import com.grloepr.pushtrack.camera.bindCameraWithAnalysis
import com.grloepr.pushtrack.camera.rememberCameraProvider
import com.grloepr.pushtrack.domain.*
import com.grloepr.pushtrack.feedback.*
import com.grloepr.pushtrack.permission.CameraPermissionDeniedContent
import com.grloepr.pushtrack.permission.CameraPermissionRequest
import com.grloepr.pushtrack.pose.PoseDetectorClient
import com.grloepr.pushtrack.settings.SettingsManager
import com.grloepr.pushtrack.ui.components.*
import com.grloepr.pushtrack.ui.overlay.EnhancedPoseOverlay
import com.grloepr.pushtrack.ui.overlay.PoseOverlay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseCameraScreen(
    initialExerciseType: ExerciseType = ExerciseType.PUSH_UP
) {
    // Camera permission state
    var hasCameraPermission by remember { mutableStateOf(false) }
    
    CameraPermissionRequest(
        onPermissionGranted = { hasCameraPermission = true },
        onPermissionDenied = { hasCameraPermission = false }
    )
    
    if (!hasCameraPermission) {
        CameraPermissionDeniedContent()
        return
    }
    
    // Exercise state
    var selectedExerciseType by remember { mutableStateOf(initialExerciseType) }
    var currentDetector by remember { mutableStateOf(ExerciseDetectorFactory.createDetector(selectedExerciseType)) }
    var showExerciseSelection by remember { mutableStateOf(false) }
    
    // Detection state
    var currentPoseResult by remember { mutableStateOf<PoseDetectionResult?>(null) }
    var detectionResult by remember { mutableStateOf(DetectionResult(0, ExerciseState.UNKNOWN, null)) }
    
    // Calibration state
    val calibrationManager = remember { CalibrationManager() }
    val calibrationState by calibrationManager.calibrationState.collectAsState()
    
    // Workout state
    var showSummary by remember { mutableStateOf(false) }
    var workoutStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var currentFeedbackMessage by remember { mutableStateOf<String?>(null) }
    
    // Settings
    val settingsManager = remember { SettingsManager(LocalContext.current) }
    val voiceEnabled by settingsManager.voiceEnabled.collectAsState()
    val speechRate by settingsManager.speechRate.collectAsState()
    val overlayMode by settingsManager.overlayMode.collectAsState()
    val showSettings by settingsManager.showSettings.collectAsState()
    
    // Voice feedback
    val voiceFeedbackManager = remember { VoiceFeedbackManager(LocalContext.current) }
    
    // Camera and pose detection
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProvider = rememberCameraProvider()
    val poseDetectorClient = remember { PoseDetectorClient() }
    val imageAnalyzer = remember { ImageAnalyzer(poseDetectorClient) }
    
    // Handle exercise type changes
    LaunchedEffect(selectedExerciseType) {
        val newDetector = ExerciseDetectorFactory.createDetector(selectedExerciseType)
        currentDetector = newDetector
        
        // Start calibration if needed
        if (calibrationManager.isCalibrationNeeded(newDetector)) {
            calibrationManager.startCalibration(newDetector)
        }
    }
    
    // Bind camera
    LaunchedEffect(cameraProvider, imageAnalyzer) {
        if (cameraProvider != null) {
            bindCameraWithAnalysis(
                context,
                lifecycleOwner,
                cameraProvider,
                imageAnalyzer,
                CameraSelector.DEFAULT_FRONT_CAMERA
            )
        }
    }
    
    // Update voice feedback settings
    LaunchedEffect(voiceEnabled, speechRate) {
        voiceFeedbackManager.updateSettings(
            enabled = voiceEnabled,
            speechRate = speechRate
        )
    }
    
    // Collect pose results and process exercise detection
    LaunchedEffect(imageAnalyzer) {
        imageAnalyzer.poseResults.collect { poseResult ->
            currentPoseResult = poseResult
            
            // Handle calibration if in progress
            if (calibrationState.isCalibrating) {
                calibrationManager.processPose(poseResult.pose)
                return@collect
            }
            
            // Skip detection if calibration is needed but not complete
            if (currentDetector.requiresCalibration() && !currentDetector.isCalibrated()) {
                return@collect
            }
            
            // Process pose for exercise detection
            val newResult = currentDetector.processPose(poseResult.pose)
            
            // Announce new rep count
            if (newResult.repCount > detectionResult.repCount) {
                voiceFeedbackManager.announceRepCount(newResult.repCount)
            }
            
            // Handle form feedback
            newResult.formQuality?.feedback?.let { feedback ->
                voiceFeedbackManager.announcePostureFeedback(PostureFeedback.GOOD_FORM) // Simplified
                currentFeedbackMessage = feedback
            }
            
            detectionResult = newResult
        }
    }
    
    // Clear feedback message after delay
    LaunchedEffect(currentFeedbackMessage) {
        if (currentFeedbackMessage != null) {
            delay(3000)
            currentFeedbackMessage = null
        }
    }
    
    // Clean up when screen is disposed
    DisposableEffect(poseDetectorClient, voiceFeedbackManager) {
        onDispose {
            poseDetectorClient.close()
            voiceFeedbackManager.shutdown()
        }
    }
    
    // Show workout summary if requested
    if (showSummary) {
        val workoutDuration = System.currentTimeMillis() - workoutStartTime
        val goodFormReps = (detectionResult.repCount * (detectionResult.formQuality?.score ?: 75f) / 100).toInt()
        
        WorkoutSummaryScreen(
            summary = WorkoutSummary(
                totalReps = detectionResult.repCount,
                averageFormQuality = detectionResult.formQuality?.score ?: 0f,
                duration = workoutDuration,
                goodFormReps = goodFormReps,
                exerciseType = selectedExerciseType
            ),
            onStartNewWorkout = {
                showSummary = false
                currentDetector.reset()
                calibrationManager.reset()
                voiceFeedbackManager.reset()
                workoutStartTime = System.currentTimeMillis()
                
                // Restart calibration if needed
                if (calibrationManager.isCalibrationNeeded(currentDetector)) {
                    calibrationManager.startCalibration(currentDetector)
                }
            },
            onBackToCamera = {
                showSummary = false
            }
        )
        return
    }
    
    // Show exercise selection dialog
    if (showExerciseSelection) {
        ExerciseSelectionDialog(
            currentExercise = selectedExerciseType,
            onExerciseSelected = { exerciseType ->
                selectedExerciseType = exerciseType
                showExerciseSelection = false
            },
            onDismiss = { showExerciseSelection = false }
        )
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                cameraProvider?.let { provider ->
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                }
            }
        )
        
        // Pose overlay
        currentPoseResult?.let { poseResult ->
            when (overlayMode) {
                "enhanced" -> {
                    EnhancedPoseOverlay(
                        poseResult = poseResult.toPoseFrameResult(),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                "simple" -> {
                    PoseOverlay(
                        poseResult = poseResult.toPoseFrameResult(),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        
        // Calibration overlay
        if (calibrationState.isCalibrating) {
            CalibrationOverlay(
                state = calibrationState,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Exercise counter overlay
        EnhancedExerciseCounter(
            exerciseType = selectedExerciseType,
            repCount = detectionResult.repCount,
            currentState = detectionResult.currentState,
            formQuality = detectionResult.formQuality,
            confidence = detectionResult.confidence,
            lastAngle = detectionResult.lastAngle,
            isTracking = currentPoseResult != null && !calibrationState.isCalibrating,
            onReset = {
                currentDetector.reset()
                calibrationManager.reset()
                if (calibrationManager.isCalibrationNeeded(currentDetector)) {
                    calibrationManager.startCalibration(currentDetector)
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
        
        // Exercise selection button
        FloatingActionButton(
            onClick = { showExerciseSelection = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            containerColor = Color(0xFF4ECCA3)
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = "Select Exercise",
                tint = Color.White
            )
        }
        
        // Settings and summary buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { settingsManager.toggleSettings() },
                containerColor = Color(0xFF424255)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
            
            FloatingActionButton(
                onClick = { showSummary = true },
                containerColor = Color(0xFF4ECCA3)
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = "Summary",
                    tint = Color.White
                )
            }
        }
        
        // Feedback message
        currentFeedbackMessage?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2D2D3F).copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        // Settings overlay
        if (showSettings) {
            SettingsOverlay(
                settingsManager = settingsManager,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Extension function to convert PoseDetectionResult to PoseFrameResult
fun PoseDetectionResult.toPoseFrameResult(
    rotationDegrees: Int = 0,
    isFrontCamera: Boolean = false
): PoseFrameResult {
    return PoseFrameResult(
        pose = this.pose,
        imageWidth = this.imageWidth,
        imageHeight = this.imageHeight,
        rotationDegrees = rotationDegrees,
        isFrontCamera = isFrontCamera
    )
}
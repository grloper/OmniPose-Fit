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
    var detectionEnabled by remember { mutableStateOf(true) }
    
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
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val voiceEnabled by settingsManager.voiceEnabled.collectAsState()
    val speechRate by settingsManager.speechRate.collectAsState()
    val overlayMode by settingsManager.overlayMode.collectAsState()
    val showSettings by settingsManager.showSettings.collectAsState()
    
    // Voice feedback
    val voiceFeedbackManager = remember { VoiceFeedbackManager(context) }
    
    // Camera and pose detection
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProvider = rememberCameraProvider()
    val poseDetectorClient = remember { PoseDetectorClient() }
    val imageAnalyzer = remember { ImageAnalyzer(poseDetectorClient) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    
    // Debug state
    var debugModeActive by remember { mutableStateOf(false) }
    
    // Handle exercise type changes
    LaunchedEffect(selectedExerciseType) {
        // Force complete any ongoing calibration
        calibrationManager.forceCompleteCalibration()
        calibrationManager.reset()
        
        // Create new detector
        val newDetector = ExerciseDetectorFactory.createDetector(selectedExerciseType)
        currentDetector = newDetector
        
        // Reset detection state
        detectionResult = DetectionResult(0, ExerciseState.UNKNOWN, null)
        
        // Start calibration if needed
        if (calibrationManager.isCalibrationNeeded(newDetector)) {
            calibrationManager.startCalibration(newDetector)
        }
        
        // Re-enable detection
        detectionEnabled = true
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
            
            // Skip processing if detection is disabled
            if (!detectionEnabled) {
                android.util.Log.d("ExerciseCameraScreen", "Detection disabled")
                return@collect
            }
            
            // Handle calibration if in progress
            if (calibrationState.isCalibrating) {
                android.util.Log.d("ExerciseCameraScreen", "Calibrating: ${calibrationState.countdown}")
                calibrationManager.processPose(poseResult.pose)
                return@collect
            }
            
            // Skip detection if calibration is needed but not complete
            if (currentDetector.requiresCalibration() && !currentDetector.isCalibrated()) {
                android.util.Log.d("ExerciseCameraScreen", "Calibration needed but not complete")
                return@collect
            }
            
            // Process pose for exercise detection
            val newResult = currentDetector.processPose(poseResult.pose)
            android.util.Log.d("ExerciseCameraScreen", "Processed pose: state=${newResult.currentState}, " +
                    "count=${newResult.repCount}, angle=${newResult.lastAngle}, method=${newResult.detectionMethod}")
            
            // Announce new rep count
            if (newResult.repCount > detectionResult.repCount) {
                voiceFeedbackManager.announceRepCount(newResult.repCount)
                android.util.Log.d("ExerciseCameraScreen", "New rep counted: ${newResult.repCount}")
            }
            
            // Handle form feedback
            newResult.formQuality?.feedback?.let { feedback ->
                voiceFeedbackManager.announcePostureFeedback(PostureFeedback.GOOD_FORM)
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
        WorkoutSummaryScreen(
            summary = WorkoutSummary(
                totalReps = detectionResult.repCount,
                averageFormQuality = detectionResult.formQuality?.score ?: 0f,
                duration = System.currentTimeMillis() - workoutStartTime,
                goodFormReps = (detectionResult.repCount * (detectionResult.formQuality?.score ?: 75f) / 100).toInt(),
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
                // Always allow changing exercises
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
                    // Properly bind camera in the update function
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
            val poseFrameResult = poseResult.toPoseFrameResult(
                isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
            )
            
            // Always show the pose overlay in debug mode
            if (debugModeActive) {
                EnhancedPoseOverlay(
                    poseFrameResult = poseFrameResult,
                    modifier = Modifier.fillMaxSize(),
                    debugMode = true
                )
            } else if (overlayMode == "enhanced") {
                EnhancedPoseOverlay(
                    poseFrameResult = poseFrameResult,
                    modifier = Modifier.fillMaxSize(),
                    debugMode = false
                )
            } else if (overlayMode == "simple") {
                PoseOverlay(
                    pose = poseResult.pose,
                    imageWidth = poseResult.imageWidth,
                    imageHeight = poseResult.imageHeight,
                    isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // Calibration overlay
        if (calibrationState.isCalibrating || (calibrationState.isComplete && !calibrationState.isSuccessful)) {
            CalibrationOverlay(
                state = calibrationState,
                onSkipCalibration = {
                    calibrationManager.forceCompleteCalibration()
                },
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
        
        // Debug button - move it to a different position to avoid overlap with the counter
        FloatingActionButton(
            onClick = { 
                // Toggle debug mode
                debugModeActive = !debugModeActive
                // Also update settings but don't rely on it for UI state
                settingsManager.setShowDebugInfo(debugModeActive)
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp),
            containerColor = if (debugModeActive) 
                Color(0xFF4ECCA3) else Color(0xFF424255),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (debugModeActive) "HIDE" else "DEBUG", 
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Add a state indicator to show if detection is working
        if (debugModeActive) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "DETECTION DEBUG INFO",
                    color = Color(0xFF4ECCA3),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Divider(color = Color.Gray.copy(alpha = 0.5f))
                
                Text(
                    text = "Exercise: ${selectedExerciseType.displayName}",
                    color = Color.White,
                    fontSize = 14.sp
                )
                
                Text(
                    text = "State: ${detectionResult.currentState}",
                    color = when(detectionResult.currentState) {
                        ExerciseState.START_POSITION -> Color(0xFF4ECCA3)
                        ExerciseState.END_POSITION -> Color(0xFFFC5185)
                        else -> Color.White
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Reps: ${detectionResult.repCount}",
                    color = Color(0xFFFFD700),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Angle: ${detectionResult.lastAngle?.toInt() ?: "N/A"}°",
                    color = Color.White,
                    fontSize = 14.sp
                )
                
                Text(
                    text = "Confidence: ${(detectionResult.confidence * 100).toInt()}%",
                    color = if (detectionResult.confidence > 0.5f) Color(0xFF4ECCA3) else Color.Gray,
                    fontSize = 14.sp
                )
                
                Text(
                    text = "Method: ${detectionResult.detectionMethod}",
                    color = Color.White,
                    fontSize = 14.sp
                )
                
                if (calibrationState.isCalibrating) {
                    Text(
                        text = "CALIBRATING: ${calibrationState.countdown}",
                        color = Color(0xFFFFD700),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (currentPoseResult == null) {
                    Text(
                        text = "NO POSE DETECTED",
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
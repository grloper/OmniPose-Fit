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
    
    // Initialize settings manager and voice feedback
    val settingsManager = remember { SettingsManager(context) }
    val voiceFeedbackManager = remember { VoiceFeedbackManager(context) }
    
    // Camera selector state (front/back camera)
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    
    // Push-up counter state
    var pushUpResult by remember { mutableStateOf(PushUpResult(0, PushUpState.UNKNOWN, null)) }
    
    // UI state
    var showSettingsCard by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var workoutStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var currentFeedbackMessage by remember { mutableStateOf<String?>(null) }
    
    // Collect settings
    val voiceEnabled by settingsManager.voiceEnabled.collectAsState()
    val speechRate by settingsManager.speechRate.collectAsState()
    val showDebugInfo by settingsManager.showDebugInfo.collectAsState()
    val enhancedUI by settingsManager.enhancedUI.collectAsState()
    
    // Initialize push-up detector
    val pushUpDetector = remember { PushUpDetector() }
    
    // Initialize pose detection components
    val poseDetectorClient = remember { 
        PoseDetectorClient().apply { initialize() }
    }
    val imageAnalyzer = remember { ImageAnalyzer(poseDetectorClient) }
    
    // State for current pose detection result
    var currentPoseResult by remember { mutableStateOf<PoseDetectionResult?>(null) }
    
    // Update voice feedback settings when they change
    LaunchedEffect(voiceEnabled, speechRate) {
        voiceFeedbackManager.updateSettings(
            enabled = voiceEnabled,
            speechRate = speechRate
        )
    }
    
    // Collect pose results and process push-ups
    LaunchedEffect(imageAnalyzer) {
        imageAnalyzer.poseResults.collect { poseResult ->
            currentPoseResult = poseResult
            
            // Process pose for push-up detection with enhanced analysis
            val newResult = pushUpDetector.processPoseWithAnalysis(poseResult.pose)
            
            // Announce new rep count
            if (newResult.repCount > pushUpResult.repCount) {
                voiceFeedbackManager.announceRepCount(newResult.repCount)
            }
            
            // Handle posture feedback
            newResult.postureAnalysis?.feedback?.let { feedback ->
                voiceFeedbackManager.announcePostureFeedback(feedback)
                currentFeedbackMessage = when (feedback) {
                    PostureFeedback.GOOD_FORM -> "Good form!"
                    PostureFeedback.LOWER_BODY -> "Lower your body more"
                    PostureFeedback.RAISE_BODY -> "Push up higher"
                    PostureFeedback.STRAIGHTEN_BACK -> "Keep your back straight"
                    PostureFeedback.ALIGN_HANDS -> "Align your hands"
                    PostureFeedback.SLOW_DOWN -> "Slow down"
                    PostureFeedback.KEEP_GOING -> "Keep going!"
                }
            }
            
            pushUpResult = newResult
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
        val goodFormReps = (pushUpResult.repCount * (pushUpResult.postureAnalysis?.averageFormQuality ?: 75f) / 100).toInt()
        
        WorkoutSummaryScreen(
            summary = WorkoutSummary(
                totalReps = pushUpResult.repCount,
                averageFormQuality = pushUpResult.postureAnalysis?.averageFormQuality ?: 0f,
                duration = workoutDuration,
                goodFormReps = goodFormReps
            ),
            onStartNewWorkout = {
                showSummary = false
                pushUpDetector.reset()
                voiceFeedbackManager.reset()
                workoutStartTime = System.currentTimeMillis()
            },
            onBackToCamera = {
                showSummary = false
            }
        )
        return
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
        
        // Enhanced pose overlay with form quality integration
        if (showDebugInfo) {
            currentPoseResult?.let { poseResult ->
                val poseFrameResult = poseResult.toPoseFrameResult(
                    rotationDegrees = 0,
                    isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
                )
                
                EnhancedPoseOverlay(
                    poseFrameResult = poseFrameResult,
                    modifier = Modifier.fillMaxSize(),
                    debugMode = showDebugInfo
                )
            }
        }
        
        // Enhanced push-up counter with form quality
        if (enhancedUI) {
            EnhancedPushUpCounter(
                repCount = pushUpResult.repCount,
                formQuality = pushUpResult.postureAnalysis?.formQuality ?: 0f,
                averageFormQuality = pushUpResult.postureAnalysis?.averageFormQuality ?: 0f,
                hasGoodForm = pushUpResult.postureAnalysis?.hasGoodForm ?: false,
                onReset = { 
                    pushUpDetector.reset()
                    voiceFeedbackManager.reset()
                    workoutStartTime = System.currentTimeMillis()
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } else {
            // Legacy simple counter for compatibility
            PushUpOverlay(
                repCount = pushUpResult.repCount,
                onReset = { 
                    pushUpDetector.reset()
                    voiceFeedbackManager.reset()
                    workoutStartTime = System.currentTimeMillis()
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        
        // Posture feedback display
        PostureFeedbackDisplay(
            feedbackMessage = currentFeedbackMessage,
            isGoodForm = pushUpResult.postureAnalysis?.hasGoodForm ?: false,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 200.dp)
        )
        
        // Voice settings card (toggleable)
        if (showSettingsCard) {
            VoiceSettingsCard(
                isVoiceEnabled = voiceEnabled,
                speechRate = speechRate,
                onVoiceToggle = { settingsManager.setVoiceEnabled(it) },
                onSpeechRateChange = { settingsManager.setSpeechRate(it) },
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
        
        // Debug button
        FloatingActionButton(
            onClick = { settingsManager.setShowDebugInfo(!showDebugInfo) },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            containerColor = if (showDebugInfo) Color(0xFF4ECCA3) else Color(0xFF424255),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (showDebugInfo) "HIDE" else "DEBUG", 
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Camera controls and settings
        CameraControls(
            onCameraSwitch = { 
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
            },
            onSettingsToggle = { showSettingsCard = !showSettingsCard },
            onShowSummary = { 
                voiceFeedbackManager.announceWorkoutComplete(pushUpResult.repCount)
                showSummary = true 
            },
            hasReps = pushUpResult.repCount > 0,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun CameraControls(
    onCameraSwitch: () -> Unit,
    onSettingsToggle: () -> Unit,
    onShowSummary: () -> Unit,
    hasReps: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
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
            
            // Settings button
            IconButton(
                onClick = onSettingsToggle,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Voice settings",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Summary button (only if there are reps)
            if (hasReps) {
                IconButton(
                    onClick = onShowSummary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = "Show summary",
                        tint = Color(0xFF4ECCA3),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Overlay UI for displaying push-up count and reset button
 */
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
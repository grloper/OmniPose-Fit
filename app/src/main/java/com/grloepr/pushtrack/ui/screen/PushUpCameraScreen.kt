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
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.ImageAnalyzer
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import com.grloepr.pushtrack.analysis.PoseFrameResult
import com.grloepr.pushtrack.analysis.PushUpState
import com.grloepr.pushtrack.domain.GroundPositionPushUpDetector
import com.grloepr.pushtrack.domain.GroundPositionPushUpDetector.PushUpPhase
import com.grloepr.pushtrack.feedback.PostureAnalyzer
import com.grloepr.pushtrack.feedback.PostureFeedback
import com.grloepr.pushtrack.feedback.PostureAnalysisResult
import com.grloepr.pushtrack.camera.bindCameraWithAnalysis
import com.grloepr.pushtrack.camera.rememberCameraProvider
import com.grloepr.pushtrack.detection.ExerciseDetector
import com.grloepr.pushtrack.detection.ExerciseDetectorFactory
import com.grloepr.pushtrack.detection.ExercisePhase
import com.grloepr.pushtrack.detection.ExerciseType
import com.grloepr.pushtrack.feedback.VoiceFeedbackManager
import com.grloepr.pushtrack.permission.CameraPermissionDeniedContent
import com.grloepr.pushtrack.permission.CameraPermissionRequest
import com.grloepr.pushtrack.pose.PoseDetectorClient
import com.grloepr.pushtrack.ui.components.EnhancedExerciseCounter
import com.grloepr.pushtrack.ui.components.PostureFeedbackDisplay
import com.grloepr.pushtrack.ui.components.VoiceSettingsCard
import com.grloepr.pushtrack.ui.components.CameraGuidanceCard
import com.grloepr.pushtrack.ui.components.ExerciseSelectorCard // FIX: added missing import
import com.grloepr.pushtrack.ui.overlay.EnhancedPoseOverlay
import kotlinx.coroutines.flow.collect

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
fun ExerciseCameraScreen() {
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
    
    // Push-up counter state
    var repCount by remember { mutableStateOf(0) }
    
    // Debug mode toggle
    var showDebugInfo by remember { mutableStateOf(false) }
    
    // Form quality state
    var formQuality by remember { mutableStateOf(1.0f) }
    var averageFormQuality by remember { mutableStateOf(0.0f) }
    var currentPostureFeedback by remember { mutableStateOf<PostureFeedback?>(null) }
    
    // Voice feedback state
    var voiceFeedbackEnabled by remember { mutableStateOf(true) }
    
    // Exercise type and detection mode
    var currentExerciseType by remember { mutableStateOf(ExerciseType.PUSH_UP) }
    var smartModeEnabled by remember { mutableStateOf(false) }
    var showExerciseSelector by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var detectionSensitivity by remember { mutableStateOf(1.0f) } // 0.5 – 1.5
    
    // Initialize pose detection components
    val poseDetectorClient = remember { 
        PoseDetectorClient().apply { initialize() }
    }
    val imageAnalyzer = remember { ImageAnalyzer(poseDetectorClient) }
    
    // Replace ground-only detector with generic detector
    var detector by remember { mutableStateOf<ExerciseDetector?>(ExerciseDetectorFactory.createDetector(currentExerciseType)) }

    // Recreate detector when exercise type changes (manual or smart)
    LaunchedEffect(currentExerciseType) {
        detector = ExerciseDetectorFactory.createDetector(currentExerciseType).also {
            it.setSensitivity(detectionSensitivity)
        }
        repCount = 0
    }
    // Apply sensitivity live
    LaunchedEffect(detectionSensitivity, detector) {
        detector?.setSensitivity(detectionSensitivity)
    }

    // Initialize posture analyzer
    val postureAnalyzer = remember { PostureAnalyzer() }
    
    // Initialize voice feedback
    val voiceFeedbackManager = remember { 
        VoiceFeedbackManager(context)
    }
    
    // State for current pose detection result
    var currentPoseResult by remember { mutableStateOf<PoseDetectionResult?>(null) }
    
    // Previous rep count for voice feedback
    var previousRepCount by remember { mutableStateOf(0) }
    
    // Collect pose results and process push-ups
    LaunchedEffect(imageAnalyzer, smartModeEnabled) {
        imageAnalyzer.poseResults.collect { poseResult ->
            currentPoseResult = poseResult
            val pose = poseResult.pose

            if (smartModeEnabled) {
                val detected = detectExerciseType(pose)
                if (detected != currentExerciseType) {
                    currentExerciseType = detected
                    // detector will recreate via LaunchedEffect
                }
            }

            detector?.processPose(pose)

            repCount = detector?.getRepCount() ?: 0
            val phase = detector?.getCurrentPhase() ?: ExercisePhase.UP
            val mappedState = when (phase) {
                ExercisePhase.UP -> PushUpState.UP_POSITION
                ExercisePhase.DOWN -> PushUpState.DOWN_POSITION
                ExercisePhase.TRANSITIONING -> PushUpState.UNKNOWN
            }

            // Posture analysis using mapped state
            val analysisResult = postureAnalyzer.analyzePose(pose, mappedState)
            currentPostureFeedback = analysisResult.feedback
            formQuality = analysisResult.formQuality
            averageFormQuality = analysisResult.averageFormQuality
            
            // Handle voice feedback
            if (voiceFeedbackEnabled) {
                // Announce count when it changes
                if (repCount > previousRepCount) {
                    voiceFeedbackManager.announceRepCount(repCount)
                }
                
                // Voice feedback for posture issues can be added here
                
                previousRepCount = repCount
            }
        }
    }
    
    // Clean up resources when screen is disposed
    DisposableEffect(poseDetectorClient, voiceFeedbackManager) {
        onDispose {
            poseDetectorClient.close()
            voiceFeedbackManager.shutdown()
        }
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
        
        // Enhanced exercise counter with form quality
        EnhancedExerciseCounter(
            exerciseType = currentExerciseType,
            repCount = repCount,
            formQuality = formQuality,
            averageFormQuality = averageFormQuality,
            hasGoodForm = currentPostureFeedback == PostureFeedback.GOOD_FORM,
            detectionConfidence = 1.0f,
            onReset = { detector?.reset(); repCount = 0 },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // Posture feedback display
        PostureFeedbackDisplay(
            feedbackMessage = currentPostureFeedback?.toString(),
            isGoodForm = currentPostureFeedback == PostureFeedback.GOOD_FORM,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 200.dp)
        )
        
        // Voice settings card (toggleable)
        VoiceSettingsCard(
            isVoiceEnabled = voiceFeedbackEnabled,
            speechRate = 1.0f, // Fixed speech rate for simplicity
            onVoiceToggle = { enabled -> voiceFeedbackEnabled = enabled },
            onSpeechRateChange = { /* No-op */ },
            modifier = Modifier.align(Alignment.CenterStart)
        )
        
        // Camera guidance card (shows when detection confidence is low)
        CameraGuidanceCard(
            exerciseType = ExerciseType.PUSH_UP,
            detectionConfidence = 1.0f, // Fixed confidence for simplicity
            modifier = Modifier.align(Alignment.BottomStart)
        )
        
        // Debug button
        FloatingActionButton(
            onClick = { showDebugInfo = !showDebugInfo },
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
        
        // Exercise selector toggle button (floating small)
        FloatingActionButton(
            onClick = { showExerciseSelector = !showExerciseSelector },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = Color(0xFF4ECCA3),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = "Exercise",
                tint = Color.White
            )
        }

        // Settings button (separate)
        FloatingActionButton(
            onClick = { showSettings = !showSettings },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            containerColor = Color(0xFF424255),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
        }

        // Exercise selector panel
        if (showExerciseSelector) {
            ExerciseSelectorCard(
                currentExerciseType = currentExerciseType,
                smartModeEnabled = smartModeEnabled,
                onExerciseSelected = {
                    currentExerciseType = it
                    smartModeEnabled = false
                    showExerciseSelector = false
                },
                onSmartModeToggle = { enabled ->
                    smartModeEnabled = enabled
                    if (enabled) showExerciseSelector = false
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(280.dp)
            )
        }

        // Settings panel
        if (showSettings) {
            SettingsPanel(
                voiceEnabled = voiceFeedbackEnabled,
                onVoiceToggle = { voiceFeedbackEnabled = it },
                debugEnabled = showDebugInfo,
                onDebugToggle = { showDebugInfo = it },
                postureEnabled = true,
                onSensitivityChange = { detectionSensitivity = it },
                sensitivity = detectionSensitivity,
                onClose = { showSettings = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }

        // Update existing CameraControls callbacks
        CameraControls(
            onCameraSwitch = {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                    CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            },
            onSettingsToggle = { showSettings = !showSettings },
            onExerciseSelectorToggle = { showExerciseSelector = !showExerciseSelector },
            onShowSummary = { /* TODO */ },
            hasReps = repCount > 0,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun CameraControls(
    onCameraSwitch: () -> Unit,
    onSettingsToggle: () -> Unit,
    onExerciseSelectorToggle: () -> Unit,
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
            
            // Exercise selector button
            IconButton(
                onClick = onExerciseSelectorToggle,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "Select exercise type",
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

// Settings bottom panel
@Composable
private fun SettingsPanel(
    voiceEnabled: Boolean,
    onVoiceToggle: (Boolean) -> Unit,
    debugEnabled: Boolean,
    onDebugToggle: (Boolean) -> Unit,
    postureEnabled: Boolean,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Settings", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
            SettingToggleRow(
                title = "Voice Feedback",
                checked = voiceEnabled,
                onChecked = onVoiceToggle,
                icon = if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff
            )
            SettingToggleRow(
                title = "Debug Overlay",
                checked = debugEnabled,
                onChecked = onDebugToggle,
                icon = Icons.Default.BugReport
            )
            SettingToggleRow(
                title = "Posture Analysis",
                checked = postureEnabled,
                onChecked = { /* stub – posture always on for now */ },
                enabled = false,
                icon = Icons.Default.Visibility
            )
            Column {
                Text("Detection Sensitivity", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = sensitivity,
                    onValueChange = onSensitivityChange,
                    valueRange = 0.5f..1.5f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4ECCA3),
                        activeTrackColor = Color(0xFF4ECCA3)
                    )
                )
                Text(
                    text = String.format("%.2f", sensitivity),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = if (enabled) Color(0xFF4ECCA3) else Color.Gray)
            Text(title, color = if (enabled) Color.White else Color.Gray, fontSize = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChecked else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF4ECCA3),
                checkedTrackColor = Color(0xFF4ECCA3).copy(alpha = 0.5f)
            ),
            enabled = enabled
        )
    }
}

/**
 * Smart auto-detection of exercise type based on pose analysis
 * This analyzes body position and movement patterns to determine the most likely exercise
 */
private fun detectExerciseType(pose: Pose): ExerciseType {
    val landmarks = pose.allPoseLandmarks
    if (landmarks.isEmpty()) return ExerciseType.PUSH_UP // Default fallback
    
    try {
        // Get key landmarks for analysis
        val head = landmarks.find { it.landmarkType == PoseLandmark.NOSE }
        val leftShoulder = landmarks.find { it.landmarkType == PoseLandmark.LEFT_SHOULDER }
        val rightShoulder = landmarks.find { it.landmarkType == PoseLandmark.RIGHT_SHOULDER }
        val leftHip = landmarks.find { it.landmarkType == PoseLandmark.LEFT_HIP }
        val rightHip = landmarks.find { it.landmarkType == PoseLandmark.RIGHT_HIP }
        val leftKnee = landmarks.find { it.landmarkType == PoseLandmark.LEFT_KNEE }
        val rightKnee = landmarks.find { it.landmarkType == PoseLandmark.RIGHT_KNEE }
        val leftAnkle = landmarks.find { it.landmarkType == PoseLandmark.LEFT_ANKLE }
        val rightAnkle = landmarks.find { it.landmarkType == PoseLandmark.RIGHT_ANKLE }
        
        // Calculate body orientation and position
        if (head != null && leftShoulder != null && rightShoulder != null && 
            leftHip != null && rightHip != null) {
            
            val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val headY = head.position.y
            
            // Check if person is horizontal (push-up position)
            val bodyAngle = kotlin.math.abs(avgShoulderY - avgHipY)
            val isHorizontal = bodyAngle < 50f // Small Y difference indicates horizontal position
            
            // Check if head is above shoulders (standing/hanging position)
            val headAboveShoulders = headY < avgShoulderY - 20f
            
            // Check knee positions for squatting
            if (leftKnee != null && rightKnee != null && leftAnkle != null && rightAnkle != null) {
                val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2
                val avgAnkleY = (leftAnkle.position.y + rightAnkle.position.y) / 2
                val kneeBend = avgAnkleY - avgKneeY // Positive when knees are bent (squatting)
                
                // Squat detection: knees bent, body upright
                if (kneeBend > 30f && !isHorizontal && headAboveShoulders) {
                    return ExerciseType.SQUAT
                }
            }
            
            // Pull-up detection: hanging position with arms extended above head
            if (headAboveShoulders && !isHorizontal) {
                return ExerciseType.PULL_UP
            }
            
            // Push-up detection: horizontal body position
            if (isHorizontal) {
                return ExerciseType.PUSH_UP
            }
        }
    } catch (e: Exception) {
        // If any error occurs in detection, fallback to push-up
        return ExerciseType.PUSH_UP
    }
    
    // Default to push-up if no clear pattern is detected
    return ExerciseType.PUSH_UP
}
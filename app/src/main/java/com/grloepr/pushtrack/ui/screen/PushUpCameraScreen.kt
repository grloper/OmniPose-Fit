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
import com.grloepr.pushtrack.detection.*
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
import com.grloepr.pushtrack.analysis.PushUpDetector as LegacyPushUpDetector

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
    
    // Initialize settings manager and voice feedback
    val settingsManager = remember { SettingsManager(context) }
    val voiceFeedbackManager = remember { VoiceFeedbackManager(context) }
    
    // Camera selector state (front/back camera)
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    
    // Exercise detection state
    var currentExerciseType by remember { mutableStateOf(ExerciseType.PUSH_UP) }
    var smartModeEnabled by remember { mutableStateOf(false) }
    var exerciseState by remember { mutableStateOf(ExerciseState()) }
    
    // Legacy push-up state for compatibility
    var pushUpResult by remember { mutableStateOf(PushUpResult(0, PushUpState.UNKNOWN, null)) }
    
    // UI state
    var showSettingsCard by remember { mutableStateOf(false) }
    var showExerciseSelector by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var workoutStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var currentFeedbackMessage by remember { mutableStateOf<String?>(null) }
    
    // Collect settings
    val voiceEnabled by settingsManager.voiceEnabled.collectAsState()
    val speechRate by settingsManager.speechRate.collectAsState()
    val showDebugInfo by settingsManager.showDebugInfo.collectAsState()
    val enhancedUI by settingsManager.enhancedUI.collectAsState()
    
    // Initialize exercise detector based on current type
    val exerciseDetector = remember(currentExerciseType) { 
        ExerciseDetectorFactory.createDetector(currentExerciseType)
    }
    
    // Initialize legacy push-up detector for compatibility  
    val legacyPushUpDetector = remember { LegacyPushUpDetector() }
    
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
    
    // Collect pose results and process exercises
    LaunchedEffect(imageAnalyzer, currentExerciseType) {
        imageAnalyzer.poseResults.collect { poseResult ->
            currentPoseResult = poseResult
            
            // Process pose with modular detector
            exerciseDetector.processPose(poseResult.pose)
            
            // Update exercise state
            exerciseState = exerciseDetector.state.value
            
            // For compatibility, also process with legacy push-up detector if needed
            if (currentExerciseType == ExerciseType.PUSH_UP) {
                val newResult = legacyPushUpDetector.processPoseWithAnalysis(poseResult.pose)
                
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
                        else -> null // ensure exhaustiveness if enum adds values
                    }
                }
                
                pushUpResult = newResult
            } else {
                // For other exercises, announce rep count when it increases
                if (exerciseState.count > pushUpResult.repCount) {
                    voiceFeedbackManager.announceRepCount(exerciseState.count)
                }
                // Update legacy result for UI compatibility
                pushUpResult = pushUpResult.copy(repCount = exerciseState.count)
            }
            
            // Smart mode: Auto-detect exercise type based on pose patterns
            if (smartModeEnabled) {
                val detectedType = detectExerciseType(poseResult.pose)
                if (detectedType != currentExerciseType) {
                    currentExerciseType = detectedType
                    // Reset when switching exercise types
                    exerciseDetector.reset()
                    legacyPushUpDetector.reset()
                    voiceFeedbackManager.reset()
                    workoutStartTime = System.currentTimeMillis()
                }
            }
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
    DisposableEffect(poseDetectorClient, voiceFeedbackManager, exerciseDetector) {
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
                exerciseDetector.reset()
                legacyPushUpDetector.reset()
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
        
        // Enhanced exercise counter with form quality
        if (enhancedUI) {
            EnhancedExerciseCounter(
                exerciseType = currentExerciseType,
                repCount = pushUpResult.repCount,
                formQuality = pushUpResult.postureAnalysis?.formQuality ?: 0f,
                averageFormQuality = pushUpResult.postureAnalysis?.averageFormQuality ?: 0f,
                hasGoodForm = pushUpResult.postureAnalysis?.hasGoodForm ?: false,
                onReset = { 
                    exerciseDetector.reset()
                    legacyPushUpDetector.reset()
                    voiceFeedbackManager.reset()
                    workoutStartTime = System.currentTimeMillis()
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } else {
            // Legacy simple counter for compatibility
            ExerciseOverlay(
                exerciseType = currentExerciseType,
                repCount = pushUpResult.repCount,
                onReset = { 
                    exerciseDetector.reset()
                    legacyPushUpDetector.reset()
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
        
        // Exercise selector card (toggleable)
        if (showExerciseSelector) {
            ExerciseSelectorCard(
                currentExerciseType = currentExerciseType,
                smartModeEnabled = smartModeEnabled,
                onExerciseSelected = { exerciseType ->
                    currentExerciseType = exerciseType
                    // Reset detectors when switching exercise type
                    exerciseDetector.reset()
                    legacyPushUpDetector.reset()
                    voiceFeedbackManager.reset()
                    workoutStartTime = System.currentTimeMillis()
                },
                onSmartModeToggle = { smartModeEnabled = it },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        
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
            onExerciseSelectorToggle = { showExerciseSelector = !showExerciseSelector },
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

/**
 * Overlay UI for displaying exercise count and reset button
 */
@Composable
private fun ExerciseOverlay(
    exerciseType: ExerciseType,
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
                text = "${ExerciseDetectorFactory.getDisplayName(exerciseType)} Counter",
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

/**
 * Smart auto-detection of exercise type based on pose analysis
 * This analyzes body position and movement patterns to determine the most likely exercise
 */
private fun detectExerciseType(pose: Pose): ExerciseType {
    val landmarks = pose.allPoseLandmarks
    if (landmarks.isEmpty()) return ExerciseType.PUSH_UP // Default fallback
    
    try {
        // Get key landmarks for analysis
        val head = landmarks.find { it.landmarkType == com.google.mlkit.vision.pose.PoseLandmark.NOSE }
        val leftShoulder = landmarks.find { it.landmarkType == com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER }
        val rightShoulder = landmarks.find { it.landmarkType == com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER }
        val leftHip = landmarks.find { it.landmarkType == com.google.mlkit.vision.pose.PoseLandmark.LEFT_HIP }
        val rightHip = landmarks.find { it.landmarkType == com.google.mlkit.vision.pose.PoseLandmark.RIGHT_HIP }
        val leftKnee = landmarks.find { it.landmarkType == com.google.mlkit.vision.pose.PoseLandmark.LEFT_KNEE }
        val rightKnee = landmarks.find { it.landmarkType == com.google.mlkit.vision.pose.PoseLandmark.RIGHT_KNEE }
        val leftAnkle = landmarks.find { it.landmarkType == com.google.mlkit.vision.pose.PoseLandmark.LEFT_ANKLE }
        val rightAnkle = landmarks.find { it.landmarkType == com.google.mlkit.vision.pose.PoseLandmark.RIGHT_ANKLE }
        
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
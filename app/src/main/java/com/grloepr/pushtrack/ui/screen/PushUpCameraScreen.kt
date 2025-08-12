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
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlin.math.abs
import kotlin.math.min
import androidx.compose.foundation.text.BasicText

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
    // var detectionSensitivity by remember { mutableStateOf(1.0f) } // 0.5 – 1.5
    
    // Initialize pose detection components
    val poseDetectorClient = remember { 
        PoseDetectorClient().apply { initialize() }
    }
    val imageAnalyzer = remember { ImageAnalyzer(poseDetectorClient) }
    
    // Replace ground-only detector with generic detector
    var detector by remember { mutableStateOf<ExerciseDetector?>(null) }
    var resetTrigger by remember { mutableStateOf(0) }

    // Recreate detector when exercise type changes (manual or smart)
    LaunchedEffect(currentExerciseType, resetTrigger) {
        detector = ExerciseDetectorFactory.createDetector(currentExerciseType).also {
            // it.setSensitivity(detectionSensitivity)
        }
        repCount = 0
    }
    // Apply sensitivity live
    // LaunchedEffect(detectionSensitivity, detector) {
    //     detector?.setSensitivity(detectionSensitivity)
    // }

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
    
    // New state variables
    var lastAnnouncedExercise by remember { mutableStateOf<ExerciseType?>(null) } // ADDED
    var guidanceVisible by remember { mutableStateOf(false) } // ADDED
    var stableCandidate by remember { mutableStateOf<ExerciseType?>(null) }
    var candidateStableFrames by remember { mutableStateOf(0) }
    var lastSwitchTime by remember { mutableStateOf(0L) }
    var lastRepOrMovementTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastPrimaryAngleSnapshot by remember { mutableStateOf<Float?>(null) }
    val smartSwitchCooldownMs = 5000L
    val stabilityFramesRequired = 25 // ~0.5s at 50fps
    // ADDED: lift visibility tracking states to outer scope
    var fullyVisible by remember { mutableStateOf(false) }
    var visibilityHint by remember { mutableStateOf<String?>(null) }

    // Add positioning guidance state
    var lastPositioningGuidance by remember { mutableStateOf(0L) }
    val positioningGuidanceCooldown = 8000L
    
    // Initialize camera and analysis
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
        val isCompact = LocalConfiguration.current.screenHeightDp < 700
        EnhancedExerciseCounter(
            exerciseType = currentExerciseType,
            repCount = repCount,
            formQuality = formQuality,
            averageFormQuality = averageFormQuality,
            hasGoodForm = currentPostureFeedback == PostureFeedback.GOOD_FORM,
            detectionConfidence = 1.0f,
            onReset = { detector?.reset(); repCount = 0 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isCompact) 4.dp else 8.dp),
            compact = isCompact // NEW
        )
        
        // Posture feedback display
        PostureFeedbackDisplay(
            feedbackMessage = currentPostureFeedback?.toString(),
            isGoodForm = currentPostureFeedback == PostureFeedback.GOOD_FORM,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isCompact) 70.dp else 100.dp) // reduced
        )

        // Guidance toggle FAB (help) uses guidanceVisible (now declared)
        FloatingActionButton(
            onClick = { guidanceVisible = !guidanceVisible },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 88.dp),
            containerColor = if (guidanceVisible) Color(0xFF4ECCA3) else Color(0xFF424255),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Help,
                contentDescription = "Guidance",
                tint = Color.White
            )
        }

        // REMOVE duplicated exercise selector FAB if already defined earlier:
        // (Deleted second duplicate FAB at bottom-start to avoid UI duplication)

        // Guidance card conditional
        if (guidanceVisible) {
            CameraGuidanceCard(
                exerciseType = currentExerciseType,
                detectionConfidence = 0.4f,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

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
            onClick = { 
                showExerciseSelector = !showExerciseSelector
                if (showExerciseSelector) showSettings = false // Close settings when opening selector
            },
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
            onClick = { 
                showSettings = !showSettings 
                if (showSettings) showExerciseSelector = false // Close selector when opening settings
            },
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
                    showSettings = false // Close settings if open
                },
                onSmartModeToggle = { enabled ->
                    smartModeEnabled = enabled
                    if (enabled) {
                        showExerciseSelector = false
                        showSettings = false // Close settings if open
                    }
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
                onClose = { 
                    showSettings = false
                    showExerciseSelector = false // Close exercise selector if open
                },
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

        // Visibility hint chip (uses lifted state)
        if (visibilityHint != null) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = Color.Black.copy(0.6f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = visibilityHint!!,
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Collect pose results and process exercises - SEPARATED EFFECTS
    LaunchedEffect(imageAnalyzer) {
        imageAnalyzer.poseResults.collect { poseResult ->
            currentPoseResult = poseResult
            val pose = poseResult.pose

            // Enhanced visibility evaluation with voice
            val (isVisible, hint) = evaluateVisibility(pose, currentExerciseType)
            fullyVisible = isVisible
            visibilityHint = hint
            
            // Voice announce visibility issues
            if (!isVisible && hint != null && voiceFeedbackEnabled) {
                voiceFeedbackManager.announceVisibilityGuidance(hint)
                
                // Provide positioning guidance less frequently
                val now = System.currentTimeMillis()
                if (now - lastPositioningGuidance > positioningGuidanceCooldown) {
                    voiceFeedbackManager.announcePositioningGuidance(currentExerciseType.name)
                    lastPositioningGuidance = now
                }
            }
            
            if (!isVisible) {
                currentPostureFeedback = null
                previousRepCount = repCount
                return@collect
            }

            // Smart mode stable detection
            if (smartModeEnabled) {
                val rawDetected = classifyExercise(pose)
                if (stableCandidate == null || rawDetected != stableCandidate) {
                    stableCandidate = rawDetected
                    candidateStableFrames = 1
                } else {
                    candidateStableFrames++
                }
                val now = System.currentTimeMillis()
                if (candidateStableFrames >= stabilityFramesRequired &&
                    rawDetected != currentExerciseType &&
                    now - lastSwitchTime > smartSwitchCooldownMs
                ) {
                    currentExerciseType = rawDetected
                    lastSwitchTime = now
                    voiceFeedbackManager.announceExerciseDetectionCountdown(
                        rawDetected.name.replace('_',' ').lowercase().replaceFirstChar { it.uppercase() }
                    )
                    voiceFeedbackManager.announceExerciseIntro(
                        rawDetected.name.replace('_',' ').lowercase().replaceFirstChar { it.uppercase() }
                    )
                    lastAnnouncedExercise = rawDetected
                    resetTrigger++ // Force detector recreation
                    repCount = 0
                    previousRepCount = 0
                    return@collect // Skip processing with old detector
                }
            }

            // Process pose with current detector (safely)
            detector?.processPose(pose)
        }
    }
    
    // SEPARATE effect for detector state collection
    LaunchedEffect(detector) {
        detector?.state?.collect { detectorState ->
            // Movement presence heuristic (primary angle change)
            val primaryAngleNow = detectorState.primaryAngle
            if (primaryAngleNow != null && lastPrimaryAngleSnapshot != null) {
                if (abs(primaryAngleNow - lastPrimaryAngleSnapshot!!) > 2f) {
                    lastRepOrMovementTime = System.currentTimeMillis()
                }
            }
            lastPrimaryAngleSnapshot = primaryAngleNow

            repCount = detectorState.count
            val phase = detectorState.phase
            val mappedState = when (phase) {
                ExercisePhase.UP -> PushUpState.UP_POSITION
                ExercisePhase.DOWN -> PushUpState.DOWN_POSITION
                ExercisePhase.TRANSITIONING -> PushUpState.UNKNOWN
            }

            val analysisResult = currentPoseResult?.pose?.let { pose -> 
                postureAnalyzer.analyzePose(pose, mappedState) 
            }
            if (analysisResult != null) {
                currentPostureFeedback = analysisResult.feedback
                formQuality = analysisResult.formQuality
                averageFormQuality = analysisResult.averageFormQuality
            }

            val nowTs = System.currentTimeMillis()

            if (voiceFeedbackEnabled) {
                // Rep announcements
                if (repCount > previousRepCount) {
                    voiceFeedbackManager.announceRepCount(repCount)
                    voiceFeedbackManager.maybeEncourage(repCount)
                    previousRepCount = repCount
                    lastRepOrMovementTime = nowTs
                }
                // Posture voice (throttled inside manager)
                voiceFeedbackManager.providePostureFeedback(currentPostureFeedback, formQuality)
                // Inactivity prompt (>12s, no reps)
                if (repCount == 0 && nowTs - lastRepOrMovementTime > 12000) {
                    voiceFeedbackManager.announceExerciseIntro(
                        currentExerciseType.name.replace('_',' ').lowercase().replaceFirstChar { it.uppercase() }
                    )
                    lastRepOrMovementTime = nowTs
                }
            }
        }
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
            Text(
                "Smart Mode: auto exercise detection stabilized to reduce flicker.",
                color = Color.Gray, fontSize = 12.sp
            )
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

// Replace classifyExercise implementation
private fun classifyExercise(pose: Pose): ExerciseType {
    val lm = { id: Int -> pose.getPoseLandmark(id) }
    val lSh = lm(PoseLandmark.LEFT_SHOULDER)
    val rSh = lm(PoseLandmark.RIGHT_SHOULDER)
    val lHip = lm(PoseLandmark.LEFT_HIP)
    val rHip = lm(PoseLandmark.RIGHT_HIP)
    val lWr = lm(PoseLandmark.LEFT_WRIST)
    val rWr = lm(PoseLandmark.RIGHT_WRIST)
    val nose = lm(PoseLandmark.NOSE)
    if (lSh == null || rSh == null || lHip == null || rHip == null) return ExerciseType.PUSH_UP

    val shoulderMidY = (lSh.position.y + rSh.position.y) / 2f
    val hipMidY = (lHip.position.y + rHip.position.y) / 2f
    val torsoSpan = kotlin.math.abs(hipMidY - shoulderMidY)
    val shoulderWidth = kotlin.math.abs(lSh.position.x - rSh.position.x)
    val isHorizontal = torsoSpan < shoulderWidth * 0.55f

    val wristsHigh = if (lWr != null && rWr != null && nose != null) {
        val refY = shoulderMidY - 20f // use shoulders to allow bent knees
        (lWr.position.y < refY && rWr.position.y < refY)
    } else false

    if (!isHorizontal && wristsHigh) return ExerciseType.PULL_UP
    if (isHorizontal) return ExerciseType.PUSH_UP

    // Squat heuristic (upright & knees bending) minimized here; keep previous logic if needed
    val lK = lm(PoseLandmark.LEFT_KNEE); val rK = lm(PoseLandmark.RIGHT_KNEE)
    val lA = lm(PoseLandmark.LEFT_ANKLE); val rA = lm(PoseLandmark.RIGHT_ANKLE)
    if (lK != null && rK != null && lA != null && rA != null) {
        val kneeAvgY = (lK.position.y + rK.position.y) / 2f
        val ankleAvgY = (lA.position.y + rA.position.y) / 2f
        if ((ankleAvgY - kneeAvgY) > 25f) return ExerciseType.SQUAT
    }
    return ExerciseType.PUSH_UP
}

// Enhanced visibility evaluation with better messages
private fun evaluateVisibility(pose: Pose, exerciseType: ExerciseType): Pair<Boolean,String?> {
    val landmarks = pose.allPoseLandmarks
    if (landmarks.isEmpty()) return false to "No person detected. Step into camera view."
    
    val high = landmarks.filter { it.inFrameLikelihood >= 0.6f }
    if (high.size < 10) return false to "Move closer or improve lighting."
    
    fun need(vararg ids: Int): Boolean =
        ids.all { id -> pose.getPoseLandmark(id)?.inFrameLikelihood ?: 0f >= 0.6f }

    val coreOk = need(
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP
    )
    if (!coreOk) return false to "Show your upper body clearly."

    when (exerciseType) {
        ExerciseType.PUSH_UP -> {
            val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
            val lSh = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
            val lHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
            
            if (nose?.inFrameLikelihood ?: 0f < 0.7f) {
                return false to "Position camera above you. Look down at the ground."
            }
            
            if (lSh != null && lHip != null) {
                val torsoLength = abs(lSh.position.y - lHip.position.y)
                if (torsoLength < 60f) {
                    return false to "Move camera higher to see your full body."
                }
            }
        }
        
        ExerciseType.PULL_UP -> {
            val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
            val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
            val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
            val shouldersOk =
                (pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.inFrameLikelihood ?: 0f) > 0.55f &&
                (pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)?.inFrameLikelihood ?: 0f) > 0.55f
            if (lw == null || rw == null || !shouldersOk) {
                return false to "Show both hands and shoulders."
            }
            val noseConf = nose?.inFrameLikelihood ?: 0f
            if (noseConf >= 0.55f && nose != null) { // ensure non-null for smart cast
                val avgWristY = (lw.position.y + rw.position.y) / 2f
                if (avgWristY > nose.position.y) {
                    return false to "Raise hands higher for pull ups."
                }
            }
        }
        
        ExerciseType.SQUAT -> {
            val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
            val rk = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
            val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
            val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
            
            if (lk == null || rk == null || la == null || ra == null) {
                return false to "Show your full legs for squats."
            }
        }
    }
    return true to null
}
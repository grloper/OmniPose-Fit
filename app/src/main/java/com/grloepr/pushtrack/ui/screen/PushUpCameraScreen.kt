package com.grloepr.pushtrack.ui.screen

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.detection.*
import com.grloepr.pushtrack.camera.rememberCameraProvider
import com.grloepr.pushtrack.camera.bindCameraWithAnalysis
import com.grloepr.pushtrack.pose.PoseDetectorClient
import com.grloepr.pushtrack.analysis.ImageAnalyzer
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ExerciseCameraScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProvider = rememberCameraProvider()
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }

    // Current exercise mode
    var exerciseType by remember { mutableStateOf(ExerciseType.PUSH_UP) }
    var debug by remember { mutableStateOf(true) }

    // Minimal detectors map
    val detector: ExerciseDetector = remember(exerciseType) {
        when (exerciseType) {
            ExerciseType.PUSH_UP -> PushUpDetector()
            ExerciseType.PULL_UP -> PullUpDetector()
            ExerciseType.SQUAT -> SquatDetector()
        }
    }

    // Pose stream
    val poseClient = remember { PoseDetectorClient().apply { initialize() } }
    val analyzer = remember { ImageAnalyzer(poseClient) }

    var latestLandmarks by remember { mutableStateOf<List<PoseLandmark>>(emptyList()) }
    var repCount by remember { mutableStateOf(0) }
    var phase by remember { mutableStateOf(ExercisePhase.TRANSITIONING) }
    var primaryAngle by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(Unit) {
        onDispose { poseClient.close() }
    }

    LaunchedEffect(analyzer, detector) {
        analyzer.poseResults.collectLatest { result ->
            val pose = result.pose
            detector.processPose(pose)
            latestLandmarks = pose.allPoseLandmarks
            repCount = detector.getRepCount()
            phase = detector.getCurrentPhase()
            primaryAngle = detector.state.value.primaryAngle
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            },
            modifier = Modifier.fillMaxSize(),
            update = { pv ->
                cameraProvider?.let {
                    bindCameraWithAnalysis(
                        cameraProvider = it,
                        previewView = pv,
                        lifecycleOwner = lifecycleOwner as LifecycleOwner,
                        imageAnalyzer = analyzer,
                        cameraSelector = cameraSelector
                    )
                }
            }
        )

        if (debug) {
            SkeletonOverlay(landmarks = latestLandmarks, modifier = Modifier.fillMaxSize())
        }

        ControlPanel(
            exerciseType = exerciseType,
            onSelect = { exerciseType = it },
            debug = debug,
            onToggleDebug = { debug = !debug },
            repCount = repCount,
            phase = phase,
            primaryAngle = primaryAngle,
            onSwitchCamera = {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                    CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp)
        )
    }
}

@Composable
private fun ControlPanel(
    exerciseType: ExerciseType,
    onSelect: (ExerciseType) -> Unit,
    debug: Boolean,
    onToggleDebug: () -> Unit,
    repCount: Int,
    phase: ExercisePhase,
    primaryAngle: Float?,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(0.55f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseType.values().forEach {
                    FilterChip(
                        selected = it == exerciseType,
                        onClick = { onSelect(it) },
                        label = { Text(it.name.lowercase().replace('_',' '), color = Color.White) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onSwitchCamera,
                    label = { Text("Camera", color = Color.White) }
                )
                AssistChip(
                    onClick = onToggleDebug,
                    label = { Text(if (debug) "Hide Skeleton" else "Show Skeleton", color = Color.White) }
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Reps: $repCount", color = Color.White)
            Text("Phase: ${phase.name}", color = Color.White)
            Text("Angle: ${primaryAngle?.let { String.format("%.1f", it) } ?: "--"}", color = Color.White)
        }
    }
}

@Composable
private fun SkeletonOverlay(
    landmarks: List<PoseLandmark>,
    modifier: Modifier = Modifier
) {
    // Minimal direct draw (no allocations inside loop)
    Canvas(modifier = modifier) {
        val connections = listOf(
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
            PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
            PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
        )
        val map = landmarks.associateBy { it.landmarkType }
        // Lines
        connections.forEach { (a, b) ->
            val la = map[a]; val lb = map[b]
            if (la != null && lb != null) {
                drawLine(
                    color = Color.Cyan,
                    start = androidx.compose.ui.geometry.Offset(la.position.x, la.position.y),
                    end = androidx.compose.ui.geometry.Offset(lb.position.x, lb.position.y),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
        }
        // Points
        landmarks.forEach { lm ->
            drawCircle(
                color = if (lm.inFrameLikelihood > 0.5f) Color.Green else Color.Red,
                radius = 6f,
                center = androidx.compose.ui.geometry.Offset(lm.position.x, lm.position.y)
            )
        }
    }
}
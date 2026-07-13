package com.grloepr.pushtrack.ui.screen

import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.grloepr.pushtrack.analysis.ImageAnalyzer
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import com.grloepr.pushtrack.anatomy.AnatomyCanvas
import com.grloepr.pushtrack.anatomy.MuscleGroup
import com.grloepr.pushtrack.audio.TempoTickPlayer
import com.grloepr.pushtrack.camera.bindCameraWithAnalysis
import com.grloepr.pushtrack.camera.rememberCameraProvider
import com.grloepr.pushtrack.engine.AlignmentStatus
import com.grloepr.pushtrack.engine.CameraPlane
import com.grloepr.pushtrack.engine.DynamicExerciseEngine
import com.grloepr.pushtrack.engine.EngineFrame
import com.grloepr.pushtrack.engine.EnginePhase
import com.grloepr.pushtrack.engine.ExerciseSchema
import com.grloepr.pushtrack.engine.PoseSnapshot
import com.grloepr.pushtrack.pose.PoseDetectorClient
import com.grloepr.pushtrack.progression.SkillNode
import com.grloepr.pushtrack.tts.TextToSpeechManager
import com.grloepr.pushtrack.ui.components.CameraAngleBanner
import com.grloepr.pushtrack.ui.components.GlassPanel
import com.grloepr.pushtrack.ui.components.MasteryCelebration
import com.grloepr.pushtrack.ui.components.OptimalEdgeGlow
import com.grloepr.pushtrack.ui.components.RepCounterDial
import com.grloepr.pushtrack.ui.components.StateMachineRibbon
import com.grloepr.pushtrack.ui.components.TempoPulseIndicator
import com.grloepr.pushtrack.ui.overlay.ScannerViewfinder
import com.grloepr.pushtrack.ui.overlay.SchemaTrackingOverlay
import com.grloepr.pushtrack.ui.theme.SignalAmber
import com.grloepr.pushtrack.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The live AI training arena: camera feed + schema-driven tracking overlay,
 * rep dial with depth gauge, tempo pacer, state-machine ribbon, spatial
 * camera guidance and the mastery celebration — everything the athlete needs,
 * nothing they don't.
 */
@Composable
fun TrainingScreen(
    node: SkillNode,
    schema: ExerciseSchema,
    alreadyMastered: Boolean,
    onMastered: () -> Unit,
    onExit: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val cameraProvider = rememberCameraProvider()

    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    var soundOn by remember { mutableStateOf(true) }

    val ttsManager = remember { TextToSpeechManager(context) }
    val tickPlayer = remember { TempoTickPlayer() }
    val poseDetectorClient = remember { PoseDetectorClient().apply { initialize() } }
    val imageAnalyzer = remember { ImageAnalyzer(poseDetectorClient) }

    var poseResult by remember { mutableStateOf<PoseDetectionResult?>(null) }
    var frame by remember { mutableStateOf(EngineFrame.idle()) }
    val engine = remember(schema) {
        DynamicExerciseEngine(schema) { emitted -> frame = emitted }
    }

    var celebrationVisible by remember { mutableStateOf(false) }
    var masteryFired by remember { mutableStateOf(alreadyMastered) }
    var showPartialHint by remember { mutableStateOf(false) }
    val repFlash = remember { Animatable(0f) }

    val targetMuscles = remember(schema) { MuscleGroup.fromSchemaNames(schema.targetMuscles) }

    // Pose stream → engine
    LaunchedEffect(imageAnalyzer, engine) {
        engine.reset()
        imageAnalyzer.poseResults.collect { result ->
            poseResult = result
            engine.onPose(PoseSnapshot.fromMlKit(result.pose), System.currentTimeMillis())
        }
    }

    // Voice rep announcements + rep flash on the overlay
    LaunchedEffect(frame.repCount) {
        if (frame.repCount > 0) {
            if (schema.isHold) {
                ttsManager.speak("Hold complete!")
            } else {
                ttsManager.announceRepCount(frame.repCount)
            }
            repFlash.snapTo(1f)
            repFlash.animateTo(0f, animationSpec = tween(650))
        }
    }

    // Mastery goal
    LaunchedEffect(frame.repCount) {
        if (!masteryFired && frame.repCount >= node.masteryReps) {
            masteryFired = true
            onMastered()
            ttsManager.speak("Incredible. Skill mastered!")
            celebrationVisible = true
        }
    }

    // Partial-rep coaching hint
    LaunchedEffect(frame.partialReps) {
        if (frame.partialReps > 0) {
            showPartialHint = true
            delay(1800)
            showPartialHint = false
        }
    }

    // Audio tempo pacer — a soft pip on every beat while tracking is live.
    LaunchedEffect(soundOn) {
        if (!soundOn) return@LaunchedEffect
        while (isActive) {
            delay(schema.tempoPulseIntervalMs)
            if (frame.phase != EnginePhase.SEARCHING) tickPlayer.tick()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            poseDetectorClient.close()
            ttsManager.shutdown()
            tickPlayer.release()
        }
    }

    BackHandler(onBack = onExit)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera feed
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
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

        // Schema-driven joint overlay
        poseResult?.let { result ->
            SchemaTrackingOverlay(
                poseResult = result,
                schema = schema,
                phase = frame.phase,
                isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA,
                repFlash = repFlash.value,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Scanner while the engine hunts for an athlete
        AnimatedVisibility(
            visible = frame.phase == EnginePhase.SEARCHING,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(400))
        ) {
            ScannerViewfinder(modifier = Modifier.fillMaxSize())
        }

        // Ambient edge glow when the camera angle is optimal
        OptimalEdgeGlow(visible = frame.alignment.status == AlignmentStatus.OPTIMAL)

        // ── Top chrome ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExerciseChip(node = node, plane = schema.optimalPlane)
            Spacer(modifier = Modifier.height(10.dp))
            CameraAngleBanner(alignment = frame.alignment)
        }

        // Partial-rep hint
        AnimatedVisibility(
            visible = showPartialHint,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 120.dp)
        ) {
            Text(
                text = if (schema.isHold) {
                    "Hold broken — get back into position and hold it longer"
                } else {
                    "Half rep — hit full depth for it to count"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = SignalAmber,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ── Bottom HUD ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TempoPulseIndicator(
                            intervalMs = schema.tempoPulseIntervalMs,
                            active = frame.phase != EnginePhase.SEARCHING,
                            avgRepDurationMs = frame.avgRepDurationMs
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AnatomyCanvas(
                            highlighted = targetMuscles,
                            showBackView = false,
                            highlightIntensity = when (frame.phase) {
                                EnginePhase.SEARCHING -> 0.25f
                                EnginePhase.READY -> 0.55f
                                else -> 1f
                            },
                            modifier = Modifier.size(width = 64.dp, height = 96.dp)
                        )
                    }

                    val holdTargetMs = schema.holdTargetMs
                    if (holdTargetMs != null) {
                        // For isometric skills the dial counts hold seconds, and
                        // the ring fills as the hold approaches its target.
                        RepCounterDial(
                            repCount = (frame.holdMs / 1000L).toInt(),
                            goalReps = (holdTargetMs / 1000L).toInt(),
                            progress = frame.progress,
                            unitLabel = "sec hold"
                        )
                    } else {
                        RepCounterDial(
                            repCount = frame.repCount,
                            goalReps = node.masteryReps,
                            progress = frame.progress
                        )
                    }

                    StateMachineRibbon(phase = frame.phase, isHold = schema.isHold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                ControlButton(
                    icon = Icons.Rounded.Cameraswitch,
                    contentDescription = "Switch camera",
                    onClick = {
                        cameraSelector =
                            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }
                    }
                )
                ControlButton(
                    icon = if (soundOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDescription = "Toggle tempo sound",
                    onClick = { soundOn = !soundOn }
                )
                ControlButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Reset reps",
                    onClick = { engine.reset() }
                )
                ControlButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "End session",
                    onClick = onExit
                )
            }
        }

        // Mastery celebration
        MasteryCelebration(
            visible = celebrationVisible,
            skillTitle = node.title,
            xpReward = node.xpReward,
            onContinue = onExit
        )
    }
}

@Composable
private fun ExerciseChip(node: SkillNode, plane: CameraPlane) {
    GlassPanel(shape = RoundedCornerShape(50)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = node.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Rounded.Videocam,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = when (plane) {
                    CameraPlane.SAGITTAL -> "Side view"
                    CameraPlane.FRONTAL -> "Face camera"
                    CameraPlane.ANY -> "Any angle"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    GlassPanel(shape = CircleShape) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White
            )
        }
    }
}

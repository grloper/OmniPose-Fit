package com.grloepr.pushtrack.ui.screen

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.grloepr.pushtrack.analysis.ImageAnalyzer
import com.grloepr.pushtrack.analysis.PoseDetectionResult
import com.grloepr.pushtrack.audio.CompletionCue
import com.grloepr.pushtrack.audio.SessionFeedback
import com.grloepr.pushtrack.audio.SuccessSoundPlayer
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
    val soundPreferences = remember(context) { context.applicationContext.getSharedPreferences("training_feedback", 0) }
    var soundOn by remember { mutableStateOf(soundPreferences.getBoolean("sound_enabled", true)) }
    var paused by remember { mutableStateOf(false) }
    var sessionPreviouslyOpened by rememberSaveable(node.id) { mutableStateOf(false) }
    val recoveredSession = remember { sessionPreviouslyOpened }
    LaunchedEffect(Unit) { sessionPreviouslyOpened = true }
    var sessionActive by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var cameraError by remember { mutableStateOf(false) }
    val previewView = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }

    val successPlayer = remember { SuccessSoundPlayer(context.applicationContext) }
    val poseDetectorClient = remember { PoseDetectorClient().apply { initialize() } }
    val imageAnalyzer = remember { ImageAnalyzer(poseDetectorClient) }

    var poseResult by remember { mutableStateOf<PoseDetectionResult?>(null) }
    var frame by remember { mutableStateOf(EngineFrame.idle()) }
    var celebrationVisible by remember { mutableStateOf(false) }
    var masteryFired by remember { mutableStateOf(alreadyMastered) }
    var newlyUnlocked by remember { mutableStateOf(false) }
    var showPartialHint by remember { mutableStateOf(false) }
    var completionPulse by remember { mutableStateOf(0) }
    val repFlash = remember { Animatable(0f) }
    val feedback = remember(schema, node) { SessionFeedback(node.masteryReps, schema.isHold) }
    val engine = remember(schema, node) {
        DynamicExerciseEngine(schema) { emitted ->
            frame = emitted
            val event = feedback.accept(emitted, sessionActive && !paused, soundOn)
            if (event != null) {
                completionPulse += 1
                if (event.audible) {
                    successPlayer.play(event.cue)
                }
                if (event.cue == CompletionCue.TARGET) {
                    newlyUnlocked = node.id == schema.id && !masteryFired
                    if (newlyUnlocked) { masteryFired = true; onMastered() }
                    celebrationVisible = true
                }
            }
        }
    }


    // Keep the preview bound while avoiding detector work outside active training.
    SideEffect { imageAnalyzer.setEnabled(sessionActive && !paused && !celebrationVisible) }

    // Pose stream → engine
    LaunchedEffect(imageAnalyzer, engine) {
        engine.reset()
        imageAnalyzer.poseResults.collect { result ->
            if (!imageAnalyzer.isCurrent(result)) return@collect
            if (!sessionActive || paused || celebrationVisible) return@collect
            poseResult = result
            engine.onPose(PoseSnapshot.fromMlKit(result.pose), result.timestampMs)
        }
    }

    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) sessionActive = true
            if (event == Lifecycle.Event.ON_PAUSE) {
                sessionActive = false
                imageAnalyzer.setEnabled(false)
                successPlayer.stop()
                imageAnalyzer.invalidate()
                engine.interrupt(SystemClock.elapsedRealtime())
                poseResult = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Bind only when camera configuration changes, not on every pose recomposition.
    DisposableEffect(cameraProvider, previewView, lifecycleOwner, imageAnalyzer, cameraSelector) {
        cameraError = false
        val binding = cameraProvider?.let { provider ->
            try {
                bindCameraWithAnalysis(provider, previewView, lifecycleOwner, imageAnalyzer, cameraSelector)
            } catch (_: Exception) {
                cameraError = true
                null
            }
        }
        onDispose {
            imageAnalyzer.invalidate()
            binding?.close()
        }
    }

    LaunchedEffect(completionPulse) {
        if (completionPulse > 0 && sessionActive && !paused) {
            repFlash.snapTo(1f)
            repFlash.animateTo(0f, animationSpec = tween(650))
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
    LaunchedEffect(soundOn, sessionActive, paused, celebrationVisible) {
        if (!soundOn || !sessionActive || paused || celebrationVisible) return@LaunchedEffect
        while (isActive) {
            delay(schema.tempoPulseIntervalMs)
            if (frame.phase != EnginePhase.SEARCHING) successPlayer.play(CompletionCue.TEMPO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            poseDetectorClient.close()
            successPlayer.release()
        }
    }

    BackHandler(onBack = onExit)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera feed
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize().testTag("camera-preview").semantics {
                stateDescription = if (poseResult != null) "Pose analysis active" else "Waiting for pose analysis"
                analysisTimestampMs = poseResult?.timestampMs ?: -1L
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
            visible = !paused && frame.phase == EnginePhase.SEARCHING,
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
            if (recoveredSession) {
                Text("New session after restart - previous session counts were reset.",
                    style = MaterialTheme.typography.bodySmall, color = SignalAmber)
            }
            Text(
                text = if (node.id == schema.id) {
                    "Estimates joint motion; does not certify technique or mastery"
                } else {
                    "Generic ${schema.displayName} tracking; variant is not validated"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.7f)).padding(8.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (paused) {
                Text("Paused — completed counts are kept. Resume from the start posture.", color = Color.White)
            } else if (cameraProvider == null) {
                Text("Starting camera…", color = Color.White)
            } else if (cameraError) {
                Text("Camera unavailable. Try switching camera or end this session.", color = SignalAmber)
            } else {
                CameraAngleBanner(alignment = frame.alignment)
            }
            Text(
                text = "Completed: ${frame.repCount}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("rep-status")
            )
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

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StateMachineRibbon(phase = frame.phase, isHold = schema.isHold)
                        Spacer(modifier = Modifier.height(12.dp))
                        TempoPulseIndicator(
                            intervalMs = schema.tempoPulseIntervalMs,
                            active = sessionActive && !paused && frame.phase != EnginePhase.SEARCHING,
                            avgRepDurationMs = frame.avgRepDurationMs
                        )
                    }
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
                        imageAnalyzer.invalidate()
                        engine.interrupt(SystemClock.elapsedRealtime())
                        poseResult = null
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
                    contentDescription = if (soundOn) "Mute training audio" else "Enable training audio",
                    onClick = {
                        soundOn = !soundOn
                        soundPreferences.edit().putBoolean("sound_enabled", soundOn).apply()
                        if (!soundOn) { successPlayer.stop() }
                    }
                )
                ControlButton(
                    icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (paused) "Resume tracking" else "Pause tracking",
                    onClick = {
                        paused = !paused
                        imageAnalyzer.setEnabled(sessionActive && !paused && !celebrationVisible)
                        imageAnalyzer.invalidate()
                        engine.interrupt(SystemClock.elapsedRealtime())
                        poseResult = null
                        successPlayer.stop()
                    }
                )
                ControlButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Reset reps",
                    onClick = {
                        imageAnalyzer.invalidate()
                        feedback.reset()
                        engine.reset()
                        poseResult = null
                        showPartialHint = false
                        celebrationVisible = false
                        completionPulse = 0
                        successPlayer.stop()
                    }
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
            xpReward = if (newlyUnlocked) node.xpReward else 0,
            onContinue = onExit,
            onKeepPracticing = {
                imageAnalyzer.invalidate()
                engine.interrupt(SystemClock.elapsedRealtime())
                poseResult = null
                celebrationVisible = false
            }
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

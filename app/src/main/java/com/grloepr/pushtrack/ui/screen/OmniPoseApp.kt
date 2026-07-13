package com.grloepr.pushtrack.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.grloepr.pushtrack.engine.ExerciseLibrary
import com.grloepr.pushtrack.permission.CameraPermissionDeniedContent
import com.grloepr.pushtrack.permission.CameraPermissionRequest
import com.grloepr.pushtrack.progression.SkillNode
import com.grloepr.pushtrack.progression.rememberSkillTreeState
import com.grloepr.pushtrack.ui.theme.DeepSpace
import com.grloepr.pushtrack.ui.theme.TextMuted
import com.grloepr.pushtrack.ui.tree.SkillTreeScreen
import androidx.compose.ui.unit.dp

/**
 * Root of the OmniPose Fit experience: the skill tree is home; tapping
 * "Start AI training" on an unlocked node dives into the camera arena.
 */
@Composable
fun OmniPoseApp() {
    val treeState = rememberSkillTreeState()
    var trainingNode by remember { mutableStateOf<SkillNode?>(null) }

    AnimatedContent(
        targetState = trainingNode,
        transitionSpec = {
            (fadeIn(tween(320)) + scaleIn(initialScale = 0.96f, animationSpec = tween(320)))
                .togetherWith(fadeOut(tween(220)) + scaleOut(targetScale = 1.03f, animationSpec = tween(220)))
        },
        label = "rootNavigation"
    ) { node ->
        if (node == null) {
            SkillTreeScreen(
                treeState = treeState,
                onStartTraining = { selected -> trainingNode = selected }
            )
        } else {
            TrainingFlow(
                node = node,
                alreadyMastered = treeState.isMastered(node.id),
                onMastered = { treeState.master(node.id) },
                onExit = { trainingNode = null }
            )
        }
    }
}

@Composable
private fun TrainingFlow(
    node: SkillNode,
    alreadyMastered: Boolean,
    onMastered: () -> Unit,
    onExit: () -> Unit
) {
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    when {
        permissionDenied -> CameraPermissionDeniedContent()

        permissionGranted -> {
            val context = LocalContext.current
            val schema = remember(node.schemaId) {
                node.schemaId?.let { ExerciseLibrary.get(context, it) }
            }
            if (schema == null) {
                MissingSchemaScreen(node = node, onExit = onExit)
            } else {
                TrainingScreen(
                    node = node,
                    schema = schema,
                    alreadyMastered = alreadyMastered,
                    onMastered = onMastered,
                    onExit = onExit
                )
            }
        }

        else -> CameraPermissionRequest(
            onPermissionGranted = { permissionGranted = true },
            onPermissionDenied = { permissionDenied = true }
        )
    }
}

@Composable
private fun MissingSchemaScreen(node: SkillNode, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No tracking schema for “${node.title}” yet",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Drop a JSON definition into assets/exercises to enable AI tracking for this skill.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onExit) { Text("Back to the tree") }
    }
}

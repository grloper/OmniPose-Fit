package com.grloepr.pushtrack.ui.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.anatomy.InteractiveAnatomyPanel
import com.grloepr.pushtrack.progression.CalisthenicsSkillGraph
import com.grloepr.pushtrack.progression.SkillNode
import com.grloepr.pushtrack.progression.SkillStatus
import com.grloepr.pushtrack.ui.components.VideoPlaceholder
import com.grloepr.pushtrack.ui.theme.AbyssBlack
import com.grloepr.pushtrack.ui.theme.AchievementGold
import com.grloepr.pushtrack.ui.theme.DeepSpace
import com.grloepr.pushtrack.ui.theme.ElectricCyan
import com.grloepr.pushtrack.ui.theme.GoldDeep
import com.grloepr.pushtrack.ui.theme.OutlineSteel
import com.grloepr.pushtrack.ui.theme.SurfaceHigh
import com.grloepr.pushtrack.ui.theme.SurfaceRaised
import com.grloepr.pushtrack.ui.theme.TextBright
import com.grloepr.pushtrack.ui.theme.TextFaint
import com.grloepr.pushtrack.ui.theme.TextMuted
import com.grloepr.pushtrack.ui.theme.VoltLime

/**
 * Pre-view modal launched from any skill node: technique video placeholder,
 * target muscles rendered on the anatomy model, prerequisite chain and the
 * call-to-action appropriate to the node's state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillDetailSheet(
    node: SkillNode,
    status: SkillStatus,
    masteredIds: Set<String>,
    onDismiss: () -> Unit,
    onStartTraining: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceRaised,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OutlineSteel) }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 22.dp)
        ) {
            SheetHeader(node, status)

            Spacer(modifier = Modifier.height(18.dp))
            VideoPlaceholder(title = node.title)

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = node.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Target muscles")
            Spacer(modifier = Modifier.height(6.dp))
            InteractiveAnatomyPanel(
                targeted = node.targetMuscles,
                figureHeight = 210.dp
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Requirements")
            Spacer(modifier = Modifier.height(8.dp))
            PrerequisiteChips(node, masteredIds)

            Spacer(modifier = Modifier.height(22.dp))
            CallToAction(node, status, onStartTraining)
        }
    }
}

@Composable
private fun SheetHeader(node: SkillNode, status: SkillStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    when (status) {
                        SkillStatus.MASTERED -> Brush.linearGradient(listOf(AchievementGold, GoldDeep))
                        else -> Brush.linearGradient(listOf(SurfaceHigh, DeepSpace))
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = node.icon.asVector(),
                contentDescription = null,
                tint = if (status == SkillStatus.MASTERED) DeepSpace else ElectricCyan,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextBright
            )
            Text(
                text = node.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DifficultyDots(node.difficulty)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "+${node.xpReward} XP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = VoltLime
                )
            }
        }
        StatusPill(status)
    }
}

@Composable
private fun DifficultyDots(difficulty: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        if (index < difficulty) ElectricCyan else SurfaceHigh,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun StatusPill(status: SkillStatus) {
    val (label, color) = when (status) {
        SkillStatus.LOCKED -> "Locked" to TextFaint
        SkillStatus.AVAILABLE -> "Ready" to ElectricCyan
        SkillStatus.MASTERED -> "Mastered" to AchievementGold
    }
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = TextFaint
    )
}

@Composable
private fun PrerequisiteChips(node: SkillNode, masteredIds: Set<String>) {
    if (node.prerequisites.isEmpty()) {
        Text(
            text = "Foundation skill — no prerequisites",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        node.prerequisites.forEach { prereqId ->
            val prereq = CalisthenicsSkillGraph.byId(prereqId) ?: return@forEach
            PrereqChip(prereq = prereq, isMastered = prereqId in masteredIds)
        }
    }
}

@Composable
private fun PrereqChip(prereq: SkillNode, isMastered: Boolean) {
    val accent = if (isMastered) VoltLime else TextFaint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = if (isMastered) Icons.Rounded.Check else Icons.Rounded.Lock,
            contentDescription = if (isMastered) "Mastered" else "Locked",
            tint = accent,
            modifier = Modifier
                .size(22.dp)
                .background(accent.copy(alpha = 0.14f), CircleShape)
                .padding(4.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = prereq.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (isMastered) TextBright else TextMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isMastered) "MASTERED" else "REQUIRED",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = accent
        )
    }
}

@Composable
private fun CallToAction(
    node: SkillNode,
    status: SkillStatus,
    onStartTraining: () -> Unit
) {
    val trackable = node.schemaId != null

    when {
        status == SkillStatus.LOCKED -> {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = SurfaceHigh,
                    disabledContentColor = TextFaint
                )
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Master the requirements to unlock", modifier = Modifier.padding(vertical = 6.dp))
            }
        }

        trackable -> {
            Button(
                onClick = onStartTraining,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status == SkillStatus.MASTERED) AchievementGold else ElectricCyan,
                    contentColor = AbyssBlack
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (status == SkillStatus.MASTERED) "Train again" else "Start AI training",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

        else -> {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = SurfaceHigh,
                    disabledContentColor = TextMuted
                )
            ) {
                Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI tracking for this skill coming soon", modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

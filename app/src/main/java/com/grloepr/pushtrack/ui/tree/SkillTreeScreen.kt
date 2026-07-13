package com.grloepr.pushtrack.ui.tree

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grloepr.pushtrack.progression.CalisthenicsSkillGraph
import com.grloepr.pushtrack.progression.SkillBranch
import com.grloepr.pushtrack.progression.SkillNode
import com.grloepr.pushtrack.progression.SkillStatus
import com.grloepr.pushtrack.progression.SkillTreeState
import com.grloepr.pushtrack.ui.theme.AchievementGold
import com.grloepr.pushtrack.ui.theme.DeepSpace
import com.grloepr.pushtrack.ui.theme.ElectricCyan
import com.grloepr.pushtrack.ui.theme.GoldDeep
import com.grloepr.pushtrack.ui.theme.NeonViolet
import com.grloepr.pushtrack.ui.theme.OutlineSteel
import com.grloepr.pushtrack.ui.theme.SurfaceHigh
import com.grloepr.pushtrack.ui.theme.SurfaceRaised
import com.grloepr.pushtrack.ui.theme.TextBright
import com.grloepr.pushtrack.ui.theme.TextFaint
import com.grloepr.pushtrack.ui.theme.TextMuted

// ── Layout constants for the progression map grid ───────────────────────────
private val ColumnWidth = 106.dp
private val RowHeight = 156.dp
private val NodeSize = 70.dp
private val GridPaddingH = 20.dp
private val GridPaddingTop = 40.dp
private val GridPaddingBottom = 48.dp
private val LaneCount = SkillBranch.entries.size

/**
 * The gamified progression hub: a pannable Directed-Acyclic-Graph map of
 * calisthenics skills. Edges carry energy toward unlockable nodes; node visual
 * states — locked (muted padlock), available (pulsing ring), mastered (gold
 * glow) — tell the athlete where their journey stands at a glance.
 */
@Composable
fun SkillTreeScreen(
    treeState: SkillTreeState,
    onStartTraining: (SkillNode) -> Unit,
    modifier: Modifier = Modifier
) {
    var inspectedNode by remember { mutableStateOf<SkillNode?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
            .drawBehind {
                // Ambient nebula glows behind everything.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonViolet.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.width * 0.7f
                    ),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.85f, size.height * 0.15f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ElectricCyan.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.75f),
                        radius = size.width * 0.8f
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.1f, size.height * 0.75f)
                )
            }
            .statusBarsPadding()
    ) {
        TreeHeader(treeState)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            SkillGraphCanvas(
                treeState = treeState,
                onNodeClick = { inspectedNode = it }
            )
        }
    }

    inspectedNode?.let { node ->
        SkillDetailSheet(
            node = node,
            status = treeState.statusOf(node),
            masteredIds = treeState.mastered,
            onDismiss = { inspectedNode = null },
            onStartTraining = {
                inspectedNode = null
                onStartTraining(node)
            },
            onSkipUnlock = { treeState.unlock(node.id) }
        )
    }
}

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun TreeHeader(treeState: SkillTreeState) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OMNIPOSE",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    color = TextBright
                )
                Text(
                    text = "Calisthenics skill tree",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            LevelRing(level = treeState.level, progress = treeState.levelProgress)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { treeState.levelProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = ElectricCyan,
                    trackColor = SurfaceHigh,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${treeState.xpIntoLevel} / ${SkillTreeState.XP_PER_LEVEL} XP · " +
                        "${treeState.masteredCount} of ${treeState.totalSkills} skills mastered",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Legend()
        }
    }
}

@Composable
private fun LevelRing(level: Int, progress: Float) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val stroke = 4.dp.toPx()
            drawArc(
                color = SurfaceHigh,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(ElectricCyan, NeonViolet, ElectricCyan)),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "LV",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted
            )
            Text(
                text = "$level",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = TextBright
            )
        }
    }
}

@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        LegendRow(color = TextFaint, label = "Locked")
        LegendRow(color = ElectricCyan, label = "Ready")
        LegendRow(color = AchievementGold, label = "Mastered")
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

// ── The DAG map ──────────────────────────────────────────────────────────────

private fun nodeCenter(node: SkillNode): Pair<Dp, Dp> {
    val cx = GridPaddingH + ColumnWidth * node.branch.lane + ColumnWidth / 2
    val cy = GridPaddingTop + RowHeight * node.tier + NodeSize / 2
    return Pair(cx, cy)
}

@Composable
private fun SkillGraphCanvas(
    treeState: SkillTreeState,
    onNodeClick: (SkillNode) -> Unit
) {
    val contentWidth = GridPaddingH * 2 + ColumnWidth * LaneCount
    val contentHeight = GridPaddingTop + GridPaddingBottom +
        RowHeight * (CalisthenicsSkillGraph.maxTier + 1)

    val infinite = rememberInfiniteTransition(label = "edgeFlow")
    val dashPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "edgeDashPhase"
    )

    Box(
        modifier = Modifier
            .width(contentWidth)
            .height(contentHeight)
    ) {
        // Branch headers
        SkillBranch.entries.forEach { branch ->
            Text(
                text = branch.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = TextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = GridPaddingH + ColumnWidth * branch.lane, y = 8.dp)
                    .width(ColumnWidth)
            )
        }

        // Prerequisite edges
        val density = LocalDensity.current
        Canvas(modifier = Modifier.fillMaxSize()) {
            CalisthenicsSkillGraph.edges.forEach { (fromId, toId) ->
                val from = CalisthenicsSkillGraph.byId(fromId) ?: return@forEach
                val to = CalisthenicsSkillGraph.byId(toId) ?: return@forEach
                val toStatus = treeState.statusOf(to)

                val (fx, fy) = nodeCenter(from)
                val (tx, ty) = nodeCenter(to)
                with(density) {
                    val start = Offset(fx.toPx(), fy.toPx() + NodeSize.toPx() / 2f)
                    val end = Offset(tx.toPx(), ty.toPx() - NodeSize.toPx() / 2f)
                    val midY = (start.y + end.y) / 2f

                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(start.x, midY, end.x, midY, end.x, end.y)
                    }

                    when (toStatus) {
                        SkillStatus.MASTERED -> drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(AchievementGold.copy(alpha = 0.9f), GoldDeep),
                                startY = start.y,
                                endY = end.y
                            ),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        SkillStatus.AVAILABLE -> drawPath(
                            path = path,
                            color = ElectricCyan.copy(alpha = 0.85f),
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    intervals = floatArrayOf(14f, 10f),
                                    phase = -dashPhase
                                )
                            )
                        )

                        SkillStatus.LOCKED -> drawPath(
                            path = path,
                            color = OutlineSteel.copy(alpha = 0.55f),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                            )
                        )
                    }
                }
            }
        }

        // Nodes
        CalisthenicsSkillGraph.nodes.forEach { node ->
            val (cx, cy) = nodeCenter(node)
            SkillNodeItem(
                node = node,
                status = treeState.statusOf(node),
                onClick = { onNodeClick(node) },
                modifier = Modifier.offset(
                    x = cx - ColumnWidth / 2,
                    y = cy - NodeSize / 2
                )
            )
        }
    }
}

// ── Nodes ────────────────────────────────────────────────────────────────────

@Composable
private fun SkillNodeItem(
    node: SkillNode,
    status: SkillStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(ColumnWidth)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (status) {
                SkillStatus.AVAILABLE -> AvailableNodeAura()
                SkillStatus.MASTERED -> MasteredNodeAura()
                SkillStatus.LOCKED -> Unit
            }
            NodeDisc(node = node, status = status, onClick = onClick)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = node.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = when (status) {
                SkillStatus.LOCKED -> TextFaint
                SkillStatus.AVAILABLE -> TextBright
                SkillStatus.MASTERED -> AchievementGold
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
                .background(DeepSpace.copy(alpha = 0.72f), MaterialTheme.shapes.small)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Pulsing "ready to unlock" ring — the tree's call to action. */
@Composable
private fun AvailableNodeAura() {
    val infinite = rememberInfiniteTransition(label = "availableAura")
    val ring by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "availableRing"
    )
    Canvas(modifier = Modifier.size(NodeSize + 34.dp)) {
        val baseRadius = NodeSize.toPx() / 2f
        val expand = baseRadius * (1f + 0.42f * ring)
        drawCircle(
            color = ElectricCyan.copy(alpha = (1f - ring) * 0.5f),
            radius = expand,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = ElectricCyan.copy(alpha = 0.16f),
            radius = baseRadius + 4.dp.toPx()
        )
    }
}

/** Steady gold halo for mastered skills. */
@Composable
private fun MasteredNodeAura() {
    val infinite = rememberInfiniteTransition(label = "masteredAura")
    val breath by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "masteredBreath"
    )
    Canvas(modifier = Modifier.size(NodeSize + 34.dp)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    AchievementGold.copy(alpha = 0.4f * breath),
                    Color.Transparent
                )
            ),
            radius = size.minDimension / 2f
        )
    }
}

@Composable
private fun NodeDisc(
    node: SkillNode,
    status: SkillStatus,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "nodeBreath")
    val breathScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (status == SkillStatus.AVAILABLE) 1.045f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nodeBreathScale"
    )

    val background = when (status) {
        SkillStatus.LOCKED -> Brush.verticalGradient(
            listOf(SurfaceRaised.copy(alpha = 0.85f), DeepSpace)
        )
        SkillStatus.AVAILABLE -> Brush.verticalGradient(
            listOf(SurfaceHigh, SurfaceRaised)
        )
        SkillStatus.MASTERED -> Brush.linearGradient(
            listOf(AchievementGold, GoldDeep)
        )
    }
    val borderColor = when (status) {
        SkillStatus.LOCKED -> OutlineSteel.copy(alpha = 0.6f)
        SkillStatus.AVAILABLE -> ElectricCyan
        SkillStatus.MASTERED -> AchievementGold
    }
    val iconTint = when (status) {
        SkillStatus.LOCKED -> TextFaint
        SkillStatus.AVAILABLE -> TextBright
        SkillStatus.MASTERED -> DeepSpace
    }

    Box(
        modifier = Modifier
            .size(NodeSize)
            .scale(breathScale)
            .clip(CircleShape)
            .background(background, CircleShape)
            .border(width = 2.dp, color = borderColor, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = node.icon.asVector(),
            contentDescription = node.title,
            tint = iconTint,
            modifier = Modifier.size(30.dp)
        )

        // Status badge
        when (status) {
            SkillStatus.LOCKED -> NodeBadge(
                icon = Icons.Rounded.Lock,
                background = SurfaceHigh,
                tint = TextMuted
            )
            SkillStatus.MASTERED -> NodeBadge(
                icon = Icons.Rounded.Star,
                background = AchievementGold,
                tint = DeepSpace
            )
            SkillStatus.AVAILABLE -> Unit
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.NodeBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    tint: Color
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(22.dp)
            .background(background, CircleShape)
            .border(1.5.dp, DeepSpace, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp)
        )
    }
}

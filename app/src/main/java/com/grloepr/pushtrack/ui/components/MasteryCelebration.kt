package com.grloepr.pushtrack.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.grloepr.pushtrack.ui.theme.AchievementGold

/** A truthful practice milestone, with a direct path back to ongoing training. */
@Composable
fun MasteryCelebration(
    visible: Boolean,
    skillTitle: String,
    xpReward: Int,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    onKeepPracticing: () -> Unit = onContinue
) {
    if (!visible) return
    Dialog(onDismissRequest = onKeepPracticing) {
        Card(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null,
                    tint = AchievementGold, modifier = Modifier.size(48.dp))
                Text("Session target reached", style = MaterialTheme.typography.titleLarge)
                Text(skillTitle, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (xpReward > 0) "+$xpReward XP · New practice paths unlocked"
                    else "Your completed counts are kept. Continue at your own pace.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Motion counts are estimates, not a certification of technique.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = onKeepPracticing, modifier = Modifier.fillMaxWidth()) {
                    Text("Keep practicing")
                }
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to the tree")
                }
            }
        }
    }
}

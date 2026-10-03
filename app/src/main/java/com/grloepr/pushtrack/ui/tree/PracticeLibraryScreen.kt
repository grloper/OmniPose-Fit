package com.grloepr.pushtrack.ui.tree

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.grloepr.pushtrack.engine.ExerciseLibrary
import com.grloepr.pushtrack.practice.PracticeLibrary
import com.grloepr.pushtrack.practice.LibraryBrowseState
import com.grloepr.pushtrack.practice.rememberLibraryBrowseState
import com.grloepr.pushtrack.progression.*
import com.grloepr.pushtrack.ui.theme.TextMuted
import com.grloepr.pushtrack.ui.theme.TextBright

/** Browsing and manual practice require neither camera access nor progression unlocks. */
@Composable
fun PracticeLibraryScreen(onInspect: (SkillNode) -> Unit, modifier: Modifier = Modifier,
    browseState: LibraryBrowseState = rememberLibraryBrowseState(), listState: LazyListState = rememberLazyListState()) {
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("practice_library", Context.MODE_PRIVATE) }
    val known = remember { CalisthenicsSkillGraph.nodes.map { it.id }.toSet() }
    var favorites by remember { mutableStateOf(PracticeLibrary.readFavorites(prefs.all["favorites"], known)) }
    var plan by remember { mutableStateOf(PracticeLibrary.readPlan(prefs.all["plan"], known)) }
    var query by browseState.query
    var branchName by browseState.branchName
    var filter by browseState.filter
    var motionFilter by browseState.motionFilter
    val holds = remember { CalisthenicsSkillGraph.nodes.filter { node -> node.schemaId?.let { ExerciseLibrary.get(context, it)?.isHold } == true }.map { it.id }.toSet() }
    var manualId by rememberSaveable { mutableStateOf<String?>(null) }
    val branch = SkillBranch.entries.find { it.name == branchName }
    val nodes = PracticeLibrary.search(CalisthenicsSkillGraph.nodes, query, branch, when(filter) {
        "Favorites" -> favorites
        "My plan" -> plan.toSet()
        else -> null
    }).filter { motionFilter == "Any motion" || (it.id in holds) == (motionFilter == "Holds") }.let { matches -> if (filter == "My plan") matches.sortedBy { plan.indexOf(it.id) } else matches }
    LazyColumn(modifier.navigationBarsPadding().testTag("exercise-library-list"), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column {
        OutlinedTextField(query, { query = it.take(200) }, label = { Text("Search exercises") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            listOf("All", "Favorites", "My plan").forEach { label ->
                FilterChip(filter == label, { filter = label }, { Text(label) }, Modifier.padding(end = 6.dp))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            FilterChip(branch == null, { branchName = null }, { Text("All branches") })
            SkillBranch.entries.forEach { item ->
                FilterChip(branch == item, { branchName = item.name }, { Text(item.label) }, Modifier.padding(start = 6.dp))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            listOf("Any motion", "Repetitions", "Holds").forEach { label ->
                FilterChip(motionFilter == label, { motionFilter = label }, { Text(label) }, Modifier.padding(end = 6.dp))
            }
            TextButton({ query = ""; branchName = null; filter = "All"; motionFilter = "Any motion" }) { Text("Reset filters") }
        }
        Text("${nodes.size} exercises · Plans are stored locally; Android backup may include them. Manual practice does not award progression.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(16.dp))
        if (nodes.isEmpty()) Text("No matching exercises. Change your search or filters.", Modifier.padding(16.dp), color = TextBright)
        } }
            items(nodes, key = { it.id }) { node ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(node.title, style = MaterialTheme.typography.titleMedium)
                        Text("${node.branch.label} · Difficulty ${node.difficulty}/5", style = MaterialTheme.typography.bodySmall)
                        Text(node.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        Text(if(node.schemaId == node.id) "Experimental joint-position estimate; technique is not verified."
                            else "Camera uses a shared motion model; this variation is not independently verified.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        Column(Modifier.fillMaxWidth()) {
                            TextButton({
                                favorites = if(node.id in favorites) favorites - node.id else favorites + node.id
                                prefs.edit().putStringSet("favorites", favorites).apply()
                            }) { Text(if(node.id in favorites) "Unfavorite ${node.title}" else "Favorite ${node.title}") }
                            TextButton({
                                plan = if(node.id in plan) plan - node.id else plan + node.id
                                prefs.edit().putString("plan", plan.joinToString(",")).apply()
                            }) { Text(if(node.id in plan) "Remove from plan" else "Add to plan") }
                        }
                        Column {
                            TextButton({ onInspect(node) }, modifier = Modifier.testTag("exercise-detail-${node.id}")) { Text("Preview & tracking") }
                            Button({ manualId = node.id }, modifier = Modifier.testTag("manual-practice-${node.id}")) { Text("Manual practice") }
                        }
                    }
                }
            }
    }
    manualId?.let { id -> CalisthenicsSkillGraph.byId(id)?.let { node ->
        ManualPracticeDialog(node, onDismiss = { manualId = null })
    } }
}

@Composable
private fun ManualPracticeDialog(node: SkillNode, onDismiss: () -> Unit) {
    var target by rememberSaveable(node.id) { mutableStateOf("10") }
    var count by rememberSaveable(node.id) { mutableIntStateOf(0) }
    var seconds by rememberSaveable(node.id) { mutableStateOf(false) }
    val goal = target.toIntOrNull()?.takeIf { it in 1..300 }
    AlertDialog(modifier = Modifier.testTag("manual-practice-dialog"), onDismissRequest = onDismiss, title = { Text(node.title) }, text = {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Camera-free, self-reported practice. Choose a comfortable goal; stop if uncomfortable. This does not certify technique or award XP.")
            Column { FilterChip(!seconds, { seconds = false; count = 0 }, { Text("Repetitions") })
                Spacer(Modifier.height(8.dp))
                FilterChip(seconds, { seconds = true; count = 0 }, { Text("Seconds held") }) }
            OutlinedTextField(target, { target = it.take(3); count = 0 }, label = { Text("Personal goal (1–300)") }, singleLine = true,
                isError = goal == null)
            Text("$count / ${goal ?: "—"} ${if(seconds) "seconds reported" else "repetitions reported"}", style = MaterialTheme.typography.headlineSmall)
            if(goal != null && count >= goal) Text("Personal goal reported. No progression award.")
            Button({ count = (count + 1).coerceAtMost(300) }, enabled = goal != null && count < (goal ?: 0)) {
                Text(if(seconds) "Report 1 second held" else "Report 1 repetition")
            }
            TextButton({ count = 0 }) { Text("Reset reported count") }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("End practice") } })
}

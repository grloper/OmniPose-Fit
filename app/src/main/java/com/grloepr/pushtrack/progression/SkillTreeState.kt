package com.grloepr.pushtrack.progression

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Observable, persisted progression state. Mastery survives process death via
 * [SharedPreferences]; all reads are snapshot-state backed so Compose reacts
 * to unlocks instantly.
 */
@Stable
class SkillTreeState(private val prefs: SharedPreferences) {

    var mastered: Set<String> by mutableStateOf(
        prefs.getStringSet(KEY_MASTERED, emptySet()).orEmpty().toSet()
    )
        private set

    fun isMastered(id: String): Boolean = id in mastered

    fun statusOf(node: SkillNode): SkillStatus = CalisthenicsSkillGraph.statusOf(node, mastered)

    fun master(id: String) {
        if (id in mastered) return
        mastered = mastered + id
        prefs.edit().putStringSet(KEY_MASTERED, mastered).apply()
    }

    val masteredCount: Int get() = mastered.size

    val totalSkills: Int get() = CalisthenicsSkillGraph.nodes.size

    val totalXp: Int
        get() = CalisthenicsSkillGraph.nodes
            .filter { it.id in mastered }
            .sumOf { it.xpReward }

    val level: Int get() = totalXp / XP_PER_LEVEL + 1

    /** 0..1 progress through the current level. */
    val levelProgress: Float
        get() = (totalXp % XP_PER_LEVEL) / XP_PER_LEVEL.toFloat()

    val xpIntoLevel: Int get() = totalXp % XP_PER_LEVEL

    companion object {
        private const val KEY_MASTERED = "mastered_skills"
        private const val PREFS_NAME = "omnipose_progression"
        const val XP_PER_LEVEL = 300

        fun create(context: Context): SkillTreeState = SkillTreeState(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )
    }
}

@Composable
fun rememberSkillTreeState(): SkillTreeState {
    val context = LocalContext.current
    return remember { SkillTreeState.create(context) }
}

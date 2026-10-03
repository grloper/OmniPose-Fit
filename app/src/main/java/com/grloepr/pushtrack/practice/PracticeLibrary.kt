package com.grloepr.pushtrack.practice

import com.grloepr.pushtrack.progression.SkillNode
import com.grloepr.pushtrack.progression.SkillBranch

/** Only known catalog identifiers persist; stale or malformed entries never become exercises. */
object PracticeLibrary {
    fun validatedIds(ids: Collection<String>, knownIds: Set<String>): List<String> =
        ids.filter { it in knownIds }.distinct().take(knownIds.size)

    fun readFavorites(value: Any?, knownIds: Set<String>): Set<String> =
        validatedIds((value as? Set<*>)?.filterIsInstance<String>().orEmpty(), knownIds).toSet()

    fun readPlan(value: Any?, knownIds: Set<String>): List<String> =
        validatedIds((value as? String)?.take(4096)?.split(',').orEmpty(), knownIds)

    fun search(nodes: List<SkillNode>, query: String, branch: SkillBranch? = null,
               allowedIds: Set<String>? = null): List<SkillNode> {
        val terms = query.trim().take(200).lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return nodes.filter { node ->
            (branch == null || node.branch == branch) &&
                (allowedIds == null || node.id in allowedIds) &&
                terms.all { it in "${node.title} ${node.description} ${node.branch.label} ${node.targetMuscles.joinToString(" ") { it.name.replace('_', ' ') }}".lowercase() }
        }
    }
}

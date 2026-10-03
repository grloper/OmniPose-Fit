package com.grloepr.pushtrack.practice

import com.grloepr.pushtrack.progression.CalisthenicsSkillGraph
import com.grloepr.pushtrack.progression.SkillBranch
import org.junit.Assert.*
import org.junit.Test

class PracticeLibraryTest {
    private val nodes = CalisthenicsSkillGraph.nodes
    @Test fun unknownDuplicateAndStalePlanEntriesAreDiscardedInOrder() {
        assertEquals(listOf("pushup", "wall_pushup"), PracticeLibrary.validatedIds(
            listOf("unknown", "pushup", "pushup", "wall_pushup", ""), nodes.map { it.id }.toSet()))
    }
    @Test fun incorrectlyTypedRestoredPreferencesAreIgnored() {
        val known = nodes.map { it.id }.toSet()
        assertTrue(PracticeLibrary.readFavorites(42, known).isEmpty())
        assertTrue(PracticeLibrary.readPlan(setOf("pushup"), known).isEmpty())
        assertEquals(setOf("pushup"), PracticeLibrary.readFavorites(setOf("pushup", 42, "unknown"), known))
        assertEquals(listOf("pushup"), PracticeLibrary.readPlan("pushup,pushup,unknown", known))
    }
    @Test fun searchCombinesWordsBranchAndFavoritesWithoutUnlockRestrictions() {
        assertTrue(PracticeLibrary.search(nodes, "  PUSH   UP ", SkillBranch.PUSH).all { it.branch == SkillBranch.PUSH })
        assertEquals(listOf("pushup"), PracticeLibrary.search(nodes, "push", allowedIds = setOf("pushup")).map { it.id })
        assertTrue(PracticeLibrary.search(nodes, "", allowedIds = emptySet()).isEmpty())
        assertEquals(nodes, PracticeLibrary.search(nodes, ""))
        assertTrue(PracticeLibrary.search(nodes, "not-an-exercise").isEmpty())
    }
}

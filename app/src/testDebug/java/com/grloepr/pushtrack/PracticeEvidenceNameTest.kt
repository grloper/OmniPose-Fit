package com.grloepr.pushtrack

import com.grloepr.pushtrack.progression.CalisthenicsSkillGraph
import org.junit.Assert.*
import org.junit.Test

/** Pure filename-contract regression, including real IDs that broke runtime evidence. */
class PracticeEvidenceNameTest {
    @Test fun allRealCatalogIdsProduceDistinctSafeCaptureNames() {
        val names = CalisthenicsSkillGraph.nodes.map {
            exerciseEvidenceName("all-exercises-tracking", it.id, "empty-synthetic-camera")
        }
        assertEquals(19, names.size)
        assertEquals(19, names.toSet().size)
        assertTrue(names.all { it.matches(Regex("[a-z0-9-]+")) })
        assertTrue(names.contains("all-exercises-tracking-wall-pushup-empty-synthetic-camera"))
        assertEquals("detail-front-lever", exerciseEvidenceName("detail", "front_lever"))
    }
    @Test fun pathComponentsAndInvalidSuffixesCannotEscapeEvidenceDirectory() {
        for (invalid in listOf("../wall_pushup", "wall/pushup", "", "wall.pushup")) {
            try { exerciseEvidenceName("detail", invalid); fail("Accepted unsafe catalog id: $invalid") }
            catch (_: IllegalArgumentException) { }
        }
        try { exerciseEvidenceName("detail", "pushup", "../capture"); fail("Accepted unsafe suffix") }
        catch (_: IllegalArgumentException) { }
    }
}

package com.grloepr.pushtrack.practice

import org.junit.Assert.*
import org.junit.Test

class LibraryBrowseStateTest {
    @Test fun selectionsSurviveIndependentRestorationTogetherWithQuery() {
        val selected = LibraryBrowseState().apply {
            query.value = "Wall Push-Up"
            branchName.value = "PUSH"
            filter.value = "Favorites"
            motionFilter.value = "Repetitions"
        }
        val restored = LibraryBrowseState.restore(selected.savedValues())
        assertNotSame(selected, restored)
        assertEquals("Wall Push-Up", restored.query.value)
        assertEquals("PUSH", restored.branchName.value)
        assertEquals("Favorites", restored.filter.value)
        assertEquals("Repetitions", restored.motionFilter.value)
        selected.filter.value = "My plan"
        assertEquals("Favorites", restored.filter.value)
    }
    @Test fun malformedRestoredTokensCannotCreateInvalidFiltersOrOversizedQuery() {
        val restored = LibraryBrowseState.restore(listOf("x".repeat(500), "UNKNOWN", "invented", "other"))
        assertEquals(200, restored.query.value.length)
        assertNull(restored.branchName.value)
        assertEquals("All", restored.filter.value)
        assertEquals("Any motion", restored.motionFilter.value)
        assertEquals("", LibraryBrowseState.restore(emptyList()).query.value)
    }
}

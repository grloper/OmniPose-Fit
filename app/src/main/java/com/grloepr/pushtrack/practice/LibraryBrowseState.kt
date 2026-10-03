package com.grloepr.pushtrack.practice

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.grloepr.pushtrack.progression.SkillBranch

/** Owned by the library/map host so switching views cannot dispose the selected filters. */
@Stable
class LibraryBrowseState {
    val query = mutableStateOf("")
    val branchName = mutableStateOf<String?>(null)
    val filter = mutableStateOf("All")
    val motionFilter = mutableStateOf("Any motion")

    fun savedValues(): List<String> = listOf(query.value, branchName.value.orEmpty(), filter.value, motionFilter.value)

    companion object {
        fun restore(values: List<String>): LibraryBrowseState = LibraryBrowseState().apply {
            if (values.size == 4) {
                query.value = values[0].take(200)
                branchName.value = values[1].takeIf { name -> SkillBranch.entries.any { it.name == name } }
                filter.value = values[2].takeIf { it in setOf("All", "Favorites", "My plan") } ?: "All"
                motionFilter.value = values[3].takeIf { it in setOf("Any motion", "Repetitions", "Holds") } ?: "Any motion"
            }
        }
        val Saver = listSaver<LibraryBrowseState, String>(save = { it.savedValues() }, restore = { restore(it) })
    }
}

@Composable
fun rememberLibraryBrowseState(): LibraryBrowseState = rememberSaveable(saver = LibraryBrowseState.Saver) { LibraryBrowseState() }

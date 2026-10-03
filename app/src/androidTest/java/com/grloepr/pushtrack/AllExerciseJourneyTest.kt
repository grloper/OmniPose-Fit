package com.grloepr.pushtrack

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.grloepr.pushtrack.progression.CalisthenicsSkillGraph
import com.grloepr.pushtrack.progression.SkillNode
import com.grloepr.pushtrack.practice.MotionTrackingLimitations
import com.grloepr.pushtrack.ui.screen.AnalysisTimestampMs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.test.filters.SdkSuppress

/** All 19 actual app routes. Empty-camera runs do not demonstrate recognition accuracy. */
class AllExerciseJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private fun selectExercise(node: SkillNode) {
        compose.onNodeWithTag("exercise-library-list").performScrollToIndex(0)
        val search = compose.onNodeWithText("Search exercises")
        search.performScrollTo().performTextClearance()
        search.performTextInput(node.title)
        Espresso.closeSoftKeyboard()
    }
    private fun clickLibraryAction(tag: String) {
        compose.onNodeWithTag("exercise-library-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    private fun details(node: SkillNode) {
        clickLibraryAction("exercise-detail-${node.id}")
        val insideSheet = hasAnyAncestor(hasTestTag("exercise-detail-sheet"))
        val instructions = hasText(node.description) and insideSheet
        compose.onAllNodes(instructions).assertCountEquals(1)
        compose.onNode(instructions).performScrollTo().assertExists()
        val limitations = hasText(MotionTrackingLimitations.forExercise(node)) and insideSheet
        compose.onAllNodes(limitations).assertCountEquals(1)
        compose.onNode(limitations).performScrollTo().assertExists()
        val preview = hasText("Play illustrated preview") and insideSheet
        compose.onAllNodes(preview).assertCountEquals(1)
        compose.onNode(preview).performScrollTo().assertExists()
    }
    private fun awaitFreshAnalysis(after: Long = -1L) {
        compose.waitUntil(30000) {
            val config = compose.onAllNodesWithTag("camera-preview").fetchSemanticsNodes().firstOrNull()?.config
            config != null && config.contains(AnalysisTimestampMs) && config[AnalysisTimestampMs] > after &&
                SystemClock.elapsedRealtime() - config[AnalysisTimestampMs] < 10000L
        }
        compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
    }
    private fun analysisTimestamp(): Long = compose.onNodeWithTag("camera-preview")
        .fetchSemanticsNode().config[AnalysisTimestampMs]

    @SdkSuppress(minSdkVersion = 29)
    @Test fun allNineteenDetailsAndManualUnitsResetEndReopenWithoutCameraOrXp() = assertManualJourneyDoesNotUseCamera {
        val progressionBefore = context.getSharedPreferences("omnipose_progression", 0).all.toMap()
        assertEquals(19, CalisthenicsSkillGraph.nodes.size)
        compose.onNodeWithText("Search exercises").assertIsDisplayed()
        compose.waitForIdle()
        capturePracticeEvidence("all-exercises-library-production-theme")
        for (node in CalisthenicsSkillGraph.nodes) {
            selectExercise(node)
            details(node)
            if (node.id in setOf("wall_pushup", "handstand", "front_lever"))
                capturePracticeEvidence(exerciseEvidenceName("all-exercises-detail", node.id))
            Espresso.pressBack()
            for (seconds in listOf(false, true)) {
                clickLibraryAction("manual-practice-${node.id}")
                compose.onNodeWithText(if(seconds) "Seconds held" else "Repetitions").performScrollTo().performClick()
                val goal = compose.onNode(hasSetTextAction() and hasText("Personal goal", substring = true))
                goal.performScrollTo().performTextClearance()
                goal.performTextInput("2")
                Espresso.closeSoftKeyboard()
                val action = if(seconds) "Report 1 second held" else "Report 1 repetition"
                compose.onNodeWithText(action).performScrollTo().performClick()
                compose.onNodeWithText(action).performScrollTo().performClick()
                compose.onNodeWithText("Personal goal reported. No progression award.").performScrollTo().assertExists()
                compose.onNodeWithText("Reset reported count").performScrollTo().performClick()
                compose.onNodeWithText("0 / 2 ${if(seconds) "seconds reported" else "repetitions reported"}")
                    .performScrollTo().assertExists()
                if(node.id == "wall_pushup" && !seconds) capturePracticeEvidence("all-exercises-manual-production-theme")
                compose.onNodeWithTag("camera-preview").assertDoesNotExist()
                compose.onNodeWithText("End practice").performClick()
            }
            clickLibraryAction("manual-practice-${node.id}")
            compose.onNodeWithText("0 / 10 repetitions reported").performScrollTo().assertExists()
            compose.onNodeWithText("End practice").performClick()
            assertEquals("Manual route changed progression: ${node.id}", progressionBefore,
                context.getSharedPreferences("omnipose_progression", 0).all.toMap())
            println("ALL19 manual/instructions/preview PASS ${node.id}")
        }
    }

    @Test fun allNineteenEmptyCameraRoutesPauseResumeResetExitAndRepeat() {
        shell("pm grant ${context.packageName} android.permission.CAMERA")
        val masteredBefore = context.getSharedPreferences("omnipose_progression", 0)
            .getStringSet("mastered_skills", emptySet()).orEmpty().toSet()
        for (node in CalisthenicsSkillGraph.nodes) {
            repeat(2) { iteration ->
                selectExercise(node)
                clickLibraryAction("exercise-detail-${node.id}")
                if(compose.onAllNodesWithText("Open practice anyway").fetchSemanticsNodes().isNotEmpty())
                    compose.onNodeWithText("Open practice anyway").performClick()
                val start = if(compose.onAllNodesWithText("Train again").fetchSemanticsNodes().isNotEmpty()) "Train again" else "Start motion tracking"
                compose.onNodeWithText(start).performClick()
                awaitFreshAnalysis()
                compose.onNodeWithContentDescription("Pause tracking").performClick()
                compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
                val pausedAt = analysisTimestamp()
                compose.onNodeWithContentDescription("Reset reps").performClick()
                compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
                compose.onNodeWithContentDescription("Resume tracking").performClick()
                awaitFreshAnalysis(pausedAt)
                if (node.id in setOf("wall_pushup", "handstand", "front_lever") && iteration == 0)
                    capturePracticeEvidence(exerciseEvidenceName("all-exercises-tracking", node.id, "empty-synthetic-camera"))
                compose.onNodeWithContentDescription("End session").performClick()
                compose.onNodeWithTag("exercise-library-list").assertExists()
                println("ALL19 camera route PASS ${node.id} iteration=$iteration (no valid pose / no accuracy claim)")
            }
        }
        assertEquals(masteredBefore, context.getSharedPreferences("omnipose_progression", 0)
            .getStringSet("mastered_skills", emptySet()).orEmpty().toSet())
    }
}


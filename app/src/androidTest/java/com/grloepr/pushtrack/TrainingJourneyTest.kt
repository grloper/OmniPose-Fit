package com.grloepr.pushtrack

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Run only on the CI emulator with synthetic emulated cameras; no personal video. */
class TrainingJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun grantSyntheticCamera() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${InstrumentationRegistry.getInstrumentation().targetContext.packageName} android.permission.CAMERA").close()
    }

    @Test fun skillTrainingResetSwitchAndExit() {
        compose.onNodeWithText("Map view").performClick()
        compose.onNodeWithContentDescription("Wall Push-Up").performClick()
        compose.waitForIdle()
        captureRuntimeEvidence("exercise-detail")
        compose.onNodeWithText("Start motion tracking").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("rep-status").fetchSemanticsNodes().isNotEmpty()
        }
        // Zero counts alone could pass before CameraX or ML Kit runs at all.
        compose.waitUntil(30000) {
            compose.onAllNodes(
                hasTestTag("camera-preview") and SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                    "Pose analysis active"
                )
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Camera unavailable. Try switching camera or end this session.")
            .assertDoesNotExist()
        compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
        compose.waitForIdle()
        captureRuntimeEvidence("training-controls-empty-synthetic-camera")
        compose.onNodeWithContentDescription("Pause tracking").performClick()
        compose.onNodeWithText("Paused — completed counts are kept. Resume from the start posture.").assertExists()
        compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
        compose.waitForIdle()
        captureRuntimeEvidence("training-paused")
        compose.onNodeWithContentDescription("Resume tracking").performClick()
        val mute = compose.onAllNodesWithContentDescription("Mute training audio").fetchSemanticsNodes()
        if (mute.isNotEmpty()) compose.onNodeWithContentDescription("Mute training audio").performClick()
        compose.onNodeWithContentDescription("Enable training audio").assertExists()
        compose.onNodeWithContentDescription("Reset reps").performClick()
        compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
        compose.onNodeWithContentDescription("Switch camera").performClick()
        compose.waitUntil(30000) {
            compose.onAllNodes(
                hasTestTag("camera-preview") and SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                    "Pose analysis active"
                )
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Camera unavailable. Try switching camera or end this session.")
            .assertDoesNotExist()
        compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
        compose.onNodeWithContentDescription("End session").performClick()
        compose.onNodeWithText("Map view").performClick()
        compose.onNodeWithContentDescription("Wall Push-Up").assertExists()
        compose.onNodeWithContentDescription("Wall Push-Up").performClick()
        compose.onNodeWithText("Start motion tracking").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("rep-status").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Enable training audio").assertExists()
        compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
        compose.onNodeWithContentDescription("End session").performClick()
    }
}

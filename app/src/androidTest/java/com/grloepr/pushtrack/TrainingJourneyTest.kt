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
            .executeShellCommand("pm grant com.grloepr.pushtrack android.permission.CAMERA").close()
    }

    @Test fun skillTrainingResetSwitchAndExit() {
        compose.onNodeWithContentDescription("Wall Push-Up").performClick()
        compose.onNodeWithText("Start motion tracking").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("rep-status").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
        compose.onNodeWithContentDescription("Reset reps").performClick()
        compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
        compose.onNodeWithContentDescription("Switch camera").performClick()
        compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
        compose.onNodeWithContentDescription("End session").performClick()
        compose.onNodeWithContentDescription("Wall Push-Up").assertExists()
    }
}

package com.grloepr.pushtrack

import com.grloepr.pushtrack.ui.theme.PushTrackTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.grloepr.pushtrack.ui.tree.PracticeLibraryScreen
import org.junit.Rule
import org.junit.Test

/** Component accessibility regression; actual MainActivity routing is tested separately. */
class PracticeLibraryAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun malformedRestoredPreferencesAndDoubleFontScaleKeepManualControlsReachable() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("practice_library", 0)
        prefs.edit().putInt("favorites", 42).putBoolean("plan", true).commit()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                PushTrackTheme { PracticeLibraryScreen(onInspect = {}) }
            }
        }
        capturePracticeEvidence("library-font2")
        compose.onNodeWithText("Search exercises").performTextInput("Wall Push-Up")
        compose.onNodeWithText("Favorite Wall Push-Up").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add to plan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Manual practice").performScrollTo().performClick()
        val goal = compose.onNode(manualGoalMatcher())
        goal.performScrollTo().performClick()
        compose.waitUntil(10000) {
            var visible = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull()
                visible = activity?.window?.decorView?.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
            }
            visible
        }
        capturePracticeEvidence("manual-font2-keyboard")
        compose.manualControl("Report 1 repetition").performScrollTo().performClick()
        compose.manualControl("Reset reported count").performScrollTo().performClick()
        compose.manualControl("0 / 10 repetitions reported").performScrollTo().assertIsDisplayed()
        compose.manualControl("Reset reported count").performScrollTo().assertIsDisplayed()
        // End is a fixed dialog confirm button, outside the scrollable text content.
        compose.manualControl("End practice").assertIsDisplayed()
        compose.waitForIdle()
        capturePracticeEvidence("manual-font2-controls")
        compose.manualControl("End practice").performClick()
        compose.onNodeWithTag("manual-practice-dialog").assertDoesNotExist()
        prefs.edit().remove("favorites").remove("plan").commit()
    }
}

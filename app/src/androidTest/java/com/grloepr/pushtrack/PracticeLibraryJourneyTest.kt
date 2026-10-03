package com.grloepr.pushtrack

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.grloepr.pushtrack.ui.tree.PracticeLibraryScreen
import org.junit.Rule
import org.junit.Test

class PracticeLibraryJourneyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchFavoritePlanAndManualPracticeNeverRequestsCameraOrAwardsProgression() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("practice_library", 0).edit().clear().commit()
        compose.setContent { MaterialTheme { PracticeLibraryScreen(onInspect = {}) } }
        compose.onNodeWithText("Search exercises").performTextInput("Wall Push-Up")
        compose.onNodeWithText("Favorite Wall Push-Up").performScrollTo().performClick()
        compose.onNodeWithText("Add to plan").performScrollTo().performClick()
        compose.onNodeWithText("Manual practice").performScrollTo().performClick()
        compose.onNodeWithText("Report 1 repetition").performClick()
        compose.onNodeWithText("1 / 10 repetitions reported").assertIsDisplayed()
        compose.onNodeWithText("Reset reported count").performClick()
        compose.onNodeWithText("0 / 10 repetitions reported").assertIsDisplayed()
        compose.onNodeWithText("End practice").performClick()
        compose.onNodeWithText("My plan").performClick()
        compose.onNodeWithText("Remove from plan").performScrollTo().assertExists()
        org.junit.Assert.assertEquals("wall_pushup", context.getSharedPreferences("practice_library", 0).getString("plan", ""))
    }
}

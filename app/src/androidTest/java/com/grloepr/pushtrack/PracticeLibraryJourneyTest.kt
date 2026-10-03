package com.grloepr.pushtrack

import androidx.compose.ui.test.*
import android.os.SystemClock
import android.view.WindowInsets
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.test.filters.SdkSuppress

class PracticeLibraryJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @SdkSuppress(minSdkVersion = 30)
    private fun awaitNativeKeyboardAndStableBounds(matcher: SemanticsMatcher) {
        var lastBounds: Rect? = null
        var stableSince = SystemClock.elapsedRealtime()
        compose.waitUntil(10000) {
            var layoutReady = false
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                layoutReady = !view.isLayoutRequested && view.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false
            }
            val bounds = compose.onNode(matcher).fetchSemanticsNode().boundsInRoot
            val now = SystemClock.elapsedRealtime()
            if (bounds != lastBounds || !layoutReady) {
                lastBounds = bounds
                stableSince = now
                false
            } else now - stableSince >= 200L
        }
        println("Stable pointer target: ${compose.onNode(matcher).fetchSemanticsNode().boundsInRoot}")
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun actualRouteManualPracticeSurvivesRecreationWithoutCameraUseOrProgressionAward() = assertManualJourneyDoesNotUseCamera {
        compose.onNodeWithText("Search exercises").assertIsDisplayed()
        compose.waitForIdle()
        capturePracticeEvidence("library-normal")
        val progressBefore = context.getSharedPreferences("omnipose_progression", 0).all.toMap()
        compose.onNodeWithText("Search exercises").performTextInput("Wall Push-Up")
        compose.onNodeWithText("Manual practice").performScrollTo().performClick()
        compose.manualControl("Report 1 repetition").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        capturePracticeEvidence("manual-normal")
        compose.manualControl("Seconds held").performScrollTo().performClick()
        compose.onNode(manualGoalMatcher()).performScrollTo().performTextClearance()
        compose.onNode(manualGoalMatcher()).performTextInput("2")
        compose.manualControl("Report 1 second held").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.manualControl("1 / 2 seconds reported").performScrollTo().assertIsDisplayed()
        compose.manualControl("Report 1 second held").performScrollTo().performClick()
        compose.manualControl("Personal goal reported. No progression award.").performScrollTo().assertIsDisplayed()
        compose.manualControl("End practice").performClick()
        compose.onNodeWithText("Manual practice").performScrollTo().performClick()
        compose.manualControl("0 / 10 repetitions reported").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("camera-preview").assertDoesNotExist()
        compose.manualControl("End practice").performClick()
        assertEquals(progressBefore, context.getSharedPreferences("omnipose_progression", 0).all.toMap())
    }


    @Test fun illustratedExerciseDetailEvidence() {
        compose.onNodeWithText("Search exercises").performTextInput("Wall Push-Up")
        compose.onNodeWithText("Preview & tracking").performScrollTo().performClick()
        compose.onNodeWithText("Play illustrated preview").assertExists()
        capturePracticeEvidence("exercise-detail-normal")
    }

    @SdkSuppress(minSdkVersion = 30)
    @Test fun libraryQueryAndFavoriteFilterSurviveMapAndActivityRecreation() {
        compose.onNodeWithTag("exercise-library-list").performScrollToIndex(0)
        compose.onNodeWithText("All").assertIsSelected()
        compose.onNodeWithText("Search exercises").performTextClearance()
        compose.onNodeWithText("Search exercises").performTextInput("Wall Push-Up")
        compose.onNodeWithText("Search exercises").performImeAction()
        compose.onNodeWithText("Search exercises").assertIsNotFocused()
        val favoriteChoice = hasText("Favorite Wall Push-Up") or hasText("Unfavorite Wall Push-Up")
        compose.onNodeWithTag("exercise-library-list").performScrollToNode(favoriteChoice)
        compose.onAllNodes(favoriteChoice).assertCountEquals(1)
        awaitNativeKeyboardAndStableBounds(favoriteChoice)
        capturePracticeEvidence("favorites-setup-before")
        if (compose.onAllNodesWithText("Favorite Wall Push-Up").fetchSemanticsNodes().isNotEmpty()) {
            awaitNativeKeyboardAndStableBounds(favoriteChoice)
            compose.onNodeWithText("Favorite Wall Push-Up").performClick()
        }
        compose.onNodeWithText("Unfavorite Wall Push-Up").assertExists()
        capturePracticeEvidence("favorites-setup-after")
        compose.onNodeWithTag("exercise-library-list").performScrollToIndex(0)
        compose.onNodeWithText("Favorites").performScrollTo().assertIsDisplayed()
        awaitNativeKeyboardAndStableBounds(hasText("Favorites"))
        println("Favorites before pointer click:\n${compose.onNodeWithText("Favorites").printToString()}")
        capturePracticeEvidence("favorites-filter-before")
        awaitNativeKeyboardAndStableBounds(hasText("Favorites"))
        compose.onNodeWithText("Favorites").performClick()
        compose.waitForIdle()
        capturePracticeEvidence("favorites-filter-after")
        println("Favorites after pointer click:\n${compose.onNodeWithText("Favorites").printToString()}")
        compose.onNodeWithText("Favorites").assertIsSelected()
        compose.onNodeWithText("Push").performScrollTo().performClick()
        compose.onNodeWithText("Repetitions").performScrollTo().performClick()
        compose.onNodeWithText("Push").assertIsSelected()
        compose.onNodeWithText("Repetitions").assertIsSelected()
        compose.waitForIdle()
        compose.onNodeWithText("Map view").performClick()
        compose.onNodeWithText("Exercise library").performClick()
        compose.onNodeWithText("Search exercises").performScrollTo().assertTextContains("Wall Push-Up")
        compose.onNodeWithText("Favorites").assertIsSelected()
        compose.onNodeWithText("Push").assertIsSelected()
        compose.onNodeWithText("Repetitions").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Search exercises").performScrollTo().assertTextContains("Wall Push-Up")
        compose.onNodeWithText("Favorites").assertIsSelected()
        compose.onNodeWithText("Push").assertIsSelected()
        compose.onNodeWithText("Repetitions").assertIsSelected()
        compose.onNodeWithText("Unfavorite Wall Push-Up").performScrollTo().assertExists()
    }
}


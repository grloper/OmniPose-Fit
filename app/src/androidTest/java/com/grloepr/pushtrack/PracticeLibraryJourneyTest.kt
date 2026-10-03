package com.grloepr.pushtrack

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PracticeLibraryJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun cameraAppOp(): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("appops get ${context.packageName} CAMERA")
    ).bufferedReader().use { it.readText() }

    @Test fun actualRouteManualPracticeSurvivesRecreationWithoutCameraUseOrProgressionAward() {
        capturePracticeEvidence("library-normal")
        val permissionBefore = context.checkSelfPermission(Manifest.permission.CAMERA)
        val appOpBefore = cameraAppOp()
        val progressBefore = context.getSharedPreferences("omnipose_progression", 0).all.toMap()
        compose.onNodeWithText("Search exercises").performTextInput("Wall Push-Up")
        compose.onNodeWithText("Manual practice").performScrollTo().performClick()
        capturePracticeEvidence("manual-normal")
        compose.onNodeWithText("Seconds held").performScrollTo().performClick()
        compose.onNode(hasText("Personal goal", substring = true) and hasSetTextAction()).performScrollTo().performTextClearance()
        compose.onNode(hasText("Personal goal", substring = true) and hasSetTextAction()).performTextInput("2")
        compose.onNodeWithText("Report 1 second held").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("1 / 2 seconds reported").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Report 1 second held").performScrollTo().performClick()
        compose.onNodeWithText("Personal goal reported. No progression award.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("End practice").performClick()
        compose.onNodeWithText("Manual practice").performScrollTo().performClick()
        compose.onNodeWithText("0 / 10 repetitions reported").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("camera-preview").assertDoesNotExist()
        compose.onNodeWithText("End practice").performClick()
        assertEquals(permissionBefore, context.checkSelfPermission(Manifest.permission.CAMERA))
        assertEquals(appOpBefore, cameraAppOp())
        assertEquals(progressBefore, context.getSharedPreferences("omnipose_progression", 0).all.toMap())
    }


    @Test fun illustratedExerciseDetailEvidence() {
        compose.onNodeWithText("Search exercises").performTextInput("Wall Push-Up")
        compose.onNodeWithText("Preview & tracking").performScrollTo().performClick()
        compose.onNodeWithText("Play illustrated preview").assertExists()
        capturePracticeEvidence("exercise-detail-normal")
    }

    @Test fun libraryQueryAndFavoriteFilterSurviveMapAndActivityRecreation() {
        compose.onNodeWithText("Search exercises").performTextInput("Wall Push-Up")
        val favorite = compose.onAllNodesWithText("Favorite Wall Push-Up").fetchSemanticsNodes()
        if(favorite.isNotEmpty()) compose.onNodeWithText("Favorite Wall Push-Up").performScrollTo().performClick()
        compose.onNodeWithText("Favorites").performScrollTo().performClick()
        compose.onNodeWithText("Map view").performClick()
        compose.onNodeWithText("Exercise library").performClick()
        compose.onNodeWithText("Search exercises").performScrollTo().assertTextContains("Wall Push-Up")
        compose.onNodeWithText("Favorites").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Search exercises").performScrollTo().assertTextContains("Wall Push-Up")
        compose.onNodeWithText("Favorites").assertIsSelected()
        compose.onNodeWithText("Unfavorite Wall Push-Up").performScrollTo().assertExists()
    }
}

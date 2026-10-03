package com.grloepr.pushtrack

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.grloepr.pushtrack.ui.screen.AnalysisTimestampMs
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Run on an isolated synthetic empty-camera emulator, never a personal camera. */
class TrainingEnduranceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun grantSyntheticCamera() {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant com.grloepr.pushtrack android.permission.CAMERA")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private var lastAnalysisTimestamp = -1L

    private fun latestAnalysisTimestamp(): Long {
        val config = compose.onAllNodesWithTag("camera-preview").fetchSemanticsNodes()
            .firstOrNull()?.config ?: return -1L
        return if (config.contains(AnalysisTimestampMs)) config[AnalysisTimestampMs] else -1L
    }

    private fun awaitCamera() {
        val previousTimestamp = lastAnalysisTimestamp
        compose.waitUntil(30000) {
            compose.onAllNodes(hasTestTag("camera-preview") and SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, "Pose analysis active"
            )).fetchSemanticsNodes().isNotEmpty() &&
                latestAnalysisTimestamp() > previousTimestamp &&
                SystemClock.elapsedRealtime() - latestAnalysisTimestamp() < 10000L
        }
        lastAnalysisTimestamp = latestAnalysisTimestamp()
        println("OmniPose fresh analysis timestamp: $lastAnalysisTimestamp")
        compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
        compose.onNodeWithText("Camera unavailable. Try switching camera or end this session.")
            .assertDoesNotExist()
    }

    private fun enterTraining() {
        compose.onNodeWithContentDescription("Wall Push-Up").performClick()
        compose.onNodeWithText("Start motion tracking").performClick()
        awaitCamera()
    }

    @Test fun thirtyRepeatedEmptyCameraSessions() {
        repeat(30) {
            enterTraining()
            compose.onNodeWithContentDescription("Pause tracking").performClick()
            compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
            compose.onNodeWithContentDescription("Reset reps").performClick()
            compose.onNodeWithTag("rep-status").assertTextEquals("Completed: 0")
            compose.onNodeWithContentDescription("Resume tracking").performClick()
            awaitCamera()
            compose.onNodeWithContentDescription("End session").performClick()
            compose.onNodeWithContentDescription("Wall Push-Up").assertExists()
        }
    }

    @Test fun recreationRetainsRouteAndExplainsResetCounts() {
        enterTraining()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("New session after restart - previous session counts were reset.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("New session after restart - previous session counts were reset.").assertExists()
        awaitCamera()
        compose.onNodeWithContentDescription("End session").performClick()
    }

    /** Opt in with -e soakMinutes 30. Default CI skips this long-running method. */
    @Test fun optionalEmptyCameraEndurance() {
        val minutes = InstrumentationRegistry.getArguments().getString("soakMinutes")
            ?.toIntOrNull()?.coerceIn(0, 30) ?: 0
        assumeTrue("Long endurance is explicitly opt-in", minutes > 0)
        enterTraining()
        val until = SystemClock.elapsedRealtime() + minutes * 60000L
        var nextSample = SystemClock.elapsedRealtime() + 60000L
        var sample = 0
        while (SystemClock.elapsedRealtime() < until) {
            Thread.sleep(1000)
            if (SystemClock.elapsedRealtime() >= nextSample) {
                awaitCamera() // Fails if the detector froze despite its cached active label.
                compose.onNodeWithText("Session target reached").assertDoesNotExist()
                sample += 1
                println("OmniPose endurance minute=$sample freshFrameMs=$lastAnalysisTimestamp")
                nextSample = SystemClock.elapsedRealtime() + 60000L
            }
        }
        awaitCamera()
        compose.onNodeWithContentDescription("End session").performClick()
    }
}

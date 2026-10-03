package com.grloepr.pushtrack

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.grloepr.pushtrack.audio.*
import com.grloepr.pushtrack.engine.EngineFrame
import com.grloepr.pushtrack.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic engine events exercise the actual counter/dialog, without claiming pose accuracy. */
class SessionPresentationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nonzeroPausedSessionRepeatAndGenericHoldUseProductionPresentation() {
        val controller = SessionOutcomeController(2, false, true, false)
        var frame by mutableStateOf(EngineFrame.idle(0))
        var outcome by mutableStateOf<SessionOutcome?>(null)
        var visible by mutableStateOf(false)
        var holdTarget by mutableStateOf<Long?>(null)
        var awards = 0
        val cues = mutableListOf<CompletionCue>()
        controller.accept(frame, true, true)
        compose.setContent {
            Column {
                SessionCounter(frame, 2, holdTarget)
                MasteryCelebration(visible, "Practice", if (outcome?.awardProgress == true) 10 else 0,
                    onContinue = { visible = false }, onKeepPracticing = { visible = false })
            }
        }
        fun emit(count: Int, time: Long, active: Boolean = true) = compose.runOnIdle {
            frame = EngineFrame.idle(time).copy(repCount = count, repDelta = 1)
            outcome = controller.accept(frame, active, true)
            outcome?.let {
                if (it.awardProgress) awards++
                if (it.feedback.audible) cues += it.feedback.cue
                if (it.feedback.cue == CompletionCue.TARGET) visible = true
            }
        }
        emit(1, 100)
        compose.waitForIdle()
        captureRuntimeEvidence("synthetic-event-counter-one")
        compose.onNodeWithTag("session-counter").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "1 completed of 2"))
        compose.runOnIdle { assertNull(controller.accept(frame.copy(timestampMs = 200, repDelta = 0), false, true)) }
        compose.onNodeWithTag("session-counter").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "1 completed of 2"))
        emit(2, 300)
        compose.onNodeWithText("Session target reached").assertIsDisplayed()
        compose.waitForIdle()
        captureRuntimeEvidence("synthetic-event-target-dialog")
        compose.onNodeWithText("Keep practicing").performClick()
        compose.waitForIdle()
        captureRuntimeEvidence("synthetic-event-after-target")
        compose.runOnIdle { controller.reset(); frame = EngineFrame.idle(400); controller.accept(frame, true, true) }
        emit(1, 500); emit(2, 600)
        compose.onNodeWithText("Session target reached").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, awards); assertEquals(listOf(CompletionCue.REP, CompletionCue.TARGET, CompletionCue.REP, CompletionCue.TARGET), cues) }
        compose.onNodeWithText("Keep practicing").performClick()
        val generic = SessionOutcomeController(1, true, false, false)
        compose.runOnIdle { generic.accept(EngineFrame.idle(700), true, true)
            holdTarget = 2000L
            frame = EngineFrame.idle(800).copy(repCount = 1, repDelta = 1, holdMs = 2000)
            outcome = generic.accept(frame, true, true); visible = true
            assertFalse(requireNotNull(outcome).awardProgress)
        }
        compose.onNodeWithText("Session target reached").assertIsDisplayed()
        compose.onNodeWithText("Your completed counts are kept. Continue at your own pace.").assertIsDisplayed()
    }
}

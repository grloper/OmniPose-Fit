package com.grloepr.pushtrack.audio

import com.grloepr.pushtrack.engine.EngineFrame
import org.junit.Assert.*
import org.junit.Test

class SessionOutcomeControllerTest {
    private fun rep(n: Int, t: Long) = EngineFrame.idle(t).copy(repCount = n, repDelta = 1)
    @Test fun nonzeroPauseResumeTargetAndRepeatAwardOnce() {
        val controller = SessionOutcomeController(2, false, true, false)
        controller.accept(EngineFrame.idle(0), true, true)
        assertEquals(CompletionCue.REP, controller.accept(rep(1, 100), true, true)?.feedback?.cue)
        assertNull(controller.accept(EngineFrame.idle(200).copy(repCount = 1), false, true))
        val target = requireNotNull(controller.accept(rep(2, 300), true, true))
        assertTrue(target.awardProgress)
        assertEquals(CompletionCue.TARGET, target.feedback.cue)
        controller.reset()
        controller.accept(EngineFrame.idle(400), true, true)
        controller.accept(rep(1, 500), true, true)
        val repeat = requireNotNull(controller.accept(rep(2, 600), true, false))
        assertFalse(repeat.awardProgress)
        assertFalse(repeat.feedback.audible)
        assertEquals(CompletionCue.TARGET, repeat.feedback.cue)
    }
    @Test fun holdsAndGenericVariantsHaveHonestTargetsWithoutProgression() {
        val controller = SessionOutcomeController(2, true, false, false)
        controller.accept(EngineFrame.idle(0), true, true)
        assertEquals(CompletionCue.HOLD, controller.accept(rep(1, 100), true, true)?.feedback?.cue)
        val target = requireNotNull(controller.accept(rep(2, 200), true, true))
        assertEquals(CompletionCue.TARGET, target.feedback.cue)
        assertFalse(target.awardProgress)
    }
}

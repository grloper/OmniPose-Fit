package com.grloepr.pushtrack.audio

import com.grloepr.pushtrack.engine.EngineFrame
import org.junit.Assert.*
import org.junit.Test

class SessionFeedbackTest {
    private fun completed(count: Int, time: Long) = EngineFrame.idle(time).copy(repCount = count, repDelta = 1)

    @Test fun `restored count and replayed completion are silent`() {
        val feedback = SessionFeedback(3, false)
        assertNull(feedback.accept(completed(2, 100), true, true))
        assertEquals(CompletionCue.TARGET, feedback.accept(completed(3, 200), true, true)?.cue)
        assertNull(feedback.accept(completed(3, 200), true, true))
        assertNull(feedback.accept(completed(3, 201), true, true))
    }

    @Test fun `mute consumes events without replaying after unmute`() {
        val feedback = SessionFeedback(5, false)
        feedback.accept(EngineFrame.idle(), true, true)
        assertEquals(false, feedback.accept(completed(1, 100), true, false)?.audible)
        assertNull(feedback.accept(completed(1, 101), true, true))
        assertEquals(true, feedback.accept(completed(2, 200), true, true)?.audible)
    }

    @Test fun `background events never become delayed success`() {
        val feedback = SessionFeedback(2, true)
        feedback.accept(EngineFrame.idle(), true, true)
        assertNull(feedback.accept(completed(1, 100), false, true))
        assertNull(feedback.accept(completed(1, 101), true, true))
        assertEquals(CompletionCue.TARGET, feedback.accept(completed(2, 200), true, true)?.cue)
    }

    @Test fun `reset allows another complete session but not a false count jump`() {
        val feedback = SessionFeedback(1, true)
        feedback.accept(EngineFrame.idle(), true, true)
        assertEquals(CompletionCue.TARGET, feedback.accept(completed(1, 100), true, true)?.cue)
        assertEquals(CompletionCue.HOLD, feedback.accept(completed(2, 200), true, true)?.cue)
        feedback.reset()
        feedback.accept(EngineFrame.idle(), true, true)
        assertEquals(CompletionCue.TARGET, feedback.accept(completed(1, 300), true, true)?.cue)
        assertNull(feedback.accept(completed(3, 400), true, true))
    }

    @Test fun `partial and visibility frames cannot fabricate success`() {
        val feedback = SessionFeedback(2, false)
        feedback.accept(EngineFrame.idle(), true, true)
        assertNull(feedback.accept(EngineFrame.idle(100).copy(partialReps = 1, partialRepDelta = 1), true, true))
        assertNull(feedback.accept(EngineFrame.idle(200).copy(repCount = 1, repDelta = 0), true, true))
    }
    @Test fun `rapid tempo pulses never crowd completion and target replaces previous cue`() {
        val timing = CuePlaybackWindow()
        assertTrue(timing.eligible(CompletionCue.REP, 1000))
        timing.played(1000)
        assertFalse(timing.eligible(CompletionCue.TEMPO, 1100))
        assertFalse(timing.eligible(CompletionCue.REP, 1200))
        assertTrue(timing.eligible(CompletionCue.TARGET, 1200))
        timing.played(1200)
        assertFalse(timing.eligible(CompletionCue.TEMPO, 1499))
        assertTrue(timing.eligible(CompletionCue.TEMPO, 1500))
        assertFalse(timing.eligible(CompletionCue.REP, 1199))
    }

}

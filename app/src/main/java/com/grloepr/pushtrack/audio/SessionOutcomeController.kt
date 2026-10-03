package com.grloepr.pushtrack.audio

import com.grloepr.pushtrack.engine.EngineFrame

data class SessionOutcome(val feedback: CompletionFeedback, val awardProgress: Boolean)

/** Session target and persisted progression are separate; generic variants never earn progression. */
class SessionOutcomeController(target: Int, hold: Boolean, private val exactSchema: Boolean,
                               alreadyAwarded: Boolean) {
    private val feedback = SessionFeedback(target, hold)
    private var awarded = alreadyAwarded
    fun reset() = feedback.reset()
    fun accept(frame: EngineFrame, active: Boolean, soundOn: Boolean): SessionOutcome? {
        val event = feedback.accept(frame, active, soundOn) ?: return null
        val award = event.cue == CompletionCue.TARGET && exactSchema && !awarded
        if (award) awarded = true
        return SessionOutcome(event, award)
    }
}

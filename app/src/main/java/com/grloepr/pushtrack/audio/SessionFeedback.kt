package com.grloepr.pushtrack.audio

import com.grloepr.pushtrack.engine.EngineFrame

enum class CompletionCue { REP, HOLD, TARGET, TEMPO }
data class CompletionFeedback(val cue: CompletionCue, val audible: Boolean)

/** Consumes live engine events once, including muted/inactive events, so they cannot replay. */
class SessionFeedback(private val target: Int, private val isHold: Boolean) {
    private var count = 0
    private var timestamp = -1L
    private var initialized = false
    private var targetFired = false

    fun reset() { count = 0; timestamp = -1L; initialized = true; targetFired = false }

    fun accept(frame: EngineFrame, active: Boolean, soundOn: Boolean): CompletionFeedback? {
        if (!initialized) {
            initialized = true
            count = frame.repCount
            timestamp = frame.timestampMs
            targetFired = count >= target
            return null // Initial/restored counts are a baseline, not new achievements.
        }
        if (frame.timestampMs <= timestamp) return null
        timestamp = frame.timestampMs
        val completed = frame.repDelta == 1 && frame.repCount == count + 1
        count = frame.repCount
        if (!completed) return null
        val reached = !targetFired && count >= target
        if (reached) targetFired = true
        if (!active) return null
        val cue = if (reached) CompletionCue.TARGET else if (isHold) CompletionCue.HOLD else CompletionCue.REP
        return CompletionFeedback(cue, soundOn)
    }
}

/** Ordinary pulses never crowd a completion; a target may replace a preceding short cue. */
class CuePlaybackWindow {
    private var lastPlayedAt = -1000L
    private var lastCue: CompletionCue? = null
    fun eligible(cue: CompletionCue, nowMs: Long): Boolean =
        nowMs >= lastPlayedAt && (
            cue == CompletionCue.TARGET ||
            (lastCue == CompletionCue.TEMPO && cue != CompletionCue.TEMPO) ||
            nowMs - lastPlayedAt >= 300L
        )
    fun played(cue: CompletionCue, nowMs: Long) { lastCue = cue; lastPlayedAt = nowMs }
}

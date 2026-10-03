package com.grloepr.pushtrack.audio

/** Immediate focus only: denied/lost focus never queues a later success cue. */
class FocusPlayback(private val request: () -> Boolean, private val abandon: () -> Unit,
                    private val start: (CompletionCue) -> Boolean, private val halt: () -> Unit) {
    private var released = false
    fun play(cue: CompletionCue): Boolean {
        if (released) return false
        stop()
        if (!request()) return false
        if (!start(cue)) { stop(); return false }
        return true
    }
    fun stop() { halt(); abandon() }
    fun onFocusLost() = stop()
    fun release() { stop(); released = true }
}

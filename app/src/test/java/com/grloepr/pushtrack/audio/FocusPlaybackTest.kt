package com.grloepr.pushtrack.audio

import org.junit.Assert.*
import org.junit.Test

class FocusPlaybackTest {
    @Test fun deniedFocusDoesNotReplayAndLossStopsBeforeFreshRecovery() {
        val calls = mutableListOf<String>()
        var granted = false
        val player = FocusPlayback({ calls += "request"; granted }, { calls += "abandon" },
            { calls += "play:$it"; true }, { calls += "stop" })
        assertFalse(player.play(CompletionCue.REP))
        assertFalse(calls.any { it.startsWith("play") })
        granted = true // Gain alone has no queued event.
        assertFalse(calls.any { it.startsWith("play") })
        assertTrue(player.play(CompletionCue.HOLD))
        player.onFocusLost()
        assertEquals(listOf("stop", "abandon"), calls.takeLast(2))
        assertTrue(player.play(CompletionCue.TARGET))
        player.release()
        val before = calls.toList()
        assertFalse(player.play(CompletionCue.REP))
        assertEquals(before, calls)
    }
    @Test fun failedSoundStartImmediatelyAbandonsFocus() {
        val calls = mutableListOf<String>()
        val player = FocusPlayback({ true }, { calls += "abandon" }, { false }, { calls += "stop" })
        assertFalse(player.play(CompletionCue.REP))
        assertEquals(listOf("stop", "abandon", "stop", "abandon"), calls)
    }
}

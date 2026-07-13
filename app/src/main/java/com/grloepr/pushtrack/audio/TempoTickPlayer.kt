package com.grloepr.pushtrack.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Emits the short, soft metronome pip used by the tempo pacer. Failures are
 * swallowed — audio pacing is an enhancement, never a crash source.
 */
class TempoTickPlayer {

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
    } catch (e: RuntimeException) {
        Log.w(TAG, "ToneGenerator unavailable", e)
        null
    }

    fun tick() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, TICK_DURATION_MS)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Tempo tick failed", e)
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }

    private companion object {
        const val TAG = "TempoTickPlayer"
        const val VOLUME = 35 // 0..100, deliberately subtle
        const val TICK_DURATION_MS = 60
    }
}

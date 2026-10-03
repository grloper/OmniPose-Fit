package com.grloepr.pushtrack.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.grloepr.pushtrack.R

/** Original short synthesized cues. Media stream volume is never changed. No delayed playback. */
class SuccessSoundPlayer(context: Context) {
    private val ready = mutableSetOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val listener = AudioManager.OnAudioFocusChangeListener { change -> if (change <= 0) stop() }
    private val focusRequest = if (Build.VERSION.SDK_INT >= 26) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(listener, handler).build()
    } else null
    private val endCue = Runnable { stop() }
    private val playbackWindow = CuePlaybackWindow()
    private var pool: SoundPool? = runCatching {
        SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
            attributes
        ).build()
    }.getOrNull()
    private var stream = 0
    private val rep: Int
    private val target: Int
    private val tick: Int

    init {
        pool?.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
        rep = runCatching { pool?.load(context, R.raw.rep_success, 1) ?: 0 }.getOrDefault(0)
        target = runCatching { pool?.load(context, R.raw.target_success, 1) ?: 0 }.getOrDefault(0)
        tick = runCatching { pool?.load(context, R.raw.tempo_tick, 1) ?: 0 }.getOrDefault(0)
    }

    @Suppress("DEPRECATION")
    fun play(cue: CompletionCue) {
        val sound = when (cue) { CompletionCue.TARGET -> target; CompletionCue.TEMPO -> tick; else -> rep }
        val now = SystemClock.elapsedRealtime()
        if (sound !in ready || !playbackWindow.eligible(cue, now)) return
        stop()
        val granted = runCatching {
            if (Build.VERSION.SDK_INT >= 26) manager.requestAudioFocus(requireNotNull(focusRequest))
            else manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        playbackWindow.played(now)
        val volume = if (cue == CompletionCue.TEMPO) 0.18f else 0.35f
        stream = runCatching { pool?.play(sound, volume, volume, 1, 0, 1f) ?: 0 }.getOrDefault(0)
        if (stream == 0) stop() else handler.postDelayed(endCue, 500L)
    }

    @Suppress("DEPRECATION")
    fun stop() {
        handler.removeCallbacks(endCue)
        runCatching { if (stream != 0) pool?.stop(stream) }
        stream = 0
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) manager.abandonAudioFocusRequest(requireNotNull(focusRequest))
            else manager.abandonAudioFocus(listener)
        }
    }
    fun release() { stop(); pool?.release(); pool = null; ready.clear() }
}

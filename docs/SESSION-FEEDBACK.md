# Session feedback

Training audio preference persists across sessions and app restarts. Mute controls tempo ticks and success cues together; repetitive spoken counts have been removed to avoid collisions, stops currently playing feedback, and does not change device volume. Pausing/backgrounding also stops audio and drops an incomplete attempt while retaining completed counts.

Success cues are original synthesized PCM WAV files: a 220 ms rising two-note bell for a completed repetition/hold and a 440 ms four-note bell for the configured practice target. They contain no sampled recordings. SoundPool uses media audio attributes, max one stream and low relative volume. Unloaded cues are skipped, never queued for later playback. Player resources are released with the screen.

The pure feedback policy consumes each live completion once; restored counts, partials, duplicate callbacks and inactive events cannot replay success. Reset starts a new practice target. Repeating an already unlocked skill can celebrate a session target without awarding XP again. Generic exercise variants can celebrate a practice target but cannot automatically unlock progression.

The camera-based motion counter remains an estimate, not a form/accuracy certification. Tests added: muted/inactive consumption, restored/duplicate events, partials, repeated/reset targets, and a synthetic-camera UI journey with pause/resume and mute persistence between sessions. These changes have not yet been built or executed by the implementing worker; parent owns the bounded verification job.

Android references: https://developer.android.com/reference/android/media/SoundPool and https://developer.android.com/reference/android/media/AudioAttributes

Lightweight PCM inspection: rep 0.220 s, target 0.440 s; both peak at 0.2787 full scale (no clipped PCM samples). Subjective listening has not been performed by this worker. Cues request transient media focus, skip denied/unloaded playback, release focus within 500 ms, stop on focus loss, and suppress bursts closer than 300 ms. Parent must verify listening quality and physical-device media/focus behavior.

UX: fixed action footer above system navigation; scrollable skill detail body; explicit pause/resume retaining counts; branch shortcuts with discoverable sideways map; larger grid lanes/rows, higher-contrast secondary text, scalable system typography; compact training HUD; restrained dialog with Keep practicing instead of forcing exit/confetti. Ambient infinite map animation was removed; navigation and remaining brief Compose transitions follow system animator scaling. No additional XP is awarded for repeated targets or generic variants. No new SDK or build tooling dependency.

Accessible list/map choice and combined map title/status semantics are included. Selected training route survives saved-state restoration, but transient session counts do not; a visible recovery notice explicitly says previous counts were reset. No camera/image/count history is silently persisted. Tempo is also an original synthesized 45 ms cue in the same SoundPool; no platform beep or repeated TTS is used. Source generator is scripts/generate_feedback_audio.py.

Prepared endurance instrumentation: TrainingEnduranceTest executes 30 actual enter/pause/reset/resume/exit UI journeys and checks genuine detector activity with zero completed motion counts. Recreation retains the selected route and displays the reset-count notice. optionalEmptyCameraEndurance is skipped by default; root can explicitly pass soakMinutes=30 to test sustained empty-camera stability. Neither run validates movement recognition, body diversity, or count accuracy. The implementing worker did not execute them.

package com.grloepr.pushtrack.engine

/**
 * Tracks rep cadence so the UI can pace the athlete: keeps the duration of the
 * most recent reps and exposes a rolling average.
 */
class TempoTracker(private val window: Int = 5) {
    private val repTimestamps = ArrayDeque<Long>()
    private val repDurations = ArrayDeque<Long>()

    var lastRepDurationMs: Long? = null
        private set

    val averageRepDurationMs: Long?
        get() = if (repDurations.isEmpty()) null else repDurations.sum() / repDurations.size

    fun onRepCompleted(timestampMs: Long) {
        repTimestamps.lastOrNull()?.let { previous ->
            val duration = timestampMs - previous
            // Ignore pathological gaps (athlete rested between reps).
            if (duration in 400..20_000) {
                lastRepDurationMs = duration
                if (repDurations.size == window) repDurations.removeFirst()
                repDurations.addLast(duration)
            }
        }
        if (repTimestamps.size == window) repTimestamps.removeFirst()
        repTimestamps.addLast(timestampMs)
    }

    fun reset() {
        repTimestamps.clear()
        repDurations.clear()
        lastRepDurationMs = null
    }
}

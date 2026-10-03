package com.grloepr.pushtrack.analysis

/** Serializes stream transitions against frame admission and late detector callbacks. */
class AnalysisGate {
    private var enabled = true
    private var generation = 0L
    @Synchronized fun setEnabled(value: Boolean) {
        if (enabled != value) { enabled = value; generation++ }
    }
    @Synchronized fun invalidate() { generation++ }
    @Synchronized fun admit(): Long? = if (enabled) generation else null
    @Synchronized fun isCurrent(token: Long): Boolean = enabled && token == generation
}

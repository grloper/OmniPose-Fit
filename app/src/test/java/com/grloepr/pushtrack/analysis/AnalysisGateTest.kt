package com.grloepr.pushtrack.analysis

import org.junit.Assert.*
import org.junit.Test

class AnalysisGateTest {
    @Test fun pauseRejectsNewFramesAndLateCallbacksEvenAfterResume() {
        val gate = AnalysisGate()
        val old = requireNotNull(gate.admit())
        gate.setEnabled(false)
        assertNull(gate.admit())
        assertFalse(gate.isCurrent(old))
        gate.setEnabled(true)
        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(requireNotNull(gate.admit())))
    }
    @Test fun repeatedEnabledUpdatesPreserveLiveFramesButCameraSwitchInvalidates() {
        val gate = AnalysisGate()
        val token = requireNotNull(gate.admit())
        repeat(10) { gate.setEnabled(true) }
        assertTrue(gate.isCurrent(token))
        gate.invalidate()
        assertFalse(gate.isCurrent(token))
        assertTrue(gate.isCurrent(requireNotNull(gate.admit())))
    }
}

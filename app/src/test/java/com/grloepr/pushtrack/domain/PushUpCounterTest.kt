package com.grloepr.pushtrack.domain

import com.grloepr.pushtrack.domain.PushUpCounter
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for PushUpCounter
 */
class PushUpCounterTest {

    @Test
    fun testInitialState() {
        val counter = PushUpCounter()
        val state = counter.state.value
        
        assertEquals(0, state.count)
        assertEquals(PushUpCounter.Phase.UP, state.phase)
        assertNull(state.lastAngle)
        assertFalse(state.isTracking)
    }

    @Test
    fun testDownPhaseTransition() {
        val counter = PushUpCounter()
        
        // Send angle that should trigger DOWN phase
        counter.processAngle(65f) // Below 70° threshold
        
        val state = counter.state.value
        assertEquals(PushUpCounter.Phase.DOWN, state.phase)
        assertTrue(state.isTracking)
        assertEquals(0, state.count) // Count should not increment yet
    }

    @Test
    fun testPushUpCycle() {
        val counter = PushUpCounter()
        
        // Start in UP phase, go to DOWN
        counter.processAngle(65f) // Below 70° threshold
        Thread.sleep(300) // Wait for debounce
        
        // Go back to UP to complete one push-up
        counter.processAngle(165f) // Above 160° threshold
        
        val state = counter.state.value
        assertEquals(PushUpCounter.Phase.UP, state.phase)
        assertEquals(1, state.count) // Should increment count
    }

    @Test
    fun testDebounce() {
        val counter = PushUpCounter(debounceMs = 100L)
        
        // Send rapid angle changes
        counter.processAngle(65f) // DOWN
        counter.processAngle(165f) // UP - should be ignored due to debounce
        
        val state = counter.state.value
        assertEquals(PushUpCounter.Phase.DOWN, state.phase) // Should stay DOWN
        assertEquals(0, state.count)
    }

    @Test
    fun testMultiplePushUps() {
        val counter = PushUpCounter(debounceMs = 50L)
        
        // First push-up
        counter.processAngle(65f) // DOWN
        Thread.sleep(100)
        counter.processAngle(165f) // UP
        Thread.sleep(100)
        
        // Second push-up
        counter.processAngle(65f) // DOWN
        Thread.sleep(100)
        counter.processAngle(165f) // UP
        
        val state = counter.state.value
        assertEquals(2, state.count)
        assertEquals(PushUpCounter.Phase.UP, state.phase)
    }

    @Test
    fun testReset() {
        val counter = PushUpCounter(debounceMs = 50L)
        
        // Do a push-up
        counter.processAngle(65f)
        Thread.sleep(100)
        counter.processAngle(165f)
        
        assertEquals(1, counter.getCurrentCount())
        
        // Reset
        counter.reset()
        
        val state = counter.state.value
        assertEquals(0, state.count)
        assertEquals(PushUpCounter.Phase.UP, state.phase)
        assertNull(state.lastAngle)
        assertFalse(state.isTracking)
    }

    @Test
    fun testStopTracking() {
        val counter = PushUpCounter()
        
        counter.processAngle(65f)
        assertTrue(counter.isTracking())
        
        counter.stopTracking()
        assertFalse(counter.isTracking())
    }

    @Test
    fun testHysteresis() {
        val counter = PushUpCounter(downThreshold = 70f, upThreshold = 160f)
        
        // Start in UP, transition to DOWN
        counter.processAngle(65f) // Below 70°
        Thread.sleep(300)
        assertEquals(PushUpCounter.Phase.DOWN, counter.getCurrentPhase())
        
        // Send angle that's between thresholds - should stay DOWN
        counter.processAngle(100f) // Between 70° and 160°
        Thread.sleep(300)
        assertEquals(PushUpCounter.Phase.DOWN, counter.getCurrentPhase())
        
        // Only go to UP when above upper threshold
        counter.processAngle(165f) // Above 160°
        Thread.sleep(300)
        assertEquals(PushUpCounter.Phase.UP, counter.getCurrentPhase())
    }
}
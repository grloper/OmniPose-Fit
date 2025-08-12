package com.grloepr.pushtrack.detection.utils

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VelocityTrackerTest {
    
    private lateinit var velocityTracker: VelocityTracker
    
    @Before
    fun setUp() {
        velocityTracker = VelocityTracker(bufferSize = 3) // Small buffer for testing
    }
    
    @Test
    fun `initial state should have zero velocity`() {
        assertEquals(0f, velocityTracker.getCurrentVelocity(), 0.001f)
        assertEquals(0f, velocityTracker.getAverageVelocity(), 0.001f)
        assertFalse(velocityTracker.hasEnoughSamples())
    }
    
    @Test
    fun `should calculate velocity between samples`() {
        val time1 = 1000L
        val time2 = 1100L // 100ms later
        
        velocityTracker.addSample(0f, time1)
        velocityTracker.addSample(10f, time2) // Moved 10 units in 100ms
        
        assertTrue(velocityTracker.hasEnoughSamples())
        
        // Velocity should be 10 units / 0.1 seconds = 100 units/second
        assertEquals(100f, velocityTracker.getCurrentVelocity(), 0.1f)
    }
    
    @Test
    fun `should handle circular buffer correctly`() {
        // Fill buffer beyond capacity to test circular behavior
        velocityTracker.addSample(0f, 1000L)
        velocityTracker.addSample(10f, 1100L)
        velocityTracker.addSample(20f, 1200L)
        velocityTracker.addSample(30f, 1300L) // Should overwrite first sample
        
        // Should still calculate velocity correctly
        assertTrue(velocityTracker.getCurrentVelocity() > 0f)
        assertTrue(velocityTracker.getAverageVelocity() > 0f)
    }
    
    @Test
    fun `should detect fast movement correctly`() {
        velocityTracker.addSample(0f, 1000L)
        velocityTracker.addSample(20f, 1100L) // Fast movement: 20 units in 100ms = 200 units/second
        
        assertTrue(velocityTracker.isFastMovement(150f)) // Threshold: 150 units/second
        assertFalse(velocityTracker.isFastMovement(250f)) // Higher threshold
    }
    
    @Test
    fun `should provide adaptive thresholds based on velocity`() {
        // Slow movement
        velocityTracker.addSample(0f, 1000L)
        velocityTracker.addSample(1f, 1100L) // Slow: 1 unit in 100ms = 10 units/second
        
        val slowThreshold = velocityTracker.getAdaptiveThreshold(baseThreshold = 3, minThreshold = 1)
        assertEquals(4, slowThreshold) // Should increase for slow movement
        
        // Fast movement
        velocityTracker.reset()
        velocityTracker.addSample(0f, 1000L)
        velocityTracker.addSample(20f, 1100L) // Fast: 20 units in 100ms = 200 units/second
        
        val fastThreshold = velocityTracker.getAdaptiveThreshold(baseThreshold = 3, minThreshold = 1)
        assertEquals(1, fastThreshold) // Should decrease for fast movement
    }
    
    @Test
    fun `should reset correctly`() {
        // Add some samples
        velocityTracker.addSample(0f, 1000L)
        velocityTracker.addSample(10f, 1100L)
        
        assertTrue(velocityTracker.getCurrentVelocity() > 0f)
        assertTrue(velocityTracker.hasEnoughSamples())
        
        // Reset
        velocityTracker.reset()
        
        assertEquals(0f, velocityTracker.getCurrentVelocity(), 0.001f)
        assertEquals(0f, velocityTracker.getAverageVelocity(), 0.001f)
        assertFalse(velocityTracker.hasEnoughSamples())
    }
    
    @Test
    fun `should handle same timestamp gracefully`() {
        val sameTime = 1000L
        
        velocityTracker.addSample(0f, sameTime)
        velocityTracker.addSample(10f, sameTime) // Same timestamp
        
        // Should not crash and should handle zero time delta
        assertEquals(0f, velocityTracker.getCurrentVelocity(), 0.001f)
    }
    
    @Test
    fun `should calculate average velocity correctly`() {
        // Add multiple samples with known velocities
        velocityTracker.addSample(0f, 1000L)   // Baseline
        velocityTracker.addSample(10f, 1100L)  // Velocity: 100 units/second
        velocityTracker.addSample(30f, 1200L)  // Velocity: 200 units/second
        
        val avgVelocity = velocityTracker.getAverageVelocity()
        
        // Average should be between the two velocities
        assertTrue("Average velocity should be between 100 and 200", avgVelocity > 100f && avgVelocity < 200f)
    }
    
    @Test
    fun `should maintain O1 complexity with many samples`() {
        val startTime = System.nanoTime()
        
        // Add many samples to test O(1) behavior
        for (i in 0..1000) {
            velocityTracker.addSample(i.toFloat(), 1000L + i * 10L)
        }
        
        val endTime = System.nanoTime()
        val duration = endTime - startTime
        
        // Verify that velocity calculations still work
        assertTrue(velocityTracker.getCurrentVelocity() > 0f)
        assertTrue(velocityTracker.getAverageVelocity() > 0f)
        
        // The operation should be very fast even with many samples
        // This is a basic performance check - actual O(1) verification would need more sophisticated testing
        assertTrue("Operation should complete quickly", duration < 10_000_000) // 10ms
    }
}
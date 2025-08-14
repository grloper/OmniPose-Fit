package com.grloepr.pushtrack.detection

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for enhanced temporal smoothing and confidence-based detection
 */
class EnhancedDetectionUtilsTest {
    
    @Before
    fun setup() {
    }
    
    @Test
    fun `temporal smoother handles outliers correctly`() {
        // Add normal values
        val value1 = temporalSmoother.addSample(100f)
        val value2 = temporalSmoother.addSample(102f)
        val value3 = temporalSmoother.addSample(98f)
        
        // Add outlier
        val outlierValue = temporalSmoother.addSample(150f) // 50% deviation
        
        // Outlier should be rejected/smoothed
        assertTrue("Outlier should be smoothed", outlierValue < 130f)
        
        // Return to normal values should work
        val normalValue = temporalSmoother.addSample(101f)
        assertTrue("Normal value should be accepted", 
                   normalValue > 95f && normalValue < 110f)
    }
    
    @Test
    fun `temporal smoother provides stable output for noisy input`() {
        val noisyInputs = floatArrayOf(100f, 105f, 95f, 102f, 98f, 103f, 97f, 101f)
        val outputs = mutableListOf<Float>()
        
        noisyInputs.forEach { input ->
            outputs.add(temporalSmoother.addSample(input))
        }
        
        // Check that output variance is less than input variance
        val inputVariance = calculateVariance(noisyInputs.toList())
        val outputVariance = calculateVariance(outputs)
        
        assertTrue("Output should be smoother than input", outputVariance < inputVariance)
    }
    
    @Test
    fun `debounced state machine prevents rapid state changes`() {
        // Send alternating signals (should not cause rapid changes)
        var confirmedState = stateMachine.updateState(ExercisePhase.DOWN)
        assertEquals("Initial state should be UP", ExercisePhase.UP, confirmedState)
        
        confirmedState = stateMachine.updateState(ExercisePhase.UP)
        assertEquals("Single opposite signal should not change state", ExercisePhase.UP, confirmedState)
        
        // Send consistent DOWN signals
        repeat(3) {
            confirmedState = stateMachine.updateState(ExercisePhase.DOWN)
        }
        assertEquals("Consistent signals should change state", ExercisePhase.DOWN, confirmedState)
    }
    
    @Test
    fun `confidence rep counter handles uncertain reps correctly`() {
        // High confidence rep
        repCounter.processPhase(ExercisePhase.DOWN, 0.9f)
        repCounter.processPhase(ExercisePhase.UP, 0.9f)
        
        // Low confidence rep
        repCounter.processPhase(ExercisePhase.DOWN, 0.5f)
        repCounter.processPhase(ExercisePhase.UP, 0.5f)
        
        // Very low confidence rep (should not count)
        repCounter.processPhase(ExercisePhase.DOWN, 0.3f)
        repCounter.processPhase(ExercisePhase.UP, 0.3f)
        
        val result = repCounter.processPhase(ExercisePhase.UP, 0.8f)
        
        assertEquals("Should have 2 total reps (high + uncertain)", 2, result.count)
        assertEquals("Should have 1 uncertain rep", 1, result.uncertainCount)
        assertEquals("Should have 1 confirmed rep", 1, repCounter.getConfirmedReps())
    }
    
    @Test
    fun `confidence rep counter calculates confidence ratio correctly`() {
        // Add mix of confident and uncertain reps
        repeat(5) {
            repCounter.processPhase(ExercisePhase.DOWN, 0.9f)
            repCounter.processPhase(ExercisePhase.UP, 0.9f)
        }
        
        repeat(2) {
            repCounter.processPhase(ExercisePhase.DOWN, 0.65f)
            repCounter.processPhase(ExercisePhase.UP, 0.65f)
        }
        
        val stats = repCounter.getStats()
        val confidenceRatio = stats["confidence_ratio"] as Float
        
        assertEquals("Should have 7 total reps", 7, stats["total_reps"])
        assertEquals("Should have 5 confirmed reps", 5, stats["confirmed_reps"])
        assertEquals("Should have 2 uncertain reps", 2, stats["uncertain_reps"])
        assertTrue("Confidence ratio should be around 0.714", 
                   Math.abs(confidenceRatio - 0.714f) < 0.1f)
    }
    
    @Test
    fun `state machine transition progress tracking works correctly`() {
        // Start transitioning to DOWN
        stateMachine.updateState(ExercisePhase.DOWN)
        assertTrue("Should be transitioning", stateMachine.isTransitioning())
        
        val progress1 = stateMachine.getTransitionProgress()
        assertTrue("Progress should be > 0", progress1 > 0f)
        
        // Continue transitioning
        stateMachine.updateState(ExercisePhase.DOWN)
        val progress2 = stateMachine.getTransitionProgress()
        assertTrue("Progress should increase", progress2 > progress1)
        
        // Complete transition
        stateMachine.updateState(ExercisePhase.DOWN)
        assertFalse("Should not be transitioning after confirmation", 
                    stateMachine.isTransitioning())
        assertEquals("Progress should be 0 after completion", 
                     0f, stateMachine.getTransitionProgress())
    }
    
    @Test
    fun `temporal smoother resets correctly on too many outliers`() {
        // Establish baseline
        repeat(5) {
            temporalSmoother.addSample(100f)
        }
        
        // Send many outliers to trigger reset
        repeat(5) {
            temporalSmoother.addSample(200f) // 100% deviation
        }
        
        // Should reset and accept new baseline
        val newValue = temporalSmoother.addSample(200f)
        assertTrue("Should accept new baseline after reset", 
                   Math.abs(newValue - 200f) < 10f)
    }
    
    private fun calculateVariance(values: List<Float>): Float {
        val mean = values.average().toFloat()
        val squaredDiffs = values.map { (it - mean) * (it - mean) }
        return squaredDiffs.average().toFloat()
    }
}
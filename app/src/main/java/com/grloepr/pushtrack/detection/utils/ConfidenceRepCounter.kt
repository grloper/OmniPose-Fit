package com.grloepr.pushtrack.detection.utils

import com.grloepr.pushtrack.detection.ExercisePhase

/**
 * Advanced rep counter with confidence tracking and uncertain rep detection
 */
class ConfidenceRepCounter(
    private val uncertainThreshold: Float = 0.6f,
    private val validThreshold: Float = 0.8f
) {
    
    data class RepResult(
        val count: Int,
        val uncertainCount: Int,
        val lastRepConfidence: Float,
        val phaseConfidences: List<Float> = emptyList()
    )
    
    private var totalReps = 0
    private var uncertainReps = 0
    private var lastPhase = ExercisePhase.UP
    private var currentRepConfidences = mutableListOf<Float>()
    private var isInRep = false
    
    /**
     * Process a new phase detection with confidence
     */
    fun processPhase(phase: ExercisePhase, confidence: Float): RepResult {
        // Add confidence to current rep tracking
        currentRepConfidences.add(confidence)
        
        // Limit confidence history to prevent memory growth
        if (currentRepConfidences.size > 20) {
            currentRepConfidences = currentRepConfidences.takeLast(15).toMutableList()
        }
        
        // Detect rep completion (DOWN -> UP transition)
        if (lastPhase == ExercisePhase.DOWN && phase == ExercisePhase.UP) {
            completeRep()
        }
        
        // Track if we're currently in a rep
        when (phase) {
            ExercisePhase.DOWN -> isInRep = true
            ExercisePhase.UP -> if (isInRep) isInRep = false
            ExercisePhase.TRANSITIONING -> {} // Keep current state
        }
        
        lastPhase = phase
        
        val lastRepConf = if (currentRepConfidences.isNotEmpty()) {
            currentRepConfidences.average().toFloat()
        } else {
            confidence
        }
        
        return RepResult(
            count = totalReps,
            uncertainCount = uncertainReps,
            lastRepConfidence = lastRepConf,
            phaseConfidences = currentRepConfidences.toList()
        )
    }
    
    /**
     * Complete a rep and evaluate its confidence
     */
    private fun completeRep() {
        if (currentRepConfidences.isEmpty()) {
            // No confidence data, skip this rep
            return
        }
        
        val avgConfidence = currentRepConfidences.average().toFloat()
        val minConfidence = currentRepConfidences.minOrNull() ?: 0f
        
        // Use weighted confidence: average confidence + minimum confidence penalty
        val weightedConfidence = (avgConfidence * 0.7f) + (minConfidence * 0.3f)
        
        when {
            weightedConfidence >= validThreshold -> {
                // High confidence rep
                totalReps++
            }
            weightedConfidence >= uncertainThreshold -> {
                // Uncertain rep - count it but mark as uncertain
                totalReps++
                uncertainReps++
            }
            else -> {
                // Low confidence - don't count this rep
                // Could add to a "missed rep" counter if needed
            }
        }
        
        // Reset confidence tracking for next rep
        currentRepConfidences.clear()
    }
    
    /**
     * Get total confirmed reps
     */
    fun getTotalReps(): Int = totalReps
    
    /**
     * Get total uncertain reps
     */
    fun getUncertainReps(): Int = uncertainReps
    
    /**
     * Get confirmed reps (total - uncertain)
     */
    fun getConfirmedReps(): Int = totalReps - uncertainReps
    
    /**
     * Get confidence ratio for current session
     */
    fun getConfidenceRatio(): Float {
        return if (totalReps > 0) {
            getConfirmedReps().toFloat() / totalReps.toFloat()
        } else {
            1.0f
        }
    }
    
    /**
     * Check if currently in a rep
     */
    fun isCurrentlyInRep(): Boolean = isInRep
    
    /**
     * Get current rep confidence (ongoing)
     */
    fun getCurrentRepConfidence(): Float {
        return if (currentRepConfidences.isNotEmpty()) {
            currentRepConfidences.average().toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Reset the counter
     */
    fun reset() {
        totalReps = 0
        uncertainReps = 0
        lastPhase = ExercisePhase.UP
        currentRepConfidences.clear()
        isInRep = false
    }
    
    /**
     * Manual rep addition (for testing or correction)
     */
    fun addManualRep(confidence: Float = 1.0f) {
        totalReps++
        if (confidence < validThreshold) {
            uncertainReps++
        }
    }
    
    /**
     * Get detailed statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "total_reps" to totalReps,
            "confirmed_reps" to getConfirmedReps(),
            "uncertain_reps" to uncertainReps,
            "confidence_ratio" to getConfidenceRatio(),
            "current_rep_confidence" to getCurrentRepConfidence(),
            "is_in_rep" to isInRep
        )
    }
}
package com.grloepr.pushtrack.detection.utils

/**
 * O(1) velocity tracker using fixed-size circular buffer
 * Tracks movement velocity without growing state
 */
class VelocityTracker(private val bufferSize: Int = 5) {
    
    // Fixed-size circular buffer for position history
    private val positionBuffer = FloatArray(bufferSize)
    private val timeBuffer = LongArray(bufferSize)
    private var bufferIndex = 0
    private var bufferFull = false
    
    // Current velocity state
    private var currentVelocity = 0f
    private var averageVelocity = 0f
    
    /**
     * Add a new position sample
     * @param position Current position value
     * @param timestamp Current timestamp in milliseconds
     */
    fun addSample(position: Float, timestamp: Long = System.currentTimeMillis()) {
        // Store in circular buffer
        positionBuffer[bufferIndex] = position
        timeBuffer[bufferIndex] = timestamp
        
        // Calculate velocity if we have at least 2 samples
        if (bufferFull || bufferIndex > 0) {
            val prevIndex = if (bufferIndex == 0) bufferSize - 1 else bufferIndex - 1
            val prevPosition = positionBuffer[prevIndex]
            val prevTime = timeBuffer[prevIndex]
            
            val deltaPosition = kotlin.math.abs(position - prevPosition)
            val deltaTime = timestamp - prevTime
            
            currentVelocity = if (deltaTime > 0) {
                deltaPosition / deltaTime.toFloat() * 1000f // Convert to per second
            } else {
                0f
            }
            
            // Update average velocity
            updateAverageVelocity()
        }
        
        // Move to next position in circular buffer
        bufferIndex = (bufferIndex + 1) % bufferSize
        if (bufferIndex == 0) {
            bufferFull = true
        }
    }
    
    /**
     * Calculate average velocity from all samples in buffer
     * Runs in O(1) time by maintaining running average
     */
    private fun updateAverageVelocity() {
        if (!bufferFull && bufferIndex < 2) {
            averageVelocity = currentVelocity
            return
        }
        
        val sampleCount = if (bufferFull) bufferSize - 1 else bufferIndex
        var totalVelocity = 0f
        var velocityCount = 0
        
        for (i in 0 until sampleCount) {
            val currentIdx = i
            val nextIdx = (i + 1) % bufferSize
            
            if (timeBuffer[nextIdx] > timeBuffer[currentIdx]) {
                val deltaPos = kotlin.math.abs(positionBuffer[nextIdx] - positionBuffer[currentIdx])
                val deltaTime = timeBuffer[nextIdx] - timeBuffer[currentIdx]
                
                if (deltaTime > 0) {
                    totalVelocity += deltaPos / deltaTime.toFloat() * 1000f
                    velocityCount++
                }
            }
        }
        
        averageVelocity = if (velocityCount > 0) totalVelocity / velocityCount else 0f
    }
    
    /**
     * Get current instantaneous velocity
     * @return Current velocity in units per second
     */
    fun getCurrentVelocity(): Float = currentVelocity
    
    /**
     * Get average velocity over the buffer window
     * @return Average velocity in units per second
     */
    fun getAverageVelocity(): Float = averageVelocity
    
    /**
     * Check if movement is fast based on velocity threshold
     * @param threshold Velocity threshold for fast movement detection
     * @return True if current velocity exceeds threshold
     */
    fun isFastMovement(threshold: Float = 10f): Boolean = currentVelocity > threshold
    
    /**
     * Get adaptive confirmation threshold based on movement speed
     * Fast movements need fewer confirmations for state changes
     * @param baseThreshold Base confirmation threshold for slow movements
     * @param minThreshold Minimum threshold for very fast movements
     * @return Adaptive threshold value
     */
    fun getAdaptiveThreshold(baseThreshold: Int = 3, minThreshold: Int = 1): Int {
        return when {
            averageVelocity > 15f -> minThreshold // Very fast
            averageVelocity > 10f -> baseThreshold - 1 // Fast
            averageVelocity > 5f -> baseThreshold // Normal
            else -> baseThreshold + 1 // Slow
        }
    }
    
    /**
     * Reset the velocity tracker
     */
    fun reset() {
        bufferIndex = 0
        bufferFull = false
        currentVelocity = 0f
        averageVelocity = 0f
        // Clear buffers
        for (i in positionBuffer.indices) {
            positionBuffer[i] = 0f
            timeBuffer[i] = 0L
        }
    }
    
    /**
     * Check if tracker has enough samples for reliable velocity calculation
     * @return True if at least 2 samples are available
     */
    fun hasEnoughSamples(): Boolean = bufferFull || bufferIndex >= 2
}
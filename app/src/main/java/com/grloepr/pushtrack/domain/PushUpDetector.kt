package com.grloepr.pushtrack.domain

/**
 * Interface for push-up motion detection.
 * This is a stub for future motion detection integration.
 */
interface PushUpDetector {
    
    /**
     * Analyzes image data for push-up motion
     * @return true if a push-up motion was detected
     */
    fun detectPushUp(imageData: ByteArray): Boolean
    
    /**
     * Starts motion detection
     */
    fun startDetection()
    
    /**
     * Stops motion detection
     */
    fun stopDetection()
    
    /**
     * Callback for when a push-up is detected
     */
    fun interface OnPushUpDetectedListener {
        fun onPushUpDetected()
    }
    
    /**
     * Sets the listener for push-up detection events
     */
    fun setOnPushUpDetectedListener(listener: OnPushUpDetectedListener?)
}
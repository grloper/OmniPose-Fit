package com.grloepr.pushtrack.domain

/**
 * Simplified camera view angle enum - used for future enhancements
 */
enum class CameraViewAngle {
    /**
     * Front view (facing the person)
     * Optimal for tracking arm movements and elbow angles
     */
    FRONT,
    
    /**
     * Side view (person is in profile)
     * Optimal for tracking back angle, spine alignment, and knee angles
     */
    SIDE,
    
    /**
     * Top-down view (camera above person)
     * Optimal for tracking push-up form and shoulder alignment
     */
    TOP,
    
    /**
     * Unknown or undetermined view
     */
    UNKNOWN
}


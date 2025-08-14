package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.detection.utils.AngleCalculator
import com.grloepr.pushtrack.detection.utils.KeypointNormalizer
import com.grloepr.pushtrack.detection.utils.TemporalSmoother
import com.grloepr.pushtrack.detection.utils.DebouncedStateMachine
import com.grloepr.pushtrack.detection.utils.ConfidenceRepCounter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced push-up detector with temporal smoothing and confidence tracking
 * Uses normalized keypoints and advanced signal processing for robust detection
 */
class PushUpDetector : BaseExerciseDetector(ExerciseType.PUSH_UP) {
    
    // Normalized thresholds (scale-invariant) - TUNED for real-world conditions
    private var upElbowThreshold = 120f    // Arms extended (more forgiving for range of motion)
    private var downElbowThreshold = 100f  // Arms bent (more realistic for most people)
    private val torsoParallelThreshold = 35f // Max degrees from horizontal for good form (very forgiving)
    
    // Adaptive calibration for personalized thresholds
    private var calibrationFrames = 0
    private var maxObservedAngle = 0f
    private var minObservedAngle = 180f
    private val calibrationPeriod = 50 // Frames to observe before adapting
    private var isCalibrated = false
    
    // Advanced processing components - TUNED for maximum responsiveness  
    private val angleSmoother = TemporalSmoother(emaAlpha = 0.4f) // Much more responsive
    private val torsoPitchSmoother = TemporalSmoother(emaAlpha = 0.5f) // Much more responsive
    private val stateMachine = DebouncedStateMachine(confirmationThreshold = 1) // Immediate response
    private val repCounter = ConfidenceRepCounter()
    
    // Enhanced tracking
    private var lastNormalizedPose: KeypointNormalizer.NormalizedPose? = null
    private var consecutiveLowConfidenceFrames = 0
    private val maxLowConfidenceFrames = 5
    
    // Debug tracking for troubleshooting
    private var debugInfo = mutableMapOf<String, Any>()
    
    
    /**
     * Calculate primary angle (normalized average elbow angle) for push-up detection
     */
    override fun calculatePrimaryAngle(pose: Pose): Float? {
        val normalizedPose = KeypointNormalizer.normalizePose(pose) ?: return null
        lastNormalizedPose = normalizedPose
        
        // Calculate elbow angles using normalized coordinates
        val leftAngle = calculateNormalizedElbowAngle(normalizedPose, isLeft = true)
        val rightAngle = calculateNormalizedElbowAngle(normalizedPose, isLeft = false)
        
        val validAngles = listOfNotNull(leftAngle, rightAngle)
        var averageAngle: Float? = null
        
        // Primary detection method - elbow angles
        if (validAngles.isNotEmpty()) {
            averageAngle = validAngles.average().toFloat()
        } else {
            // Fallback method - shoulder-to-wrist distance variation (for occluded elbows)
            averageAngle = calculateFallbackAngle(normalizedPose)
        }
        
        if (averageAngle == null) return null
        
        // Adaptive calibration - observe user's range of motion
        if (!isCalibrated && calibrationFrames < calibrationPeriod) {
            maxObservedAngle = maxOf(maxObservedAngle, averageAngle)
            minObservedAngle = minOf(minObservedAngle, averageAngle)
            calibrationFrames++
            
            if (calibrationFrames >= calibrationPeriod) {
                // Adapt thresholds based on observed range
                val range = maxObservedAngle - minObservedAngle
                if (range > 20f) { // Only adapt if we see meaningful range
                    upElbowThreshold = maxObservedAngle - (range * 0.2f) // 80% of max
                    downElbowThreshold = minObservedAngle + (range * 0.2f) // 20% above min
                    isCalibrated = true
                    debugInfo["calibrated_up_threshold"] = upElbowThreshold
                    debugInfo["calibrated_down_threshold"] = downElbowThreshold
                    debugInfo["observed_range"] = range
                }
            }
        }
        
        // Update debug info
        debugInfo["left_elbow_angle"] = leftAngle ?: "null"
        debugInfo["right_elbow_angle"] = rightAngle ?: "null"
        debugInfo["average_angle"] = averageAngle
        debugInfo["valid_angles_count"] = validAngles.size
        debugInfo["calibration_progress"] = calibrationFrames
        debugInfo["is_calibrated"] = isCalibrated
        debugInfo["using_fallback"] = validAngles.isEmpty()
        
        // Apply temporal smoothing
        val smoothedAngle = angleSmoother.addSample(averageAngle)
        debugInfo["smoothed_angle"] = smoothedAngle
        
        return smoothedAngle
    }
    
    /**
     * Fallback detection method using shoulder-to-wrist distance
     * Used when elbow landmarks are occluded or have low confidence
     */
    private fun calculateFallbackAngle(normalizedPose: KeypointNormalizer.NormalizedPose): Float? {
        val leftShoulder = normalizedPose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = normalizedPose.getLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftWrist = normalizedPose.getLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = normalizedPose.getLandmark(PoseLandmark.RIGHT_WRIST)
        
        val validDistances = mutableListOf<Float>()
        
        // Calculate shoulder-to-wrist distances
        if (leftShoulder != null && leftWrist != null && 
            leftShoulder.confidence > 0.3f && leftWrist.confidence > 0.3f) {
            val leftDistance = kotlin.math.sqrt(
                (leftShoulder.x - leftWrist.x).let { it * it } +
                (leftShoulder.y - leftWrist.y).let { it * it }
            )
            validDistances.add(leftDistance)
        }
        
        if (rightShoulder != null && rightWrist != null &&
            rightShoulder.confidence > 0.3f && rightWrist.confidence > 0.3f) {
            val rightDistance = kotlin.math.sqrt(
                (rightShoulder.x - rightWrist.x).let { it * it } +
                (rightShoulder.y - rightWrist.y).let { it * it }
            )
            validDistances.add(rightDistance)
        }
        
        if (validDistances.isEmpty()) return null
        
        // Convert distance to pseudo-angle (shorter distance = more bent = lower angle)
        val avgDistance = validDistances.average().toFloat()
        // Map distance to angle range approximately
        val pseudoAngle = (avgDistance * 200f).coerceIn(60f, 160f)
        
        debugInfo["fallback_avg_distance"] = avgDistance
        debugInfo["fallback_pseudo_angle"] = pseudoAngle
        
        return pseudoAngle
    }
    
    /**
     * Calculate normalized elbow angle
     */
    private fun calculateNormalizedElbowAngle(
        normalizedPose: KeypointNormalizer.NormalizedPose,
        isLeft: Boolean
    ): Float? {
        val shoulderType = if (isLeft) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER
        val elbowType = if (isLeft) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
        val wristType = if (isLeft) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
        
        val shoulder = normalizedPose.getLandmark(shoulderType) ?: return null
        val elbow = normalizedPose.getLandmark(elbowType) ?: return null
        val wrist = normalizedPose.getLandmark(wristType) ?: return null
        
        // Require reasonable confidence for angle calculation (lowered threshold for real-world conditions)
        if (shoulder.confidence < 0.4f || elbow.confidence < 0.4f || wrist.confidence < 0.4f) {
            return null
        }
        
        return KeypointNormalizer.calculateNormalizedAngle(shoulder, elbow, wrist)
    }
    
    /**
     * Determine push-up phase using enhanced detection logic
     */
    override fun determinePhase(pose: Pose, primaryAngle: Float?): ExercisePhase {
        if (primaryAngle == null) {
            consecutiveLowConfidenceFrames++
            debugInfo["consecutive_low_confidence"] = consecutiveLowConfidenceFrames
            return if (consecutiveLowConfidenceFrames > maxLowConfidenceFrames) {
                ExercisePhase.TRANSITIONING
            } else {
                stateMachine.getCurrentState()
            }
        }
        
        consecutiveLowConfidenceFrames = 0
        
        // Enhanced phase detection with torso analysis
        val anglePhase = when {
            primaryAngle < downElbowThreshold -> ExercisePhase.DOWN
            primaryAngle > upElbowThreshold -> ExercisePhase.UP
            else -> ExercisePhase.TRANSITIONING
        }
        
        // Update debug info
        debugInfo["primary_angle"] = primaryAngle
        debugInfo["up_threshold"] = upElbowThreshold
        debugInfo["down_threshold"] = downElbowThreshold
        debugInfo["raw_phase"] = anglePhase.name
        
        // Validate with torso parallelism for good form
        val torsoPhase = validateWithTorsoForm(lastNormalizedPose, anglePhase)
        debugInfo["torso_validated_phase"] = torsoPhase.name
        
        // Use debounced state machine
        val finalPhase = stateMachine.updateState(torsoPhase)
        debugInfo["final_phase"] = finalPhase.name
        debugInfo["state_machine_transitioning"] = stateMachine.isTransitioning()
        
        return finalPhase
    }
    
    
    /**
     * Validate detected phase with torso form analysis
     */
    private fun validateWithTorsoForm(
        normalizedPose: KeypointNormalizer.NormalizedPose?,
        anglePhase: ExercisePhase
    ): ExercisePhase {
        if (normalizedPose == null) return anglePhase
        
        val torsoPitch = calculateTorsoPitch(normalizedPose)
        if (torsoPitch == null) return anglePhase
        
        val smoothedPitch = torsoPitchSmoother.addSample(torsoPitch)
        
        // Update debug info
        debugInfo["torso_pitch_raw"] = torsoPitch
        debugInfo["torso_pitch_smoothed"] = smoothedPitch
        debugInfo["torso_threshold"] = torsoParallelThreshold
        
        // For good form push-ups, torso should remain relatively parallel to ground
        // More forgiving for real-world conditions
        val isGoodForm = abs(smoothedPitch) < torsoParallelThreshold
        debugInfo["torso_good_form"] = isGoodForm
        
        // Only reject DOWN phase if form is really bad (>50 degrees) - very forgiving
        val shouldReject = !isGoodForm && anglePhase == ExercisePhase.DOWN && abs(smoothedPitch) > 50f
        debugInfo["torso_rejected"] = shouldReject
        
        return if (shouldReject) {
            ExercisePhase.TRANSITIONING
        } else {
            anglePhase
        }
    }
    
    /**
     * Calculate torso pitch angle (degrees from horizontal)
     */
    private fun calculateTorsoPitch(normalizedPose: KeypointNormalizer.NormalizedPose): Float? {
        val leftShoulder = normalizedPose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = normalizedPose.getLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = normalizedPose.getLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = normalizedPose.getLandmark(PoseLandmark.RIGHT_HIP)
        
        if (leftShoulder == null || rightShoulder == null || leftHip == null || rightHip == null) {
            return null
        }
        
        // Calculate torso center points
        val shoulderCenterY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipCenterY = (leftHip.y + rightHip.y) / 2f
        val shoulderCenterX = (leftShoulder.x + rightShoulder.x) / 2f
        val hipCenterX = (leftHip.x + rightHip.x) / 2f
        
        // Calculate angle from horizontal
        val deltaY = shoulderCenterY - hipCenterY
        val deltaX = shoulderCenterX - hipCenterX
        
        return if (deltaX != 0f) {
            Math.toDegrees(kotlin.math.atan(deltaY / deltaX).toDouble()).toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Enhanced confidence calculation with form analysis
     */
    override fun calculateConfidence(pose: Pose, primaryAngle: Float?): Float {
        val normalizedPose = lastNormalizedPose
        if (normalizedPose == null || primaryAngle == null) {
            return 0f
        }
        
        var confidence = 0f
        var factors = 0
        
        // Base confidence from pose quality
        confidence += normalizedPose.confidence
        factors++
        
        // Angle reliability bonus
        if (primaryAngle > 0f) {
            confidence += 0.3f
            factors++
        }
        
        // Form bonus (good torso alignment)
        val torsoPitch = calculateTorsoPitch(normalizedPose)
        if (torsoPitch != null && abs(torsoPitch) < torsoParallelThreshold) {
            confidence += 0.2f
            factors++
        }
        
        // Key landmark visibility bonus
        val requiredLandmarks = listOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST
        )
        
        if (KeypointNormalizer.hasSufficientQuality(normalizedPose, requiredLandmarks)) {
            confidence += 0.2f
            factors++
        }
        
        val finalConfidence = if (factors > 0) (confidence / factors).coerceIn(0f, 1f) else 0f
        
        // Update rep counter with phase and confidence
        val currentPhase = stateMachine.getCurrentState()
        repCounter.processPhase(currentPhase, finalConfidence)
        
        return finalConfidence
    }
    
    
    /**
     * Get detection method description
     */
    override fun getDetectionMethod(): String {
        val baseMethod = if (lastPrimaryAngle != null) "normalized_elbow_angle" else "none"
        val confidence = repCounter.getCurrentRepConfidence()
        val uncertainReps = repCounter.getUncertainReps()
        
        return if (uncertainReps > 0) {
            "$baseMethod+uncertain_reps"
        } else {
            baseMethod
        }
    }
    
    /**
     * Get rep count from confidence-based counter
     */
    override fun getRepCount(): Int = repCounter.getTotalReps()
    
    /**
     * Get additional statistics
     */
    fun getDetailedStats(): Map<String, Any> {
        val baseStats = repCounter.getStats()
        return baseStats + mapOf(
            "torso_form_quality" to calculateFormQuality(),
            "state_machine_progress" to stateMachine.getTransitionProgress(),
            "is_transitioning" to stateMachine.isTransitioning()
        ) + debugInfo // Include all debug information
    }
    
    /**
     * Get current debug information for troubleshooting
     */
    fun getDebugInfo(): Map<String, Any> = debugInfo.toMap()
    
    /**
     * Calculate form quality based on torso alignment
     */
    private fun calculateFormQuality(): Float {
        val currentPitch = torsoPitchSmoother.getCurrentValue() ?: return 0f
        val formScore = (torsoParallelThreshold - abs(currentPitch).coerceAtMost(torsoParallelThreshold)) / torsoParallelThreshold
        return formScore.coerceIn(0f, 1f)
    }
    
    /**
     * Reset detector state
     */
    override fun reset() {
        super.reset()
        angleSmoother.reset()
        torsoPitchSmoother.reset()
        stateMachine.reset()
        repCounter.reset()
        lastNormalizedPose = null
        consecutiveLowConfidenceFrames = 0
        debugInfo.clear()
        
        // Reset calibration
        calibrationFrames = 0
        maxObservedAngle = 0f
        minObservedAngle = 180f
        isCalibrated = false
        upElbowThreshold = 120f // Reset to defaults
        downElbowThreshold = 100f
    }
}
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
    
    // Normalized thresholds (scale-invariant) - TUNED for better accuracy
    private val upElbowThreshold = 130f    // Arms extended (reduced from 140)
    private val downElbowThreshold = 90f   // Arms bent (increased from 80)
    private val torsoParallelThreshold = 20f // Max degrees from horizontal for good form (increased tolerance)
    
    // Advanced processing components - TUNED for responsiveness  
    private val angleSmoother = TemporalSmoother(emaAlpha = 0.25f) // More responsive
    private val torsoPitchSmoother = TemporalSmoother(emaAlpha = 0.3f) // More responsive
    private val stateMachine = DebouncedStateMachine(confirmationThreshold = 2) // Faster response
    private val repCounter = ConfidenceRepCounter()
    
    // Enhanced tracking
    private var lastNormalizedPose: KeypointNormalizer.NormalizedPose? = null
    private var consecutiveLowConfidenceFrames = 0
    private val maxLowConfidenceFrames = 5
    
    
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
        if (validAngles.isEmpty()) return null
        
        val averageAngle = validAngles.average().toFloat()
        
        // Apply temporal smoothing
        return angleSmoother.addSample(averageAngle)
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
        
        // Require reasonable confidence for angle calculation (lowered threshold)
        if (shoulder.confidence < 0.6f || elbow.confidence < 0.6f || wrist.confidence < 0.6f) {
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
        
        // Validate with torso parallelism for good form
        val torsoPhase = validateWithTorsoForm(lastNormalizedPose, anglePhase)
        
        // Use debounced state machine
        return stateMachine.updateState(torsoPhase)
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
        
        // For good form push-ups, torso should remain relatively parallel to ground
        // More forgiving for real-world conditions
        val isGoodForm = abs(smoothedPitch) < torsoParallelThreshold
        
        // Only reject DOWN phase if form is really bad (>30 degrees)
        return if (!isGoodForm && anglePhase == ExercisePhase.DOWN && abs(smoothedPitch) > 30f) {
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
        )
    }
    
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
    }
}
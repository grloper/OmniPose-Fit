package com.grloepr.pushtrack.detection

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.detection.utils.KeypointNormalizer
import com.grloepr.pushtrack.detection.utils.TemporalSmoother
import com.grloepr.pushtrack.detection.utils.DebouncedStateMachine
import com.grloepr.pushtrack.detection.utils.ConfidenceRepCounter
import kotlin.math.abs

/**
 * Enhanced pull-up detector focusing on torso vertical movement and chin-to-bar relationship
 * Uses normalized keypoints and advanced signal processing for accurate detection
 */
class PullUpDetector : BaseExerciseDetector(ExerciseType.PULL_UP) {
    
    // Normalized thresholds for torso movement detection - TUNED for better accuracy
    private val torsoRiseThreshold = 0.12f      // Normalized distance torso moves up (reduced for sensitivity)
    private val chinBarThreshold = 0.08f        // Normalized distance chin to estimated bar level (reduced)
    private val armExtensionThreshold = 110f    // Minimum elbow angle for hanging position (reduced)
    private val armContractionThreshold = 80f   // Maximum elbow angle for pulled-up position (increased)
    
    // Advanced processing components - TUNED for better responsiveness
    private val torsoPositionSmoother = TemporalSmoother(emaAlpha = 0.3f) // More responsive
    private val chinPositionSmoother = TemporalSmoother(emaAlpha = 0.35f) // More responsive
    private val elbowAngleSmoother = TemporalSmoother(emaAlpha = 0.25f) // More responsive
    private val stateMachine = DebouncedStateMachine(confirmationThreshold = 2) // Faster response
    private val repCounter = ConfidenceRepCounter()
    
    // Enhanced tracking
    private var lastNormalizedPose: KeypointNormalizer.NormalizedPose? = null
    private var barLevelEstimate: Float? = null
    private var hangingTorsoBaseline: Float? = null
    private var calibrationFrames = 0
    
    
    /**
     * Calculate primary signal: torso vertical position for pull-up detection
     */
    override fun calculatePrimaryAngle(pose: Pose): Float? {
        val normalizedPose = KeypointNormalizer.normalizePose(pose) ?: return null
        lastNormalizedPose = normalizedPose
        
        // Calculate torso center vertical position
        val torsoPosition = calculateTorsoVerticalPosition(normalizedPose) ?: return null
        
        // Auto-calibrate hanging position
        calibrateHangingPosition(normalizedPose, torsoPosition)
        
        // Return smoothed relative torso position
        return torsoPositionSmoother.addSample(torsoPosition)
    }
    
    /**
     * Calculate normalized torso vertical position
     */
    private fun calculateTorsoVerticalPosition(normalizedPose: KeypointNormalizer.NormalizedPose): Float? {
        val leftShoulder = normalizedPose.getLandmark(PoseLandmark.LEFT_SHOULDER) ?: return null
        val rightShoulder = normalizedPose.getLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return null
        val leftHip = normalizedPose.getLandmark(PoseLandmark.LEFT_HIP) ?: return null
        val rightHip = normalizedPose.getLandmark(PoseLandmark.RIGHT_HIP) ?: return null
        
        // Calculate torso center
        val shoulderCenterY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipCenterY = (leftHip.y + rightHip.y) / 2f
        val torsoCenterY = (shoulderCenterY + hipCenterY) / 2f
        
        return KeypointNormalizer.getVerticalPosition(
            KeypointNormalizer.NormalizedLandmark(0f, torsoCenterY, 1f)
        )
    }
    
    /**
     * Auto-calibrate hanging position baseline
     */
    private fun calibrateHangingPosition(
        normalizedPose: KeypointNormalizer.NormalizedPose,
        torsoPosition: Float
    ) {
        val avgElbowAngle = calculateAverageElbowAngle(normalizedPose)
        
        // Look for stable hanging position (extended arms) - Faster calibration
        if (avgElbowAngle != null && avgElbowAngle > armExtensionThreshold) {
            calibrationFrames++
            if (calibrationFrames >= 8) { // Reduced from 15 frames
                hangingTorsoBaseline = torsoPosition
                
                // Estimate bar level from hand positions
                estimateBarLevel(normalizedPose)
                calibrationFrames = 0
            }
        } else {
            calibrationFrames = 0
        }
    }
    
    /**
     * Estimate bar level from hand positions during hanging
     */
    private fun estimateBarLevel(normalizedPose: KeypointNormalizer.NormalizedPose) {
        val leftWrist = normalizedPose.getLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = normalizedPose.getLandmark(PoseLandmark.RIGHT_WRIST)
        
        val handPositions = listOfNotNull(leftWrist, rightWrist)
        if (handPositions.isNotEmpty()) {
            val avgHandY = handPositions.map { it.y }.average().toFloat()
            barLevelEstimate = KeypointNormalizer.getVerticalPosition(
                KeypointNormalizer.NormalizedLandmark(0f, avgHandY, 1f)
            )
        }
    }
    
    
    /**
     * Determine pull-up phase using torso movement and chin position
     */
    override fun determinePhase(pose: Pose, primaryAngle: Float?): ExercisePhase {
        val normalizedPose = lastNormalizedPose
        if (normalizedPose == null || primaryAngle == null) {
            return ExercisePhase.TRANSITIONING
        }
        
        // Multi-signal analysis for robust detection
        val torsoPhase = analyzeTorsoMovement(primaryAngle)
        val chinPhase = analyzeChinPosition(normalizedPose)
        val elbowPhase = analyzeElbowAngles(normalizedPose)
        
        // Combine signals with prioritization
        val combinedPhase = combinePhaseSignals(torsoPhase, chinPhase, elbowPhase)
        
        // Use debounced state machine
        return stateMachine.updateState(combinedPhase)
    }
    
    /**
     * Analyze torso vertical movement relative to hanging baseline
     */
    private fun analyzeTorsoMovement(torsoPosition: Float): ExercisePhase? {
        val baseline = hangingTorsoBaseline ?: return null
        val relativeTorsoPosition = torsoPosition - baseline
        
        return when {
            relativeTorsoPosition > torsoRiseThreshold -> ExercisePhase.UP    // Torso raised
            relativeTorsoPosition < -torsoRiseThreshold/3 -> ExercisePhase.DOWN // Torso lowered (more sensitive)
            else -> ExercisePhase.TRANSITIONING
        }
    }
    
    /**
     * Analyze chin position relative to estimated bar level
     */
    private fun analyzeChinPosition(normalizedPose: KeypointNormalizer.NormalizedPose): ExercisePhase? {
        val nose = normalizedPose.getLandmark(PoseLandmark.NOSE) ?: return null
        val barLevel = barLevelEstimate ?: return null
        
        val chinPosition = KeypointNormalizer.getVerticalPosition(nose)
        val smoothedChinPosition = chinPositionSmoother.addSample(chinPosition)
        val chinToBarDistance = smoothedChinPosition - barLevel
        
        return when {
            chinToBarDistance > -chinBarThreshold -> ExercisePhase.UP    // Chin near/above bar
            chinToBarDistance < -chinBarThreshold * 2 -> ExercisePhase.DOWN // Chin well below bar
            else -> ExercisePhase.TRANSITIONING
        }
    }
    
    /**
     * Analyze elbow angles for arm extension/contraction
     */
    private fun analyzeElbowAngles(normalizedPose: KeypointNormalizer.NormalizedPose): ExercisePhase? {
        val avgElbowAngle = calculateAverageElbowAngle(normalizedPose) ?: return null
        val smoothedAngle = elbowAngleSmoother.addSample(avgElbowAngle)
        
        return when {
            smoothedAngle < armContractionThreshold -> ExercisePhase.UP      // Arms contracted (pulled up)
            smoothedAngle > armExtensionThreshold -> ExercisePhase.DOWN     // Arms extended (hanging)
            else -> ExercisePhase.TRANSITIONING
        }
    }
    
    /**
     * Calculate average elbow angle from normalized pose
     */
    private fun calculateAverageElbowAngle(normalizedPose: KeypointNormalizer.NormalizedPose): Float? {
        val leftAngle = calculateNormalizedElbowAngle(normalizedPose, isLeft = true)
        val rightAngle = calculateNormalizedElbowAngle(normalizedPose, isLeft = false)
        
        val validAngles = listOfNotNull(leftAngle, rightAngle)
        return if (validAngles.isNotEmpty()) {
            validAngles.average().toFloat()
        } else {
            null
        }
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
        
        if (shoulder.confidence < 0.6f || elbow.confidence < 0.6f || wrist.confidence < 0.6f) {
            return null
        }
        
        return KeypointNormalizer.calculateNormalizedAngle(shoulder, elbow, wrist)
    }
    
    /**
     * Combine multiple phase signals with intelligent prioritization
     */
    private fun combinePhaseSignals(
        torsoPhase: ExercisePhase?,
        chinPhase: ExercisePhase?,
        elbowPhase: ExercisePhase?
    ): ExercisePhase {
        val signals = listOfNotNull(torsoPhase, chinPhase, elbowPhase)
        
        if (signals.isEmpty()) {
            return ExercisePhase.TRANSITIONING
        }
        
        // Count votes for each phase
        val upVotes = signals.count { it == ExercisePhase.UP }
        val downVotes = signals.count { it == ExercisePhase.DOWN }
        val transitionVotes = signals.count { it == ExercisePhase.TRANSITIONING }
        
        // Majority vote with preference for definitive phases
        return when {
            upVotes > downVotes && upVotes > transitionVotes -> ExercisePhase.UP
            downVotes > upVotes && downVotes > transitionVotes -> ExercisePhase.DOWN
            else -> ExercisePhase.TRANSITIONING
        }
    }
    
    
    /**
     * Enhanced confidence calculation based on multiple signal quality
     */
    override fun calculateConfidence(pose: Pose, primaryAngle: Float?): Float {
        val normalizedPose = lastNormalizedPose
        if (normalizedPose == null || primaryAngle == null) {
            return 0f
        }
        
        var confidence = 0f
        var factors = 0
        
        // Base confidence from pose normalization quality
        confidence += normalizedPose.confidence
        factors++
        
        // Calibration bonus
        if (hangingTorsoBaseline != null) {
            confidence += 0.3f
            factors++
        }
        
        // Bar level estimation bonus
        if (barLevelEstimate != null) {
            confidence += 0.2f
            factors++
        }
        
        // Key landmark visibility for pull-ups
        val requiredLandmarks = listOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
            PoseLandmark.NOSE
        )
        
        if (KeypointNormalizer.hasSufficientQuality(normalizedPose, requiredLandmarks)) {
            confidence += 0.3f
            factors++
        }
        
        // Multi-signal consistency bonus
        val torsoPhase = analyzeTorsoMovement(primaryAngle)
        val chinPhase = analyzeChinPosition(normalizedPose)
        val elbowPhase = analyzeElbowAngles(normalizedPose)
        
        val definiteSignals = listOfNotNull(torsoPhase, chinPhase, elbowPhase)
            .count { it != ExercisePhase.TRANSITIONING }
        
        if (definiteSignals >= 2) {
            confidence += 0.2f
            factors++
        }
        
        val finalConfidence = if (factors > 0) (confidence / factors).coerceIn(0f, 1f) else 0f
        
        // Update rep counter
        val currentPhase = stateMachine.getCurrentState()
        repCounter.processPhase(currentPhase, finalConfidence)
        
        return finalConfidence
    }
    
    /**
     * Get detection method description
     */
    override fun getDetectionMethod(): String {
        val methods = mutableListOf<String>()
        
        if (hangingTorsoBaseline != null) methods.add("torso_movement")
        if (barLevelEstimate != null) methods.add("chin_to_bar")
        if (lastPrimaryAngle != null) methods.add("elbow_angles")
        
        val baseMethod = if (methods.isNotEmpty()) {
            "multi_signal(${methods.joinToString(",")})"
        } else {
            "basic_elbow"
        }
        
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
     * Get detailed statistics
     */
    fun getDetailedStats(): Map<String, Any> {
        val baseStats = repCounter.getStats()
        return baseStats + mapOf(
            "is_calibrated" to (hangingTorsoBaseline != null),
            "bar_level_estimated" to (barLevelEstimate != null),
            "state_machine_progress" to stateMachine.getTransitionProgress(),
            "is_transitioning" to stateMachine.isTransitioning(),
            "torso_baseline" to (hangingTorsoBaseline ?: "not_set"),
            "bar_level" to (barLevelEstimate ?: "not_set")
        )
    }
    
    /**
     * Reset detector state
     */
    override fun reset() {
        super.reset()
        torsoPositionSmoother.reset()
        chinPositionSmoother.reset()
        elbowAngleSmoother.reset()
        stateMachine.reset()
        repCounter.reset()
        lastNormalizedPose = null
        barLevelEstimate = null
        hangingTorsoBaseline = null
        calibrationFrames = 0
    }
}
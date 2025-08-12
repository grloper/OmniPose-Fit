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
    
    // VIEW MODE detection (front vs back)
    private enum class ViewMode { FRONT, BACK }
    private var viewMode: ViewMode = ViewMode.FRONT
    private var lastViewModeChangeTs = 0L
    private val viewModeMinHoldMs = 1500L

    // Back-view specific baselines
    private var hangingShoulderWristDist: Float? = null
    private var hangingShoulderCenterY: Float? = null

    // Added thresholds tuned for back view
    private val backWristApproachRatio = 0.72f      // wrist-to-shoulder distance ratio when pulled up
    private val backShoulderLiftThreshold = 0.06f   // normalized upward movement of shoulder center
    
    /**
     * Calculate primary signal: torso vertical position for pull-up detection
     */
    override fun calculatePrimaryAngle(pose: Pose): Float? {
        val normalizedPose = KeypointNormalizer.normalizePose(pose) ?: return null
        lastNormalizedPose = normalizedPose

        // Detect current viewing mode
        detectViewMode(normalizedPose)

        // Torso vertical (used as primary numeric signal)
        val torsoPosition = calculateTorsoVerticalPosition(normalizedPose) ?: return null
        calibrateHangingPosition(normalizedPose, torsoPosition) // now also sets back-view baselines

        return torsoPositionSmoother.addSample(torsoPosition)
    }

    // Detect view mode by facial landmark confidence vs torso landmarks
    private fun detectViewMode(norm: KeypointNormalizer.NormalizedPose) {
        val faceIds = listOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_EYE, PoseLandmark.RIGHT_EYE,
            PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR
        )
        val torsoIds = listOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP
        )
        val faceConf = faceIds.mapNotNull { norm.getLandmark(it)?.confidence }.ifEmpty { listOf(0f) }.average()
        val torsoConf = torsoIds.mapNotNull { norm.getLandmark(it)?.confidence }.ifEmpty { listOf(0f) }.average()
        val now = System.currentTimeMillis()
        val proposed = if (faceConf < 0.35 && torsoConf > 0.55) ViewMode.BACK else ViewMode.FRONT
        if (proposed != viewMode && now - lastViewModeChangeTs > viewModeMinHoldMs) {
            viewMode = proposed
            lastViewModeChangeTs = now
            // Reset calibration for new orientation
            hangingTorsoBaseline = null
            barLevelEstimate = null
            hangingShoulderWristDist = null
            hangingShoulderCenterY = null
            calibrationFrames = 0
        }
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
        val wrists = listOfNotNull(
            normalizedPose.getLandmark(PoseLandmark.LEFT_WRIST),
            normalizedPose.getLandmark(PoseLandmark.RIGHT_WRIST)
        )
        val shoulders = listOfNotNull(
            normalizedPose.getLandmark(PoseLandmark.LEFT_SHOULDER),
            normalizedPose.getLandmark(PoseLandmark.RIGHT_SHOULDER)
        )
        if (avgElbowAngle != null && avgElbowAngle > armExtensionThreshold && shoulders.size == 2 && wrists.size >= 1) {
            calibrationFrames++
            if (calibrationFrames >= 8) {
                hangingTorsoBaseline = torsoPosition
                // Front view: estimate bar from wrists (existing)
                if (viewMode == ViewMode.FRONT) {
                    estimateBarLevel(normalizedPose)
                } else {
                    // Back view: store wrist-shoulder vertical distance baseline
                    val shoulderCenterY = (shoulders[0].y + shoulders[1].y) / 2f
                    val avgWristY = wrists.map { it.y }.average().toFloat()
                    hangingShoulderWristDist = (avgWristY - shoulderCenterY).coerceAtLeast(0.01f)
                    hangingShoulderCenterY = shoulderCenterY
                }
                calibrationFrames = 0
            }
        } else calibrationFrames = 0
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
        val norm = lastNormalizedPose ?: return ExercisePhase.TRANSITIONING
        if (primaryAngle == null) return ExercisePhase.TRANSITIONING

        val torsoPhase = analyzeTorsoMovement(primaryAngle)
        val elbowPhase = analyzeElbowAngles(norm)

        val auxPhase =
            if (viewMode == ViewMode.FRONT) analyzeChinPosition(norm)
            else analyzeBackViewShoulderWrist(norm) // back-view alternative to chin/bar

        val combined = combinePhaseSignals(torsoPhase, auxPhase, elbowPhase)
        return stateMachine.updateState(combined)
    }

    // Back view auxiliary phase using wrist-to-shoulder distance and shoulder elevation
    private fun analyzeBackViewShoulderWrist(norm: KeypointNormalizer.NormalizedPose): ExercisePhase? {
        val leftSh = norm.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightSh = norm.getLandmark(PoseLandmark.RIGHT_SHOULDER)
        val wrists = listOfNotNull(
            norm.getLandmark(PoseLandmark.LEFT_WRIST),
            norm.getLandmark(PoseLandmark.RIGHT_WRIST)
        )
        if (leftSh == null || rightSh == null || wrists.isEmpty()) return null
        val shoulderCenterY = (leftSh.y + rightSh.y) / 2f
        val avgWristY = wrists.map { it.y }.average().toFloat()
        val baselineDist = hangingShoulderWristDist ?: return null
        val distNow = (avgWristY - shoulderCenterY).coerceAtLeast(0.001f)
        val ratio = distNow / baselineDist // < 1 when pulling up
        val shoulderLift = if (hangingShoulderCenterY != null)
            (hangingShoulderCenterY!! - shoulderCenterY) else 0f // positive when shoulders rise

        return when {
            ratio < backWristApproachRatio && shoulderLift > backShoulderLiftThreshold -> ExercisePhase.UP
            ratio > 0.95f -> ExercisePhase.DOWN
            else -> ExercisePhase.TRANSITIONING
        }
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
        val norm = lastNormalizedPose
        if (norm == null || primaryAngle == null) return 0f

        var conf = 0f
        var factors = 0

        conf += norm.confidence; factors++

        if (hangingTorsoBaseline != null) { conf += 0.25f; factors++ }

        if (viewMode == ViewMode.FRONT && barLevelEstimate != null) {
            conf += 0.2f; factors++
        }
        if (viewMode == ViewMode.BACK && hangingShoulderWristDist != null) {
            conf += 0.2f; factors++
        }

        val required = mutableListOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST
        )
        if (viewMode == ViewMode.FRONT) required += PoseLandmark.NOSE
        if (KeypointNormalizer.hasSufficientQuality(norm, required)) {
            conf += 0.2f; factors++
        }

        // Multi-signal consistency
        val torsoPhase = analyzeTorsoMovement(primaryAngle)
        val auxPhase = if (viewMode == ViewMode.FRONT) analyzeChinPosition(norm) else analyzeBackViewShoulderWrist(norm)
        val elbowPhase = analyzeElbowAngles(norm)
        val definite = listOfNotNull(torsoPhase, auxPhase, elbowPhase).count { it != ExercisePhase.TRANSITIONING }
        if (definite >= 2) { conf += 0.15f; factors++ }

        val final = if (factors > 0) (conf / factors).coerceIn(0f, 1f) else 0f

        repCounter.processPhase(stateMachine.getCurrentState(), final)
        return final
    }
    
    /**
     * Get detection method description
     */
    override fun getDetectionMethod(): String {
        val modes = mutableListOf<String>()
        modes += "view=${viewMode.name.lowercase()}"
        if (hangingTorsoBaseline != null) modes += "torso"
        if (viewMode == ViewMode.FRONT && barLevelEstimate != null) modes += "chin_bar"
        if (viewMode == ViewMode.BACK && hangingShoulderWristDist != null) modes += "shoulder_wrist"
        if (lastPrimaryAngle != null) modes += "torso_pos_primary"
        if (repCounter.getUncertainReps() > 0) modes += "uncertain_reps"
        return modes.joinToString("+")
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
        viewMode = ViewMode.FRONT
        hangingShoulderWristDist = null
        hangingShoulderCenterY = null
    }
}
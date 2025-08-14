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
 * Enhanced push-up detector with ground-level detection and context-aware angle analysis
 * Specifically designed for low-angle camera positioning with hand/foot level analysis
 */
class PushUpDetector : BaseExerciseDetector(ExerciseType.PUSH_UP) {
    
    // GROUND-LEVEL DETECTION: Context-aware thresholds based on detected body positioning
    private var upElbowThreshold = 140f    // Arms extended (higher for ground position)
    private var downElbowThreshold = 90f   // Arms bent (90+ degrees as requested)
    private val torsoParallelThreshold = 35f // Max degrees from horizontal for good form (very forgiving)
    
    // Ground-level detection parameters - Named constants for clarity
    private var groundLevel: Float? = null
    private var handLevel: Float? = null
    private var bodyBaselineY: Float? = null
    private val groundDetectionFrames = 15  // Frames to establish ground level
    private var groundCalibrationCount = 0
    private var isGroundLevelEstablished = false
    
    // Ground detection thresholds - Named constants
    private val handGroundProximityThreshold = 0.15f  // Hands close to ground level
    private val handStabilityVarianceThreshold = 0.01f  // Stable hand positioning
    private val handShoulderRatioMin = 0.8f  // Plank position indicator
    private val handShoulderRatioMax = 1.2f  // Plank position indicator
    private val groundContactConfidenceThreshold = 0.6f  // Ground contact confidence
    
    // Ground-level tracking for multi-signal detection with optimized circular buffers
    private val levelHistorySize = 10
    private var recentHandLevels = FloatArray(levelHistorySize) { 0f }
    private var recentShoulderLevels = FloatArray(levelHistorySize) { 0f }
    private var levelHistoryIndex = 0
    private var levelHistoryCount = 0
    
    // Context-aware detection states
    private var isInGroundPosition = false
    private var groundContactConfidence = 0f
    private var lastGroundContactFrame = 0L
    private var groundDriftProtection = 0f  // Prevent ground level drift
    
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
     * Calculate primary angle with ground-level detection and graceful degradation
     * Implements hand/foot level analysis as requested by @grloper
     */
    override fun calculatePrimaryAngle(pose: Pose): Float? {
        val normalizedPose = KeypointNormalizer.normalizePose(pose) ?: return null
        lastNormalizedPose = normalizedPose
        
        // STEP 1: Establish ground level reference using hand/foot positioning
        establishGroundLevel(normalizedPose)
        
        // STEP 2: Detect current ground contact state with error handling
        val groundContactState = try {
            detectGroundContact(normalizedPose)
        } catch (e: Exception) {
            debugInfo["ground_detection_error"] = e.message ?: "unknown"
            GroundContactState(false, 0f, null, null)  // Graceful degradation
        }
        
        isInGroundPosition = groundContactState.isInContact
        groundContactConfidence = groundContactState.confidence
        
        // STEP 3: Calculate context-aware elbow angles with fallback
        var leftAngle: Float? = null
        var rightAngle: Float? = null
        
        try {
            leftAngle = calculateContextualElbowAngle(normalizedPose, isLeft = true)
            rightAngle = calculateContextualElbowAngle(normalizedPose, isLeft = false)
        } catch (e: Exception) {
            debugInfo["angle_calculation_error"] = e.message ?: "unknown"
            // Fallback to standard angle calculation
            leftAngle = calculateNormalizedElbowAngle(normalizedPose, isLeft = true)
            rightAngle = calculateNormalizedElbowAngle(normalizedPose, isLeft = false)
        }
        
        val validAngles = listOfNotNull(leftAngle, rightAngle)
        var averageAngle: Float? = null
        
        // STEP 4: Primary detection method - context-aware elbow angles
        if (validAngles.isNotEmpty()) {
            averageAngle = validAngles.average().toFloat()
            
            // Apply ground-position context adjustment with confidence gating
            if (isInGroundPosition && groundContactConfidence > 0.6f && isGroundLevelEstablished) {
                try {
                    averageAngle = adjustAngleForGroundPosition(averageAngle, groundContactState)
                } catch (e: Exception) {
                    debugInfo["ground_adjustment_error"] = e.message ?: "unknown"
                    // Continue with unadjusted angle
                }
            }
        } else {
            // STEP 5: Enhanced fallback method with error recovery
            averageAngle = try {
                if (isGroundLevelEstablished) {
                    calculateGroundAwareFallbackAngle(normalizedPose)
                } else {
                    calculateFallbackAngle(normalizedPose)  // Standard fallback
                }
            } catch (e: Exception) {
                debugInfo["fallback_calculation_error"] = e.message ?: "unknown"
                calculateFallbackAngle(normalizedPose)  // Last resort
            }
        }
        
        if (averageAngle == null) {
            debugInfo["detection_failure_reason"] = "no_valid_angles"
            return null
        }
        
        // STEP 6: Adaptive calibration with ground-level awareness and bounds checking
        if (!isCalibrated && calibrationFrames < calibrationPeriod) {
            // Only calibrate when we have stable ground detection OR no ground detection needed
            val canCalibrate = (isGroundLevelEstablished && groundContactConfidence > 0.5f) || 
                              (!isGroundLevelEstablished && groundCalibrationCount > 5)
            
            if (canCalibrate && averageAngle > 30f && averageAngle < 180f) {  // Sanity bounds
                maxObservedAngle = maxOf(maxObservedAngle, averageAngle)
                minObservedAngle = minOf(minObservedAngle, averageAngle)
                calibrationFrames++
                
                if (calibrationFrames >= calibrationPeriod) {
                    // Adapt thresholds based on observed range and ground position
                    val range = maxObservedAngle - minObservedAngle
                    if (range > 25f && range < 120f) { // Reasonable range bounds
                        // Use higher thresholds for ground position (90+ degrees as requested)
                        val baseUpThreshold = if (isGroundLevelEstablished) 140f else 120f
                        val baseDownThreshold = if (isGroundLevelEstablished) 90f else 80f
                        
                        upElbowThreshold = maxOf(baseUpThreshold, maxObservedAngle - (range * 0.15f))
                        downElbowThreshold = maxOf(baseDownThreshold, minObservedAngle + (range * 0.25f))
                        
                        // Ensure minimum thresholds for ground position
                        if (isGroundLevelEstablished) {
                            upElbowThreshold = maxOf(upElbowThreshold, 135f)
                            downElbowThreshold = maxOf(downElbowThreshold, 90f)
                        }
                        
                        isCalibrated = true
                        debugInfo.apply {
                            put("calibrated_up_threshold", upElbowThreshold)
                            put("calibrated_down_threshold", downElbowThreshold)
                            put("observed_range", range)
                            put("ground_calibration", isGroundLevelEstablished)
                            put("calibration_method", if (isGroundLevelEstablished) "ground_aware" else "standard")
                        }
                    } else {
                        debugInfo["calibration_rejected_range"] = range
                    }
                }
            }
        }
        
        // Update comprehensive debug info
        debugInfo.apply {
            put("left_elbow_angle", leftAngle ?: "null")
            put("right_elbow_angle", rightAngle ?: "null")
            put("average_angle", averageAngle)
            put("valid_angles_count", validAngles.size)
            put("calibration_progress", calibrationFrames)
            put("is_calibrated", isCalibrated)
            put("using_fallback", validAngles.isEmpty())
            put("ground_level", groundLevel ?: "establishing")
            put("is_in_ground_position", isInGroundPosition)
            put("ground_contact_confidence", groundContactConfidence)
            put("ground_level_established", isGroundLevelEstablished)
            put("detection_method", when {
                isInGroundPosition && isGroundLevelEstablished -> "ground_aware_primary"
                isGroundLevelEstablished -> "ground_calibrated"
                else -> "standard"
            })
            put("angle_bounds_valid", averageAngle > 30f && averageAngle < 180f)
        }
        
        // Apply temporal smoothing with bounds checking
        val smoothedAngle = try {
            angleSmoother.addSample(averageAngle).coerceIn(30f, 180f)
        } catch (e: Exception) {
            debugInfo["smoothing_error"] = e.message ?: "unknown"
            averageAngle  // Use raw angle if smoothing fails
        }
        
        debugInfo["smoothed_angle"] = smoothedAngle
        
        return smoothedAngle
    }
    
    /**
     * Establish ground level reference using hand and foot positioning with outlier rejection
     * Key innovation: Use Y-coordinates to determine when person is "on ground"
     */
    private fun establishGroundLevel(normalizedPose: KeypointNormalizer.NormalizedPose) {
        if (isGroundLevelEstablished) {
            // Protect against ground level drift during exercise
            return
        }
        
        // Collect hand and foot Y-coordinates with confidence filtering
        val landmarks = listOfNotNull(
            normalizedPose.getLandmark(PoseLandmark.LEFT_WRIST),
            normalizedPose.getLandmark(PoseLandmark.RIGHT_WRIST),
            normalizedPose.getLandmark(PoseLandmark.LEFT_ANKLE),
            normalizedPose.getLandmark(PoseLandmark.RIGHT_ANKLE)
        ).filter { it.confidence > 0.4f }  // Higher confidence requirement
        
        if (landmarks.size >= 2) {
            val yValues = landmarks.map { it.y }
            val currentAvgY = yValues.average().toFloat()
            
            // Outlier rejection: Only accept values within reasonable range
            val isReasonableValue = if (groundLevel != null) {
                abs(currentAvgY - groundLevel!!) < 0.3f  // Drift protection
            } else {
                true
            }
            
            if (isReasonableValue && groundCalibrationCount < groundDetectionFrames) {
                if (groundLevel == null) {
                    groundLevel = currentAvgY
                    groundDriftProtection = currentAvgY
                } else {
                    // Robust averaging with outlier protection
                    val weight = 0.15f  // Slower adaptation for stability
                    groundLevel = groundLevel!! * (1f - weight) + currentAvgY * weight
                }
                groundCalibrationCount++
                
                debugInfo["ground_calibration_progress"] = "$groundCalibrationCount/$groundDetectionFrames"
                debugInfo["current_ground_estimate"] = groundLevel
            } else if (!isReasonableValue) {
                debugInfo["ground_outlier_rejected"] = currentAvgY
            }
            
            if (groundCalibrationCount >= groundDetectionFrames) {
                isGroundLevelEstablished = true
                groundDriftProtection = groundLevel!!
                debugInfo["ground_level_established"] = true
                debugInfo["final_ground_level"] = groundLevel
                debugInfo["ground_drift_protection"] = groundDriftProtection
            }
        }
    }
    
    /**
     * Ground contact detection state
     */
    data class GroundContactState(
        val isInContact: Boolean,
        val confidence: Float,
        val handDistanceFromGround: Float?,
        val bodyDistanceFromGround: Float?
    )
    
    /**
     * Detect if person is in ground contact position using optimized hand/foot level analysis
     * Core logic: Analyze relative positioning to determine "on ground" state
     */
    private fun detectGroundContact(normalizedPose: KeypointNormalizer.NormalizedPose): GroundContactState {
        if (!isGroundLevelEstablished || groundLevel == null) {
            return GroundContactState(false, 0f, null, null)
        }
        
        // Get hand positions with confidence filtering
        val leftHand = normalizedPose.getLandmark(PoseLandmark.LEFT_WRIST)
        val rightHand = normalizedPose.getLandmark(PoseLandmark.RIGHT_WRIST)
        val validHands = listOfNotNull(leftHand, rightHand).filter { it.confidence > 0.3f }
        
        // Get body center (shoulder average)
        val leftShoulder = normalizedPose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = normalizedPose.getLandmark(PoseLandmark.RIGHT_SHOULDER)
        
        if (validHands.isEmpty() || leftShoulder == null || rightShoulder == null) {
            return GroundContactState(false, 0f, null, null)
        }
        
        val avgHandY = validHands.map { it.y }.average().toFloat()
        val avgShoulderY = (leftShoulder.y + rightShoulder.y) / 2f
        
        // Optimized circular buffer tracking for stability
        recentHandLevels[levelHistoryIndex] = avgHandY
        recentShoulderLevels[levelHistoryIndex] = avgShoulderY
        levelHistoryIndex = (levelHistoryIndex + 1) % levelHistorySize
        levelHistoryCount = minOf(levelHistoryCount + 1, levelHistorySize)
        
        // Calculate distances from ground level
        val handDistanceFromGround = abs(avgHandY - groundLevel!!)
        val bodyDistanceFromGround = abs(avgShoulderY - groundLevel!!)
        
        // Optimized stability calculation using variance
        val handStability = if (levelHistoryCount >= 3) {
            val validCount = minOf(levelHistoryCount, levelHistorySize)
            val mean = recentHandLevels.take(validCount).average().toFloat()
            val variance = recentHandLevels.take(validCount).map { (it - mean) * (it - mean) }.average().toFloat()
            variance < handStabilityVarianceThreshold
        } else false
        
        // Ground contact detection logic with named constants
        val handNearGround = handDistanceFromGround < handGroundProximityThreshold
        val handShoulderRatio = if (avgShoulderY != 0f) avgHandY / avgShoulderY else 1f
        val inPlankPosition = handShoulderRatio > handShoulderRatioMin && handShoulderRatio < handShoulderRatioMax
        
        // Enhanced ground contact confidence calculation
        var confidence = 0f
        
        // Base confidence from hand proximity to ground
        if (handNearGround) confidence += 0.4f
        
        // Stability bonus (crucial for ground detection)
        if (handStability) confidence += 0.3f
        
        // Plank position indicator (hands and shoulders at similar level)
        if (inPlankPosition) confidence += 0.3f
        
        val isInContact = confidence > groundContactConfidenceThreshold
        
        // Comprehensive debug information
        debugInfo.apply {
            put("hand_distance_from_ground", handDistanceFromGround)
            put("body_distance_from_ground", bodyDistanceFromGround)
            put("hand_near_ground", handNearGround)
            put("hand_stability", handStability)
            put("hand_shoulder_ratio", handShoulderRatio)
            put("in_plank_position", inPlankPosition)
            put("level_history_count", levelHistoryCount)
        }
        
        return GroundContactState(isInContact, confidence, handDistanceFromGround, bodyDistanceFromGround)
    }
    
    /**
     * Calculate context-aware elbow angle based on ground position
     * Adjusts interpretation based on whether person is in ground contact
     */
    private fun calculateContextualElbowAngle(
        normalizedPose: KeypointNormalizer.NormalizedPose,
        isLeft: Boolean
    ): Float? {
        val shoulderType = if (isLeft) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER
        val elbowType = if (isLeft) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
        val wristType = if (isLeft) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
        
        val shoulder = normalizedPose.getLandmark(shoulderType) ?: return null
        val elbow = normalizedPose.getLandmark(elbowType) ?: return null
        val wrist = normalizedPose.getLandmark(wristType) ?: return null
        
        // Lower confidence requirements for ground position detection
        if (shoulder.confidence < 0.3f || elbow.confidence < 0.3f || wrist.confidence < 0.3f) {
            return null
        }
        
        val baseAngle = KeypointNormalizer.calculateNormalizedAngle(shoulder, elbow, wrist)
        
        // Context-aware adjustment based on ground position
        return if (isInGroundPosition && groundContactConfidence > 0.6f) {
            // When in ground position, angles tend to be measured differently
            // Apply contextual adjustment to make angles more meaningful
            val adjustmentFactor = 1.1f  // Slight increase for ground position context
            (baseAngle * adjustmentFactor).coerceIn(45f, 180f)
        } else {
            baseAngle
        }
    }
    
    /**
     * Adjust angle interpretation for ground position context
     * Key: Higher thresholds (90+ degrees) when person is on ground
     */
    private fun adjustAngleForGroundPosition(
        angle: Float, 
        groundState: GroundContactState
    ): Float {
        // When clearly in ground position with high confidence
        if (groundState.confidence > 0.8f) {
            // Map the angle to a more appropriate range for ground position
            // The closer to ground, the more we adjust thresholds upward
            val groundAdjustment = 1.0f + (groundState.confidence * 0.2f)
            return (angle * groundAdjustment).coerceIn(60f, 180f)
        }
        return angle
    }
    
    /**
     * Enhanced fallback method with ground-level awareness
     */
    private fun calculateGroundAwareFallbackAngle(normalizedPose: KeypointNormalizer.NormalizedPose): Float? {
        val standardFallback = calculateFallbackAngle(normalizedPose)
        
        if (standardFallback == null || !isInGroundPosition) {
            return standardFallback
        }
        
        // Enhanced fallback for ground position using hand-shoulder distance
        val leftShoulder = normalizedPose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = normalizedPose.getLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftWrist = normalizedPose.getLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = normalizedPose.getLandmark(PoseLandmark.RIGHT_WRIST)
        
        if (leftShoulder != null && rightShoulder != null && 
            leftWrist != null && rightWrist != null) {
            
            val shoulderCenterY = (leftShoulder.y + rightShoulder.y) / 2f
            val handCenterY = (leftWrist.y + rightWrist.y) / 2f
            val shoulderHandDelta = abs(shoulderCenterY - handCenterY)
            
            // When hands and shoulders are at similar level (plank position)
            // Use distance variation to infer arm extension
            val pseudoAngle = when {
                shoulderHandDelta < 0.05f -> 160f  // Very close = extended
                shoulderHandDelta < 0.10f -> 130f  // Moderately close
                shoulderHandDelta < 0.15f -> 100f  // Further apart = bent
                else -> 80f  // Very far apart = very bent
            }
            
            debugInfo["ground_aware_fallback_delta"] = shoulderHandDelta
            debugInfo["ground_aware_fallback_angle"] = pseudoAngle
            
            return pseudoAngle
        }
        
        return standardFallback
    }
    
    /**
     * Calculate normalized elbow angle (legacy method for fallback)
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
        
        // Standard confidence requirements for fallback
        if (shoulder.confidence < 0.4f || elbow.confidence < 0.4f || wrist.confidence < 0.4f) {
            return null
        }
        
        return KeypointNormalizer.calculateNormalizedAngle(shoulder, elbow, wrist)
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
     * Determine push-up phase using ground-level enhanced detection logic
     * Uses context-aware thresholds based on ground contact state
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
        
        // GROUND-AWARE PHASE DETECTION: Use dynamic thresholds based on ground contact
        val effectiveUpThreshold = if (isInGroundPosition && groundContactConfidence > 0.7f) {
            // When clearly in ground position, use higher thresholds (90+ degrees as requested)
            maxOf(upElbowThreshold, 135f)  // Ensure minimum 135° for UP in ground position
        } else {
            upElbowThreshold
        }
        
        val effectiveDownThreshold = if (isInGroundPosition && groundContactConfidence > 0.7f) {
            // When clearly in ground position, use 90+ degrees for DOWN as requested
            maxOf(downElbowThreshold, 90f)  // Ensure minimum 90° for DOWN in ground position
        } else {
            downElbowThreshold
        }
        
        // Enhanced phase detection with ground-level context
        val anglePhase = when {
            primaryAngle < effectiveDownThreshold -> ExercisePhase.DOWN
            primaryAngle > effectiveUpThreshold -> ExercisePhase.UP
            else -> ExercisePhase.TRANSITIONING
        }
        
        // Update comprehensive debug info
        debugInfo.apply {
            put("primary_angle", primaryAngle)
            put("base_up_threshold", upElbowThreshold)
            put("base_down_threshold", downElbowThreshold)
            put("effective_up_threshold", effectiveUpThreshold)
            put("effective_down_threshold", effectiveDownThreshold)
            put("ground_adjusted_thresholds", isInGroundPosition && groundContactConfidence > 0.7f)
            put("raw_phase", anglePhase.name)
        }
        
        // Validate with enhanced torso form analysis
        val torsoPhase = validateWithGroundAwareTorsoForm(lastNormalizedPose, anglePhase)
        debugInfo["torso_validated_phase"] = torsoPhase.name
        
        // Use debounced state machine
        val finalPhase = stateMachine.updateState(torsoPhase)
        debugInfo.apply {
            put("final_phase", finalPhase.name)
            put("state_machine_transitioning", stateMachine.isTransitioning())
        }
        
        return finalPhase
    }
    
    
    /**
     * Validate detected phase with ground-aware torso form analysis
     * Enhanced validation that considers ground position context
     */
    private fun validateWithGroundAwareTorsoForm(
        normalizedPose: KeypointNormalizer.NormalizedPose?,
        anglePhase: ExercisePhase
    ): ExercisePhase {
        if (normalizedPose == null) return anglePhase
        
        val torsoPitch = calculateTorsoPitch(normalizedPose)
        if (torsoPitch == null) return anglePhase
        
        val smoothedPitch = torsoPitchSmoother.addSample(torsoPitch)
        
        // Ground-aware torso validation
        val effectiveTorsoThreshold = if (isInGroundPosition && groundContactConfidence > 0.6f) {
            // More forgiving torso requirements when clearly in ground position
            torsoParallelThreshold + 15f  // Additional 15° tolerance for ground position
        } else {
            torsoParallelThreshold
        }
        
        // Update debug info
        debugInfo.apply {
            put("torso_pitch_raw", torsoPitch)
            put("torso_pitch_smoothed", smoothedPitch)
            put("torso_threshold_base", torsoParallelThreshold)
            put("torso_threshold_effective", effectiveTorsoThreshold)
            put("ground_torso_adjustment", isInGroundPosition && groundContactConfidence > 0.6f)
        }
        
        // For good form push-ups, torso should remain relatively parallel to ground
        // Extra forgiving for ground position detection
        val isGoodForm = abs(smoothedPitch) < effectiveTorsoThreshold
        debugInfo["torso_good_form"] = isGoodForm
        
        // Only reject DOWN phase if form is really bad - very forgiving for ground position
        val rejectionThreshold = if (isInGroundPosition) 65f else 50f
        val shouldReject = !isGoodForm && anglePhase == ExercisePhase.DOWN && abs(smoothedPitch) > rejectionThreshold
        debugInfo["torso_rejected"] = shouldReject
        debugInfo["torso_rejection_threshold"] = rejectionThreshold
        
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
     * Reset detector state including ground-level detection components
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
        
        // Reset standard calibration
        calibrationFrames = 0
        maxObservedAngle = 0f
        minObservedAngle = 180f
        isCalibrated = false
        upElbowThreshold = 140f // Reset to ground-aware defaults
        downElbowThreshold = 90f // 90+ degrees as requested
        
        // Reset ground-level detection state
        groundLevel = null
        handLevel = null
        bodyBaselineY = null
        groundCalibrationCount = 0
        isGroundLevelEstablished = false
        isInGroundPosition = false
        groundContactConfidence = 0f
        lastGroundContactFrame = 0L
        groundDriftProtection = 0f
        
        // Reset optimized circular buffer tracking
        recentHandLevels.fill(0f)
        recentShoulderLevels.fill(0f)
        levelHistoryIndex = 0
        levelHistoryCount = 0
    }
}
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
 * Camera angle classification for adaptive detection
 */
enum class CameraAngle {
    UNKNOWN,
    HIGH_ANGLE,      // Camera positioned above person (normal setup)
    MID_ANGLE,       // Camera at moderate angle
    LOW_ANGLE,       // Camera positioned at ground level (phone on ground against wall)
    GROUND_LEVEL     // Camera is essentially at the same level as the person
}

/**
 * Enhanced push-up detector with camera angle adaptation and movement-based detection
 * Specifically designed to work from ANY camera angle including ground-level positioning
 */
class PushUpDetector : BaseExerciseDetector(ExerciseType.PUSH_UP) {
    
    // ADAPTIVE DETECTION: Context-aware thresholds based on detected camera angle
    private var upElbowThreshold = 140f    // Arms extended
    private var downElbowThreshold = 90f   // Arms bent
    private val torsoParallelThreshold = 35f // Max degrees from horizontal for good form (very forgiving)
    
    // Camera angle detection parameters
    private var detectedCameraAngle: CameraAngle = CameraAngle.UNKNOWN
    private var cameraAngleConfidence = 0f
    private val cameraAngleFrames = 10  // Frames to establish camera angle
    private var cameraAngleCalibrationCount = 0
    private var isCameraAngleEstablished = false
    
    // Movement-based detection for low-angle cameras
    private var isUsingMovementDetection = false
    private val movementHistorySize = 8
    private var shoulderDistanceHistory = FloatArray(movementHistorySize) { 0f }
    private var wristDistanceHistory = FloatArray(movementHistorySize) { 0f }
    private var movementHistoryIndex = 0
    private var movementHistoryCount = 0
    
    // Ground-level detection parameters (legacy for high-angle cameras)
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
     * Calculate primary angle with camera angle adaptation and movement-based detection
     * CORE INNOVATION: Adapts detection method based on camera positioning
     */
    override fun calculatePrimaryAngle(pose: Pose): Float? {
        val normalizedPose = KeypointNormalizer.normalizePose(pose) ?: return null
        lastNormalizedPose = normalizedPose
        
        // STEP 1: Detect camera angle if not yet established
        if (!isCameraAngleEstablished) {
            detectCameraAngle(normalizedPose)
        }
        
        // STEP 2: Choose detection method based on camera angle
        val primaryAngle = when (detectedCameraAngle) {
            CameraAngle.LOW_ANGLE, CameraAngle.GROUND_LEVEL -> {
                // Use movement-based detection for low-angle cameras
                calculateMovementBasedAngle(normalizedPose)
            }
            CameraAngle.HIGH_ANGLE, CameraAngle.MID_ANGLE -> {
                // Use traditional ground-level detection for higher cameras
                calculateGroundAwareAngle(normalizedPose)
            }
            CameraAngle.UNKNOWN -> {
                // Use hybrid approach until camera angle is determined
                calculateHybridAngle(normalizedPose)
            }
        }
        
        if (primaryAngle == null) {
            debugInfo["detection_failure_reason"] = "no_valid_angles_for_camera_angle_${detectedCameraAngle.name}"
            return null
        }
        
        // STEP 3: Adaptive calibration based on camera angle
        if (!isCalibrated && calibrationFrames < calibrationPeriod) {
            val canCalibrate = when (detectedCameraAngle) {
                CameraAngle.LOW_ANGLE, CameraAngle.GROUND_LEVEL -> {
                    // For low-angle cameras, calibrate based on movement patterns
                    movementHistoryCount >= 5 && primaryAngle > 30f && primaryAngle < 180f
                }
                else -> {
                    // For higher cameras, use ground detection or basic calibration
                    (isGroundLevelEstablished && groundContactConfidence > 0.5f) || 
                    (!isGroundLevelEstablished && groundCalibrationCount > 5)
                }
            }
            
            if (canCalibrate && primaryAngle > 30f && primaryAngle < 180f) {
                maxObservedAngle = maxOf(maxObservedAngle, primaryAngle)
                minObservedAngle = minOf(minObservedAngle, primaryAngle)
                calibrationFrames++
                
                if (calibrationFrames >= calibrationPeriod) {
                    adaptThresholdsForCameraAngle()
                    isCalibrated = true
                }
            }
        }
        
        // Update comprehensive debug info
        debugInfo.apply {
            put("camera_angle", detectedCameraAngle.name)
            put("camera_angle_confidence", cameraAngleConfidence)
            put("using_movement_detection", isUsingMovementDetection)
            put("primary_angle", primaryAngle)
            put("calibration_progress", calibrationFrames)
            put("is_calibrated", isCalibrated)
            put("detection_method", when (detectedCameraAngle) {
                CameraAngle.LOW_ANGLE, CameraAngle.GROUND_LEVEL -> "movement_based"
                CameraAngle.HIGH_ANGLE, CameraAngle.MID_ANGLE -> "ground_aware"
                CameraAngle.UNKNOWN -> "hybrid"
            })
        }
        
        // Apply temporal smoothing with bounds checking
        val smoothedAngle = try {
            angleSmoother.addSample(primaryAngle).coerceIn(30f, 180f)
        } catch (e: Exception) {
            debugInfo["smoothing_error"] = e.message ?: "unknown"
            primaryAngle  // Use raw angle if smoothing fails
        }
        
        debugInfo["smoothed_angle"] = smoothedAngle
        
        return smoothedAngle
    }
    
    /**
     * Detect camera angle based on pose landmark patterns
     * INNOVATION: Determines if camera is at ground level by analyzing body proportions
     */
    private fun detectCameraAngle(normalizedPose: KeypointNormalizer.NormalizedPose) {
        if (isCameraAngleEstablished) return
        
        // Get key landmarks for camera angle analysis
        val leftShoulder = normalizedPose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = normalizedPose.getLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = normalizedPose.getLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = normalizedPose.getLandmark(PoseLandmark.RIGHT_HIP)
        val leftWrist = normalizedPose.getLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = normalizedPose.getLandmark(PoseLandmark.RIGHT_WRIST)
        val nose = normalizedPose.getLandmark(PoseLandmark.NOSE)
        
        if (leftShoulder == null || rightShoulder == null || leftHip == null || rightHip == null) {
            return
        }
        
        // Calculate body proportions and orientations
        val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipY = (leftHip.y + rightHip.y) / 2f
        val shoulderX = (leftShoulder.x + rightShoulder.x) / 2f
        val hipX = (leftHip.x + rightHip.x) / 2f
        
        // Analyze torso orientation relative to camera
        val torsoHeight = abs(shoulderY - hipY)
        val torsoWidth = abs(shoulderX - hipX)
        val torsoAspectRatio = if (torsoHeight > 0f) torsoWidth / torsoHeight else 1f
        
        // Analyze head position relative to body
        var headToBodyRatio = 1f
        if (nose != null && leftWrist != null && rightWrist != null) {
            val avgWristY = (leftWrist.y + rightWrist.y) / 2f
            val headWristDistance = abs(nose.y - avgWristY)
            val bodyHeight = abs(shoulderY - hipY)
            headToBodyRatio = if (bodyHeight > 0f) headWristDistance / bodyHeight else 1f
        }
        
        // Calculate confidence indicators for camera angle
        var lowAngleIndicators = 0f
        var totalIndicators = 0f
        
        // Indicator 1: Torso appears very wide (camera looking up from below)
        if (torsoAspectRatio > 1.5f) {
            lowAngleIndicators += 0.3f
        }
        totalIndicators += 0.3f
        
        // Indicator 2: Head appears very close to hands (foreshortening effect)
        if (headToBodyRatio < 0.8f) {
            lowAngleIndicators += 0.25f
        }
        totalIndicators += 0.25f
        
        // Indicator 3: Overall body appears compressed vertically
        val expectedTorsoHeight = 0.3f // Normal torso height ratio
        if (torsoHeight < expectedTorsoHeight * 0.7f) {
            lowAngleIndicators += 0.25f
        }
        totalIndicators += 0.25f
        
        // Indicator 4: Shoulders appear above hips (camera looking up)
        if (shoulderY > hipY + 0.05f) { // Y increases downward in image coordinates
            lowAngleIndicators += 0.2f
        }
        totalIndicators += 0.2f
        
        val angleConfidence = if (totalIndicators > 0f) lowAngleIndicators / totalIndicators else 0f
        
        // Classify camera angle based on indicators
        val detectedAngle = when {
            angleConfidence > 0.7f -> CameraAngle.GROUND_LEVEL
            angleConfidence > 0.5f -> CameraAngle.LOW_ANGLE
            angleConfidence > 0.3f -> CameraAngle.MID_ANGLE
            else -> CameraAngle.HIGH_ANGLE
        }
        
        // Update calibration
        if (cameraAngleCalibrationCount < cameraAngleFrames) {
            cameraAngleConfidence = (cameraAngleConfidence * cameraAngleCalibrationCount + angleConfidence) / (cameraAngleCalibrationCount + 1)
            detectedCameraAngle = detectedAngle
            cameraAngleCalibrationCount++
            
            debugInfo.apply {
                put("camera_angle_calibration_progress", "$cameraAngleCalibrationCount/$cameraAngleFrames")
                put("torso_aspect_ratio", torsoAspectRatio)
                put("head_to_body_ratio", headToBodyRatio)
                put("torso_height", torsoHeight)
                put("angle_confidence", angleConfidence)
                put("low_angle_indicators", lowAngleIndicators)
            }
            
            if (cameraAngleCalibrationCount >= cameraAngleFrames) {
                isCameraAngleEstablished = true
                // Choose detection method based on camera angle
                isUsingMovementDetection = (detectedCameraAngle == CameraAngle.LOW_ANGLE || 
                                          detectedCameraAngle == CameraAngle.GROUND_LEVEL)
                debugInfo.apply {
                    put("camera_angle_established", true)
                    put("final_camera_angle", detectedCameraAngle.name)
                    put("final_camera_confidence", cameraAngleConfidence)
                    put("using_movement_detection", isUsingMovementDetection)
                }
            }
        }
    }
    
    /**
     * Movement-based detection for low-angle/ground-level cameras
     * CORE INNOVATION: Uses distance changes instead of absolute angles
     */
    private fun calculateMovementBasedAngle(normalizedPose: KeypointNormalizer.NormalizedPose): Float? {
        val leftShoulder = normalizedPose.getLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = normalizedPose.getLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftWrist = normalizedPose.getLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = normalizedPose.getLandmark(PoseLandmark.RIGHT_WRIST)
        
        if (leftShoulder == null || rightShoulder == null || leftWrist == null || rightWrist == null) {
            return null
        }
        
        // Calculate shoulder-to-wrist distances (key for push-up motion)
        val leftDistance = kotlin.math.sqrt(
            (leftShoulder.x - leftWrist.x).let { it * it } +
            (leftShoulder.y - leftWrist.y).let { it * it }
        )
        val rightDistance = kotlin.math.sqrt(
            (rightShoulder.x - rightWrist.x).let { it * it } +
            (rightShoulder.y - rightWrist.y).let { it * it }
        )
        
        val avgDistance = (leftDistance + rightDistance) / 2f
        
        // Update movement history (circular buffer)
        shoulderDistanceHistory[movementHistoryIndex] = avgDistance
        movementHistoryIndex = (movementHistoryIndex + 1) % movementHistorySize
        movementHistoryCount = minOf(movementHistoryCount + 1, movementHistorySize)
        
        if (movementHistoryCount < 3) {
            return null
        }
        
        // Calculate movement patterns
        val validCount = minOf(movementHistoryCount, movementHistorySize)
        val recentDistances = shoulderDistanceHistory.take(validCount)
        val minDistance = recentDistances.minOrNull() ?: avgDistance
        val maxDistance = recentDistances.maxOrNull() ?: avgDistance
        val distanceRange = maxDistance - minDistance
        
        // Convert distance to pseudo-angle for compatibility with existing thresholds
        // Shorter distance = arms bent = lower angle
        // Longer distance = arms extended = higher angle
        val normalizedDistance = if (distanceRange > 0.01f) {
            (avgDistance - minDistance) / distanceRange
        } else {
            0.5f // Default middle position
        }
        
        // Map to angle range suitable for push-up detection
        val pseudoAngle = 70f + (normalizedDistance * 100f)  // Range: 70-170 degrees
        
        debugInfo.apply {
            put("movement_avg_distance", avgDistance)
            put("movement_min_distance", minDistance)
            put("movement_max_distance", maxDistance)
            put("movement_distance_range", distanceRange)
            put("movement_normalized_distance", normalizedDistance)
            put("movement_pseudo_angle", pseudoAngle)
            put("movement_history_count", movementHistoryCount)
        }
        
        return pseudoAngle
    }
    
    /**
     * Ground-aware angle calculation for higher cameras (legacy method enhanced)
     */
    private fun calculateGroundAwareAngle(normalizedPose: KeypointNormalizer.NormalizedPose): Float? {
        // Legacy ground-level detection logic (simplified)
        if (!isGroundLevelEstablished) {
            establishGroundLevel(normalizedPose)
        }
        
        // Calculate traditional elbow angles
        val leftAngle = calculateContextualElbowAngle(normalizedPose, isLeft = true)
        val rightAngle = calculateContextualElbowAngle(normalizedPose, isLeft = false)
        
        val validAngles = listOfNotNull(leftAngle, rightAngle)
        if (validAngles.isEmpty()) {
            return calculateFallbackAngle(normalizedPose)
        }
        
        return validAngles.average().toFloat()
    }
    
    /**
     * Hybrid detection for unknown camera angles
     */
    private fun calculateHybridAngle(normalizedPose: KeypointNormalizer.NormalizedPose): Float? {
        // Try both methods and use the most reliable one
        val movementAngle = calculateMovementBasedAngle(normalizedPose)
        val traditionalAngle = calculateGroundAwareAngle(normalizedPose)
        
        return when {
            movementAngle != null && traditionalAngle != null -> {
                // Average both methods for robustness
                (movementAngle + traditionalAngle) / 2f
            }
            movementAngle != null -> movementAngle
            traditionalAngle != null -> traditionalAngle
            else -> null
        }
    }
    
    /**
     * Adapt thresholds based on detected camera angle
     */
    private fun adaptThresholdsForCameraAngle() {
        val range = maxObservedAngle - minObservedAngle
        if (range < 25f || range > 120f) {
            debugInfo["calibration_rejected_range"] = range
            return
        }
        
        when (detectedCameraAngle) {
            CameraAngle.LOW_ANGLE, CameraAngle.GROUND_LEVEL -> {
                // For low-angle cameras, use movement-based thresholds
                // These are calibrated to the movement detection pseudo-angles
                upElbowThreshold = maxOf(130f, maxObservedAngle - (range * 0.2f))
                downElbowThreshold = maxOf(80f, minObservedAngle + (range * 0.3f))
                debugInfo["threshold_adaptation"] = "low_angle_movement_based"
            }
            CameraAngle.HIGH_ANGLE -> {
                // Traditional thresholds for high-angle cameras
                upElbowThreshold = maxOf(140f, maxObservedAngle - (range * 0.15f))
                downElbowThreshold = maxOf(90f, minObservedAngle + (range * 0.25f))
                debugInfo["threshold_adaptation"] = "high_angle_traditional"
            }
            CameraAngle.MID_ANGLE -> {
                // Balanced thresholds for mid-angle cameras
                upElbowThreshold = maxOf(135f, maxObservedAngle - (range * 0.18f))
                downElbowThreshold = maxOf(85f, minObservedAngle + (range * 0.28f))
                debugInfo["threshold_adaptation"] = "mid_angle_balanced"
            }
            CameraAngle.UNKNOWN -> {
                // Conservative thresholds for unknown camera angles
                upElbowThreshold = maxOf(125f, maxObservedAngle - (range * 0.25f))
                downElbowThreshold = maxOf(75f, minObservedAngle + (range * 0.35f))
                debugInfo["threshold_adaptation"] = "unknown_conservative"
            }
        }
        
        debugInfo.apply {
            put("calibrated_up_threshold", upElbowThreshold)
            put("calibrated_down_threshold", downElbowThreshold)
            put("observed_range", range)
            put("camera_angle_calibration", detectedCameraAngle.name)
        }
    }
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
     * Determine push-up phase using camera-angle aware detection logic
     * Uses adaptive thresholds based on detected camera positioning
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
        
        // CAMERA-ANGLE AWARE PHASE DETECTION: Use thresholds adapted to camera positioning
        val effectiveUpThreshold = when (detectedCameraAngle) {
            CameraAngle.LOW_ANGLE, CameraAngle.GROUND_LEVEL -> {
                // For low-angle cameras using movement detection, use adjusted thresholds
                if (isUsingMovementDetection) {
                    maxOf(upElbowThreshold, 120f)  // Lower threshold for movement-based detection
                } else {
                    maxOf(upElbowThreshold, 135f)  // Traditional higher threshold
                }
            }
            CameraAngle.HIGH_ANGLE -> {
                // Traditional high thresholds for high-angle cameras
                if (isInGroundPosition && groundContactConfidence > 0.7f) {
                    maxOf(upElbowThreshold, 135f)
                } else {
                    upElbowThreshold
                }
            }
            CameraAngle.MID_ANGLE -> {
                // Balanced thresholds for mid-angle cameras
                maxOf(upElbowThreshold, 125f)
            }
            CameraAngle.UNKNOWN -> {
                // Conservative approach for unknown camera angles
                upElbowThreshold
            }
        }
        
        val effectiveDownThreshold = when (detectedCameraAngle) {
            CameraAngle.LOW_ANGLE, CameraAngle.GROUND_LEVEL -> {
                // For low-angle cameras, use more sensitive down detection
                if (isUsingMovementDetection) {
                    maxOf(downElbowThreshold, 70f)  // More sensitive for movement detection
                } else {
                    maxOf(downElbowThreshold, 90f)  // Traditional threshold
                }
            }
            CameraAngle.HIGH_ANGLE -> {
                // Traditional thresholds for high-angle cameras
                if (isInGroundPosition && groundContactConfidence > 0.7f) {
                    maxOf(downElbowThreshold, 90f)
                } else {
                    downElbowThreshold
                }
            }
            CameraAngle.MID_ANGLE -> {
                // Balanced thresholds for mid-angle cameras
                maxOf(downElbowThreshold, 80f)
            }
            CameraAngle.UNKNOWN -> {
                // Conservative approach for unknown camera angles
                downElbowThreshold
            }
        }
        
        // Enhanced phase detection with camera-angle context
        val anglePhase = when {
            primaryAngle < effectiveDownThreshold -> ExercisePhase.DOWN
            primaryAngle > effectiveUpThreshold -> ExercisePhase.UP
            else -> ExercisePhase.TRANSITIONING
        }
        
        // Update comprehensive debug info
        debugInfo.apply {
            put("primary_angle", primaryAngle)
            put("camera_angle_for_phase", detectedCameraAngle.name)
            put("base_up_threshold", upElbowThreshold)
            put("base_down_threshold", downElbowThreshold)
            put("effective_up_threshold", effectiveUpThreshold)
            put("effective_down_threshold", effectiveDownThreshold)
            put("camera_angle_adjusted_thresholds", detectedCameraAngle != CameraAngle.UNKNOWN)
            put("using_movement_thresholds", isUsingMovementDetection)
            put("raw_phase", anglePhase.name)
        }
        
        // Validate with camera-angle aware torso form analysis
        val torsoPhase = validateWithCameraAwareTorsoForm(lastNormalizedPose, anglePhase)
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
     * Validate detected phase with camera-angle aware torso form analysis
     * Enhanced validation that considers camera positioning context
     */
    private fun validateWithCameraAwareTorsoForm(
        normalizedPose: KeypointNormalizer.NormalizedPose?,
        anglePhase: ExercisePhase
    ): ExercisePhase {
        if (normalizedPose == null) return anglePhase
        
        val torsoPitch = calculateTorsoPitch(normalizedPose)
        if (torsoPitch == null) return anglePhase
        
        val smoothedPitch = torsoPitchSmoother.addSample(torsoPitch)
        
        // Camera-angle aware torso validation
        val effectiveTorsoThreshold = when (detectedCameraAngle) {
            CameraAngle.LOW_ANGLE, CameraAngle.GROUND_LEVEL -> {
                // Very forgiving for low-angle cameras as torso angle appears different
                torsoParallelThreshold + 25f  // Additional 25° tolerance for ground-level cameras
            }
            CameraAngle.HIGH_ANGLE -> {
                // Traditional torso requirements for high-angle cameras
                if (isInGroundPosition && groundContactConfidence > 0.6f) {
                    torsoParallelThreshold + 15f
                } else {
                    torsoParallelThreshold
                }
            }
            CameraAngle.MID_ANGLE -> {
                // Moderate tolerance for mid-angle cameras
                torsoParallelThreshold + 10f
            }
            CameraAngle.UNKNOWN -> {
                // Very forgiving when camera angle is unknown
                torsoParallelThreshold + 20f
            }
        }
        
        // Update debug info
        debugInfo.apply {
            put("torso_pitch_raw", torsoPitch)
            put("torso_pitch_smoothed", smoothedPitch)
            put("torso_threshold_base", torsoParallelThreshold)
            put("torso_threshold_effective", effectiveTorsoThreshold)
            put("camera_angle_torso_adjustment", detectedCameraAngle.name)
            put("torso_adjustment_amount", effectiveTorsoThreshold - torsoParallelThreshold)
        }
        
        // For good form push-ups, torso should remain relatively parallel to ground
        // Extra forgiving based on camera angle
        val isGoodForm = abs(smoothedPitch) < effectiveTorsoThreshold
        debugInfo["torso_good_form"] = isGoodForm
        
        // Only reject DOWN phase if form is really bad - very forgiving for low-angle cameras
        val rejectionThreshold = when (detectedCameraAngle) {
            CameraAngle.LOW_ANGLE, CameraAngle.GROUND_LEVEL -> 75f  // Very forgiving
            CameraAngle.HIGH_ANGLE -> 50f  // Traditional
            CameraAngle.MID_ANGLE -> 60f   // Moderate
            CameraAngle.UNKNOWN -> 70f     // Conservative
        }
        
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
     * Reset detector state including camera angle detection and movement-based components
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
        upElbowThreshold = 140f // Reset to adaptive defaults
        downElbowThreshold = 90f // Reset to adaptive defaults
        
        // Reset camera angle detection state
        detectedCameraAngle = CameraAngle.UNKNOWN
        cameraAngleConfidence = 0f
        cameraAngleCalibrationCount = 0
        isCameraAngleEstablished = false
        isUsingMovementDetection = false
        
        // Reset movement-based detection state
        shoulderDistanceHistory.fill(0f)
        wristDistanceHistory.fill(0f)
        movementHistoryIndex = 0
        movementHistoryCount = 0
        
        // Reset ground-level detection state (for legacy compatibility)
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
package com.grloepr.pushtrack.analysis

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.domain.*
import kotlin.math.*

/**
 * Detects pull-up movements from pose data
 * Uses shoulder height and elbow extension to detect reps
 */
class PullUpDetector : ExerciseDetector {
    
    override val exerciseType = ExerciseType.PULL_UP
    
    private var currentState = ExerciseState.UNKNOWN
    private var repCount = 0
    
    // Calibration variables
    private var isCalibrated = false
    private var baselineShoulderY: Float? = null
    private var shoulderRangeThreshold: Float = 0f // Will be set during calibration
    
    // Detection thresholds
    private val minElbowAngleHanging = 160.0 // Nearly straight arms when hanging
    private val maxElbowAnglePulledUp = 90.0 // Bent arms when pulled up
    
    // State confirmation counters
    private var hangingPositionCount = 0
    private var pulledUpPositionCount = 0
    private val confirmationThreshold = 2
    
    override fun requiresCalibration(): Boolean = true
    
    override fun isCalibrated(): Boolean = isCalibrated
    
    override fun calibrate(pose: Pose): Boolean {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        
        if (leftShoulder != null && rightShoulder != null) {
            // Set baseline as average shoulder Y position when hanging
            baselineShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
            
            // Set threshold as 10% of typical torso length (approximate)
            shoulderRangeThreshold = 80f // Pixels - can be adjusted based on camera distance
            
            isCalibrated = true
            return true
        }
        return false
    }
    
    override fun processPose(pose: Pose): DetectionResult {
        if (!isCalibrated) {
            return DetectionResult(
                repCount = repCount,
                currentState = ExerciseState.UNKNOWN,
                formQuality = null,
                confidence = 0f
            )
        }
        
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        
        if (leftShoulder == null || rightShoulder == null || nose == null) {
            return DetectionResult(
                repCount = repCount,
                currentState = currentState,
                formQuality = null,
                confidence = 0f
            )
        }
        
        val currentShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
        val shoulderHeightDiff = baselineShoulderY!! - currentShoulderY
        
        // Calculate elbow angles for form analysis
        val leftElbowAngle = calculateElbowAngle(pose, true)
        val rightElbowAngle = calculateElbowAngle(pose, false)
        val avgElbowAngle = when {
            leftElbowAngle != null && rightElbowAngle != null -> (leftElbowAngle + rightElbowAngle) / 2
            leftElbowAngle != null -> leftElbowAngle
            rightElbowAngle != null -> rightElbowAngle
            else -> null
        }
        
        // Determine position based on shoulder height and elbow angle
        val isHanging = shoulderHeightDiff < shoulderRangeThreshold && 
                       (avgElbowAngle == null || avgElbowAngle > minElbowAngleHanging)
        val isPulledUp = shoulderHeightDiff > shoulderRangeThreshold &&
                        (avgElbowAngle == null || avgElbowAngle < maxElbowAnglePulledUp)
        
        var newRepCount = repCount
        val newState = when {
            isHanging -> {
                hangingPositionCount++
                pulledUpPositionCount = 0
                if (hangingPositionCount >= confirmationThreshold) ExerciseState.START_POSITION else currentState
            }
            isPulledUp -> {
                pulledUpPositionCount++
                hangingPositionCount = 0
                if (pulledUpPositionCount >= confirmationThreshold) ExerciseState.END_POSITION else currentState
            }
            else -> {
                hangingPositionCount = 0
                pulledUpPositionCount = 0
                ExerciseState.TRANSITIONING
            }
        }
        
        // Count a rep when transitioning from hanging to pulled up
        if (currentState == ExerciseState.START_POSITION && newState == ExerciseState.END_POSITION) {
            newRepCount++
            repCount = newRepCount
        }
        
        currentState = newState
        
        // Simple form quality assessment
        val formQuality = FormQuality(
            score = when {
                avgElbowAngle != null && shoulderHeightDiff > shoulderRangeThreshold -> {
                    // Good pull-up form: elbows bent and good height achieved
                    85f
                }
                avgElbowAngle != null && shoulderHeightDiff < shoulderRangeThreshold / 2 -> {
                    // Not pulling up high enough
                    60f
                }
                else -> 75f
            },
            hasGoodForm = avgElbowAngle != null && shoulderHeightDiff > shoulderRangeThreshold * 0.8f,
            feedback = when {
                shoulderHeightDiff < shoulderRangeThreshold * 0.5f -> "Pull up higher"
                avgElbowAngle != null && avgElbowAngle > maxElbowAnglePulledUp + 20 -> "Bend your elbows more"
                else -> null
            }
        )
        
        return DetectionResult(
            repCount = newRepCount,
            currentState = currentState,
            formQuality = formQuality,
            confidence = if (baselineShoulderY != null) 0.8f else 0f,
            lastAngle = avgElbowAngle?.toFloat(),
            detectionMethod = "shoulder_height"
        )
    }
    
    private fun calculateElbowAngle(pose: Pose, isLeftArm: Boolean): Double? {
        val shoulderLandmark = if (isLeftArm) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER
        val elbowLandmark = if (isLeftArm) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
        val wristLandmark = if (isLeftArm) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
        
        val shoulder = pose.getPoseLandmark(shoulderLandmark)
        val elbow = pose.getPoseLandmark(elbowLandmark)
        val wrist = pose.getPoseLandmark(wristLandmark)
        
        if (shoulder == null || elbow == null || wrist == null) return null
        
        // Vector from elbow to shoulder
        val v1x = shoulder.position.x - elbow.position.x
        val v1y = shoulder.position.y - elbow.position.y
        
        // Vector from elbow to wrist
        val v2x = wrist.position.x - elbow.position.x
        val v2y = wrist.position.y - elbow.position.y
        
        // Calculate angle using dot product
        val dotProduct = v1x * v2x + v1y * v2y
        val magnitude1 = sqrt(v1x * v1x + v1y * v1y)
        val magnitude2 = sqrt(v2x * v2x + v2y * v2y)
        
        if (magnitude1 == 0f || magnitude2 == 0f) return null
        
        val cosAngle = dotProduct / (magnitude1 * magnitude2)
        val clampedCosAngle = cosAngle.coerceIn(-1f, 1f)
        
        return Math.toDegrees(acos(clampedCosAngle.toDouble()))
    }
    
    override fun reset() {
        repCount = 0
        currentState = ExerciseState.UNKNOWN
        hangingPositionCount = 0
        pulledUpPositionCount = 0
        // Keep calibration data
    }
    
    override fun getRepCount(): Int = repCount
    
    override fun getCurrentState(): ExerciseState = currentState
}
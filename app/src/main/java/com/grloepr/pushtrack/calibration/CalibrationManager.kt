package com.grloepr.pushtrack.calibration

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.ExerciseDetector
import com.grloepr.pushtrack.analysis.ExerciseType
import com.grloepr.pushtrack.analysis.ExerciseState
import com.grloepr.pushtrack.data.*
import com.grloepr.pushtrack.utils.TerminalLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.math.*

/**
 * Calibration modes for data collection
 */
enum class CalibrationMode {
    DISABLED,           // Normal operation
    WARMUP,            // Getting into position, ignore data
    RECORDING,         // Recording perfect reps
    ANALYZING,         // Processing collected data
    COMPLETE           // Calibration finished
}

/**
 * Calibration state information
 */
data class CalibrationState(
    val mode: CalibrationMode = CalibrationMode.DISABLED,
    val sessionId: String? = null,
    val exerciseType: ExerciseType,
    val targetReps: Int = 10,
    val currentRep: Int = 0,
    val frameCount: Int = 0,
    val validFrames: Int = 0,
    val startTime: Long? = null,
    val message: String = "",
    val confidence: Float = 0f,
    val isReady: Boolean = false
)

/**
 * Advanced calibration manager for optimal exercise detection
 * Collects high-quality pose data and calculates personalized thresholds
 */
class CalibrationManager(
    val context: Context,  // Changed to public
    val detector: ExerciseDetector  // Changed to public
) {
    val database = PushTrackDatabase.getDatabase(context)  // Changed to public
    val poseDataDao = database.poseDataDao()  // Changed to public
    val sessionDao = database.calibrationSessionDao()  // Changed to public
    val thresholdsDao = database.exerciseThresholdsDao()  // Changed to public
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // State management
    private val _calibrationState = MutableStateFlow(
        CalibrationState(exerciseType = detector.exerciseType)
    )
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()
    
    // Data collection
    private var currentSessionId: String? = null
    private var frameNumber = 0
    private var lastRepCount = 0
    private var repStartTime = 0L
    private val collectedData = mutableListOf<PoseDataEntity>()
    
    // Voice commands
    private val voiceCommands = mapOf(
        "start calibration" to ::startCalibration,
        "stop calibration" to ::stopCalibration,
        "reset calibration" to ::resetCalibration,
        "begin recording" to ::beginRecording,
        "analyze data" to ::analyzeData
    )
    
    companion object {
        private const val TAG = "CalibrationManager"
        private const val MIN_CONFIDENCE = 0.7f
        private const val MIN_VALID_FRAMES_PER_REP = 10
        private const val MAX_WARMUP_TIME = 30000L // 30 seconds
    }
    
    /**
     * Start calibration session
     */
    fun startCalibration() {
        scope.launch {
            try {
                currentSessionId = UUID.randomUUID().toString()
                frameNumber = 0
                lastRepCount = 0
                collectedData.clear()
                
                _calibrationState.value = _calibrationState.value.copy(
                    mode = CalibrationMode.WARMUP,
                    sessionId = currentSessionId,
                    currentRep = 0,
                    frameCount = 0,
                    validFrames = 0,
                    startTime = System.currentTimeMillis(),
                    message = "Get into starting position. Say 'begin recording' when ready.",
                    isReady = false
                )
                
                TerminalLogger.logCalibrationStart(detector.exerciseType.name, currentSessionId!!)
                TerminalLogger.c(TAG, "🎯 Target: ${_calibrationState.value.targetReps} perfect reps")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting calibration", e)
                TerminalLogger.e(TAG, "❌ Error starting calibration: ${e.message}")
            }
        }
    }
    
    /**
     * Begin recording phase (after warmup)
     */
    fun beginRecording() {
        scope.launch {
            val currentState = _calibrationState.value
            if (currentState.mode == CalibrationMode.WARMUP) {
                _calibrationState.value = currentState.copy(
                    mode = CalibrationMode.RECORDING,
                    message = "Recording in progress! Perform ${currentState.targetReps} perfect reps.",
                    isReady = true
                )
                
                detector.reset() // Reset rep counter
                TerminalLogger.c(TAG, "🎬 Recording started! Perform your reps now.")
            }
        }
    }
    
    /**
     * Process pose data during calibration
     */
    suspend fun processPose(pose: Pose): Boolean {
        val state = _calibrationState.value
        
        if (state.mode == CalibrationMode.DISABLED) {
            return false
        }
        
        frameNumber++
        
        try {
            // Create pose data entity
            val poseData = createPoseDataEntity(pose, state)
            
            if (state.mode == CalibrationMode.RECORDING && poseData.isValidPose) {
                // Store data for analysis
                collectedData.add(poseData)
                
                // Store in database asynchronously
                scope.launch {
                    poseDataDao.insertPoseData(poseData)
                }
                
                // Check rep progress
                val currentRepCount = detector.repCount
                if (currentRepCount > lastRepCount) {
                    lastRepCount = currentRepCount
                    
                    _calibrationState.value = state.copy(
                        currentRep = currentRepCount,
                        frameCount = frameNumber,
                        validFrames = collectedData.count { it.isValidPose },
                        message = "Rep $currentRepCount/${state.targetReps} completed!"
                    )
                    
                    TerminalLogger.logRepCompleted(currentRepCount, state.targetReps, poseData.primaryMetric)
                    
                    // Check if we've reached target
                    if (currentRepCount >= state.targetReps) {
                        finishRecording()
                    }
                }
                
                // Log real-time data to ADB
                if (frameNumber % 10 == 0) { // Every 10th frame
                    TerminalLogger.logDataPoint(frameNumber, poseData.primaryMetric, poseData.exerciseState, poseData.detectionConfidence)
                }
            }
            
            return poseData.isValidPose
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing pose during calibration", e)
            TerminalLogger.e(TAG, "❌ Error processing pose: ${e.message}")
            return false
        }
    }
    
    /**
     * Finish recording and start analysis
     */
    private suspend fun finishRecording() {
        _calibrationState.value = _calibrationState.value.copy(
            mode = CalibrationMode.ANALYZING,
            message = "Analyzing your data to optimize detection..."
        )
        
        TerminalLogger.c(TAG, "🔬 Analysis phase started...")
        analyzeData()
    }
    
    /**
     * Analyze collected data and optimize thresholds
     */
    fun analyzeData() {
        scope.launch {
            try {
                val state = _calibrationState.value
                val sessionId = state.sessionId ?: return@launch
                
                TerminalLogger.c(TAG, "📈 Starting data analysis...")
                
                // Get all data for this session
                val allData = poseDataDao.getPoseDataForSession(sessionId)
                val validData = allData.filter { it.isValidPose && it.primaryMetric != null }
                
                if (validData.isEmpty()) {
                    TerminalLogger.e(TAG, "❌ No valid data collected!")
                    return@launch
                }
                
                // Separate by exercise state
                val startPositionData = validData.filter { it.exerciseState == "START_POSITION" }
                val endPositionData = validData.filter { it.exerciseState == "END_POSITION" }
                
                TerminalLogger.table(TAG, "Data Analysis Summary", mapOf(
                    "Total frames" to allData.size,
                    "Valid frames" to validData.size,
                    "Start position frames" to startPositionData.size,
                    "End position frames" to endPositionData.size
                ))
                
                // Calculate optimal thresholds
                val analysis = calculateOptimalThresholds(startPositionData, endPositionData)
                
                // Save calibration session
                val session = CalibrationSession(
                    sessionId = sessionId,
                    exerciseType = state.exerciseType.name,
                    startTime = state.startTime ?: 0,
                    endTime = System.currentTimeMillis(),
                    totalReps = state.currentRep,
                    validReps = state.currentRep,
                    optimalUpThreshold = analysis.upThreshold,
                    optimalDownThreshold = analysis.downThreshold,
                    optimalTransitionSpeed = analysis.transitionSpeed,
                    averageRepDuration = analysis.averageRepDuration,
                    standardDeviation = analysis.standardDeviation,
                    confidenceScore = analysis.confidenceScore,
                    appliedAt = System.currentTimeMillis()
                )
                
                sessionDao.insertSession(session)
                
                // Create thresholds from analysis results
                val thresholds = ExerciseThresholds(
                    exerciseType = state.exerciseType.name,
                    upThreshold = analysis.upThreshold ?: detector.getUpThreshold(),
                    downThreshold = analysis.downThreshold ?: detector.getDownThreshold(),
                    transitionBuffer = analysis.transitionBuffer,
                    minRepDuration = analysis.minRepDuration,
                    maxRepDuration = analysis.maxRepDuration,
                    speedSensitivity = analysis.speedSensitivity,
                    minConfidence = MIN_CONFIDENCE,
                    minLandmarkVisibility = 0.5f,
                    calibrationSessionId = sessionId,
                    createdAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis(),
                    appliedAt = System.currentTimeMillis(), // Add this missing parameter
                    accuracyScore = analysis.confidenceScore,
                    falsePositiveRate = analysis.falsePositiveRate,
                    falseNegativeRate = analysis.falseNegativeRate
                )
                
                thresholdsDao.insertThresholds(thresholds)
                
                // Apply new thresholds to detector
                applyThresholds(thresholds)
                
                _calibrationState.value = state.copy(
                    mode = CalibrationMode.COMPLETE,
                    message = "Calibration complete! Optimized thresholds applied.",
                    confidence = analysis.confidenceScore ?: 0f
                )
                
                TerminalLogger.logAnalysisResult(analysis.upThreshold, analysis.downThreshold, analysis.confidenceScore)
                
                // Export data for further analysis
                exportCalibrationData(sessionId, analysis)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing calibration data", e)
                TerminalLogger.e(TAG, "❌ Analysis failed: ${e.message}")
            }
        }
    }
    
    /**
     * Stop calibration
     */
    fun stopCalibration() {
        scope.launch {
            _calibrationState.value = CalibrationState(exerciseType = detector.exerciseType)
            currentSessionId = null
            frameNumber = 0
            collectedData.clear()
            TerminalLogger.c(TAG, "⏹️ Calibration stopped")
        }
    }
    
    /**
     * Reset calibration
     */
    fun resetCalibration() {
        scope.launch {
            stopCalibration()
            detector.reset()
            TerminalLogger.c(TAG, "🔄 Calibration reset")
        }
    }
    
    /**
     * Process voice command
     */
    fun processVoiceCommand(command: String): Boolean {
        val normalizedCommand = command.lowercase().trim()
        return voiceCommands.entries.any { (trigger, action) ->
            if (normalizedCommand.contains(trigger)) {
                action.invoke()
                true
            } else false
        }
    }
    
    /**
     * Create pose data entity from pose
     */
    fun createPoseDataEntity(pose: Pose, state: CalibrationState): PoseDataEntity {  // Changed to public
        val primaryMetric = detector.getExerciseMetric(pose)  // Use accessor method
        val exerciseState = if (primaryMetric != null) {
            detector.getStateForMetric(primaryMetric)  // Use accessor method
        } else {
            ExerciseState.UNKNOWN
        }
        
        // Extract all landmarks
        val landmarks = pose.allPoseLandmarks
        fun getLandmark(landmarkType: Int) = landmarks.find { it.landmarkType == landmarkType }
        
        val leftShoulder = getLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = getLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = getLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = getLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = getLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = getLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = getLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = getLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = getLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = getLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = getLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = getLandmark(PoseLandmark.RIGHT_ANKLE)
        
        // Calculate angles
        val leftArmAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
        val rightArmAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
        val leftLegAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        val rightLegAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
        
        // Calculate detection confidence
        val requiredLandmarks = listOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftWrist, rightWrist)
        val validLandmarks = requiredLandmarks.count { it?.inFrameLikelihood ?: 0f > 0.5f }
        val confidence = validLandmarks.toFloat() / requiredLandmarks.size
        
        val hasAllRequired = validLandmarks == requiredLandmarks.size
        val isValid = confidence >= MIN_CONFIDENCE && primaryMetric != null
        
        return PoseDataEntity(
            sessionId = state.sessionId ?: "",
            exerciseType = state.exerciseType.name,
            timestamp = System.currentTimeMillis(),
            frameNumber = frameNumber,
            
            primaryMetric = primaryMetric,
            exerciseState = exerciseState.name,
            repCount = detector.repCount,
            
            // Landmark positions
            leftShoulderX = leftShoulder?.position?.x,
            leftShoulderY = leftShoulder?.position?.y,
            leftShoulderVisibility = leftShoulder?.inFrameLikelihood,
            
            rightShoulderX = rightShoulder?.position?.x,
            rightShoulderY = rightShoulder?.position?.y,
            rightShoulderVisibility = rightShoulder?.inFrameLikelihood,
            
            leftElbowX = leftElbow?.position?.x,
            leftElbowY = leftElbow?.position?.y,
            leftElbowVisibility = leftElbow?.inFrameLikelihood,
            
            rightElbowX = rightElbow?.position?.x,
            rightElbowY = rightElbow?.position?.y,
            rightElbowVisibility = rightElbow?.inFrameLikelihood,
            
            leftWristX = leftWrist?.position?.x,
            leftWristY = leftWrist?.position?.y,
            leftWristVisibility = leftWrist?.inFrameLikelihood,
            
            rightWristX = rightWrist?.position?.x,
            rightWristY = rightWrist?.position?.y,
            rightWristVisibility = rightWrist?.inFrameLikelihood,
            
            leftHipX = leftHip?.position?.x,
            leftHipY = leftHip?.position?.y,
            leftHipVisibility = leftHip?.inFrameLikelihood,
            
            rightHipX = rightHip?.position?.x,
            rightHipY = rightHip?.position?.y,
            rightHipVisibility = rightHip?.inFrameLikelihood,
            
            leftKneeX = leftKnee?.position?.x,
            leftKneeY = leftKnee?.position?.y,
            leftKneeVisibility = leftKnee?.inFrameLikelihood,
            
            rightKneeX = rightKnee?.position?.x,
            rightKneeY = rightKnee?.position?.y,
            rightKneeVisibility = rightKnee?.inFrameLikelihood,
            
            leftAnkleX = leftAnkle?.position?.x,
            leftAnkleY = leftAnkle?.position?.y,
            leftAnkleVisibility = leftAnkle?.inFrameLikelihood,
            
            rightAnkleX = rightAnkle?.position?.x,
            rightAnkleY = rightAnkle?.position?.y,
            rightAnkleVisibility = rightAnkle?.inFrameLikelihood,
            
            // Calculated angles
            leftArmAngle = leftArmAngle,
            rightArmAngle = rightArmAngle,
            leftLegAngle = leftLegAngle,
            rightLegAngle = rightLegAngle,
            
            detectionConfidence = confidence,
            movementSpeed = null, // TODO: Calculate from previous frame
            formQuality = confidence,
            
            isCalibrationData = state.mode == CalibrationMode.RECORDING,
            calibrationPhase = state.mode.name,
            repNumberInSession = if (state.mode == CalibrationMode.RECORDING) detector.repCount else null,
            
            isValidPose = isValid,
            hasAllRequiredLandmarks = hasAllRequired
        )
    }
    
    /**
     * Calculate angle between three landmarks
     */
    private fun calculateAngle(p1: PoseLandmark?, p2: PoseLandmark?, p3: PoseLandmark?): Double? {
        if (p1 == null || p2 == null || p3 == null) return null
        
        val point1 = p1.position
        val point2 = p2.position
        val point3 = p3.position
        
        val a = sqrt((point2.x - point3.x).pow(2) + (point2.y - point3.y).pow(2))
        val b = sqrt((point1.x - point3.x).pow(2) + (point1.y - point3.y).pow(2))
        val c = sqrt((point1.x - point2.x).pow(2) + (point1.y - point2.y).pow(2))
        
        val angle = acos((a.pow(2) + c.pow(2) - b.pow(2)) / (2 * a * c))
        return Math.toDegrees(angle.toDouble())
    }
    
    /**
     * Log message to Android Debug Bridge (visible in Android Studio)
     */
    private fun logToADB(message: String) {
        TerminalLogger.c(TAG, message)
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
    }
    
    // Additional methods for threshold calculation, data export, etc. will be in part 2...
}

package com.grloepr.pushtrack.exercise

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Analyzes pose data to detect exercise reps and track exercise state
 */
class ExerciseAnalyzer(
    private val exerciseType: ExerciseType,
    private val repListener: (Int) -> Unit
) {
    
    private var currentState: ExerciseState = ExerciseState.Waiting
    private var repCount = 0
    private var previousState: ExerciseState = ExerciseState.Waiting
    private val stateHistory = mutableListOf<Pair<Long, ExerciseState>>()
    
    // Thresholds for detecting state transitions
    private val stateThreshold = 0.3f // Minimum confidence for state change
    private val minStateDuration = 300L // Minimum time in ms before considering state change
    
    fun analyzePose(pose: Pose) {
        if (!hasValidPose(pose)) return
        
        val currentTime = System.currentTimeMillis()
        val newState = determineState(pose)
        
        // Add current state to history
        stateHistory.add(currentTime to newState)
        
        // Keep only recent history (last 2 seconds)
        stateHistory.removeAll { currentTime - it.first > 2000 }
        
        // Detect rep when transitioning from down -> up or bottom -> top
        if (previousState != newState) {
            if (isRepTransition(previousState, newState)) {
                repCount++
                repListener(repCount)
                println("Rep detected! Total: $repCount")
            }
        }
        
        previousState = newState
        currentState = newState
    }
    
    private fun isRepTransition(from: ExerciseState, to: ExerciseState): Boolean {
        return when (exerciseType) {
            ExerciseType.PUSHUP -> from == ExerciseState.Down && to == ExerciseState.Up
            ExerciseType.SQUAT -> from == ExerciseState.Down && to == ExerciseState.Up
            ExerciseType.PULLUP -> from == ExerciseState.Down && to == ExerciseState.Up
        }
    }
    
    private fun determineState(pose: Pose): ExerciseState {
        return when (exerciseType) {
            ExerciseType.PUSHUP -> detectPushupState(pose)
            ExerciseType.SQUAT -> detectSquatState(pose)
            ExerciseType.PULLUP -> detectPullupState(pose)
        }
    }
    
    private fun detectPushupState(pose: Pose): ExerciseState {
        // For pushups: Check left and right shoulder positions relative to wrists
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        
        if (!hasValidLandmarks(leftWrist, leftShoulder, rightWrist, rightShoulder)) {
            return ExerciseState.Waiting
        }
        
        // Calculate if wrists are below shoulders (down position)
        val avgWristY = (leftWrist!!.position.y + rightWrist!!.position.y) / 2f
        val avgShoulderY = (leftShoulder!!.position.y + rightShoulder!!.position.y) / 2f
        
        val wristBelowShoulder = avgWristY > avgShoulderY
        val distance = abs(avgWristY - avgShoulderY)
        
        return if (wristBelowShoulder && distance > stateThreshold) {
            ExerciseState.Down
        } else if (distance < stateThreshold) {
            ExerciseState.Up
        } else {
            ExerciseState.Waiting
        }
    }
    
    private fun detectSquatState(pose: Pose): ExerciseState {
        // For squats: Check hip position relative to knees
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        
        if (!hasValidLandmarks(leftHip, leftKnee, rightHip, rightKnee)) {
            return ExerciseState.Waiting
        }
        
        val avgHipY = (leftHip!!.position.y + rightHip!!.position.y) / 2f
        val avgKneeY = (leftKnee!!.position.y + rightKnee!!.position.y) / 2f
        
        val hipBelowKnee = avgHipY > avgKneeY
        val distance = abs(avgHipY - avgKneeY)
        
        return if (hipBelowKnee && distance > stateThreshold * 0.8f) {
            ExerciseState.Down
        } else if (distance < stateThreshold * 0.8f) {
            ExerciseState.Up
        } else {
            ExerciseState.Waiting
        }
    }
    
    private fun detectPullupState(pose: Pose): ExerciseState {
        // For pull-ups: Check chin position relative to shoulders
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        
        if (!hasValidLandmarks(nose, leftShoulder, rightShoulder, leftWrist, rightWrist)) {
            return ExerciseState.Waiting
        }
        
        val noseY = nose!!.position.y
        val avgShoulderY = (leftShoulder!!.position.y + rightShoulder!!.position.y) / 2f
        
        val chinAboveShoulder = noseY < avgShoulderY
        val distance = abs(noseY - avgShoulderY)
        
        return if (chinAboveShoulder && distance > stateThreshold) {
            ExerciseState.Up  // Chin above bar (top position)
        } else if (!chinAboveShoulder && distance > stateThreshold) {
            ExerciseState.Down  // Chin below bar (bottom position)
        } else {
            ExerciseState.Waiting
        }
    }
    
    private fun hasValidPose(pose: Pose): Boolean {
        // Check if we have enough visible landmarks
        val keyLandmarks = when (exerciseType) {
            ExerciseType.PUSHUP -> listOf(
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_WRIST,
                PoseLandmark.RIGHT_WRIST
            )
            ExerciseType.SQUAT -> listOf(
                PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE,
                PoseLandmark.RIGHT_KNEE
            )
            ExerciseType.PULLUP -> listOf(
                PoseLandmark.NOSE,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_WRIST,
                PoseLandmark.RIGHT_WRIST
            )
        }
        
        val visibleCount = keyLandmarks.count { 
            pose.getPoseLandmark(it)?.inFrameLikelihood ?: 0f > 0.5f
        }
        
        return visibleCount >= keyLandmarks.size * 0.75f // At least 75% of key landmarks visible
    }
    
    private fun hasValidLandmarks(vararg landmarks: PoseLandmark?): Boolean {
        return landmarks.all { landmark ->
            landmark != null && landmark.inFrameLikelihood > 0.5f
        }
    }
    
    fun reset() {
        repCount = 0
        currentState = ExerciseState.Waiting
        previousState = ExerciseState.Waiting
        stateHistory.clear()
    }
    
    fun getCurrentRepCount(): Int = repCount
    fun getCurrentState(): ExerciseState = currentState
}


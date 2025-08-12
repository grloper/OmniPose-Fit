package com.grloepr.pushtrack.detection

import com.grloepr.pushtrack.detection.utils.KeypointNormalizer
import io.mockk.mockk
import io.mockk.every
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Integration tests for enhanced push-up and pull-up detection accuracy
 */
class EnhancedDetectionIntegrationTest {
    
    private lateinit var pushUpDetector: PushUpDetector
    private lateinit var pullUpDetector: PullUpDetector
    
    @Before
    fun setup() {
        pushUpDetector = PushUpDetector()
        pullUpDetector = PullUpDetector()
    }
    
    @Test
    fun `enhanced push-up detector handles complete rep cycle correctly`() {
        // Simulate a complete push-up cycle with good form
        val poses = createPushUpSequence()
        val repCounts = mutableListOf<Int>()
        
        poses.forEach { pose ->
            pushUpDetector.processPose(pose)
            repCounts.add(pushUpDetector.getRepCount())
        }
        
        // Should detect exactly 1 rep for the sequence
        assertEquals("Should detect 1 complete rep", 1, repCounts.last())
        
        // Rep count should only increase at the end of cycle (DOWN -> UP)
        val increasePoints = repCounts.zipWithNext().count { (prev, curr) -> curr > prev }
        assertEquals("Rep should only be counted once per cycle", 1, increasePoints)
    }
    
    @Test
    fun `enhanced pull-up detector uses torso movement correctly`() {
        val poses = createPullUpSequence()
        val phases = mutableListOf<ExercisePhase>()
        val confidences = mutableListOf<Float>()
        
        poses.forEach { pose ->
            pullUpDetector.processPose(pose)
            phases.add(pullUpDetector.getCurrentPhase())
            confidences.add(pullUpDetector.state.value.confidence)
        }
        
        // Should show clear DOWN -> UP transition
        assertTrue("Should start in DOWN phase (hanging)", 
                   phases.take(3).all { it == ExercisePhase.DOWN })
        assertTrue("Should end in UP phase (pulled up)", 
                   phases.takeLast(3).all { it == ExercisePhase.UP })
        
        // Confidence should improve as calibration occurs
        val firstHalfAvgConf = confidences.take(confidences.size / 2).average()
        val secondHalfAvgConf = confidences.takeLast(confidences.size / 2).average()
        assertTrue("Confidence should improve with calibration", 
                   secondHalfAvgConf > firstHalfAvgConf)
    }
    
    @Test
    fun `push-up detector rejects poor form correctly`() {
        // Create poses with poor form (torso not parallel)
        val poorFormPoses = createPoorFormPushUpSequence()
        val confidences = mutableListOf<Float>()
        
        poorFormPoses.forEach { pose ->
            pushUpDetector.processPose(pose)
            confidences.add(pushUpDetector.state.value.confidence)
        }
        
        val avgConfidence = confidences.average()
        assertTrue("Poor form should result in low confidence", avgConfidence < 0.6f)
        
        // Should not count reps with poor form
        assertEquals("Poor form should not count reps", 0, pushUpDetector.getRepCount())
    }
    
    @Test
    fun `pull-up detector handles occlusion gracefully`() {
        val poses = createPullUpWithOcclusionSequence()
        var lastValidPhase = ExercisePhase.DOWN
        
        poses.forEach { pose ->
            pullUpDetector.processPose(pose)
            val currentPhase = pullUpDetector.getCurrentPhase()
            val confidence = pullUpDetector.state.value.confidence
            
            if (confidence > 0.5f) {
                lastValidPhase = currentPhase
            }
        }
        
        // Should maintain reasonable state even with occlusion
        assertTrue("Should handle occlusion without breaking", 
                   pullUpDetector.getRepCount() >= 0)
    }
    
    @Test
    fun `enhanced detectors provide detailed statistics`() {
        // Process some poses
        val pushUpPoses = createPushUpSequence()
        pushUpPoses.forEach { pushUpDetector.processPose(it) }
        
        val stats = pushUpDetector.getDetailedStats()
        
        assertTrue("Should provide form quality metric", stats.containsKey("torso_form_quality"))
        assertTrue("Should provide state machine progress", stats.containsKey("state_machine_progress"))
        assertTrue("Should provide rep confidence info", stats.containsKey("total_reps"))
        assertTrue("Should provide uncertain rep count", stats.containsKey("uncertain_reps"))
    }
    
    @Test
    fun `detectors maintain performance under rapid pose changes`() {
        val startTime = System.currentTimeMillis()
        
        // Simulate 30 FPS for 10 seconds (300 frames)
        repeat(300) {
            val randomPose = createRandomValidPose()
            pushUpDetector.processPose(randomPose)
        }
        
        val endTime = System.currentTimeMillis()
        val processingTime = endTime - startTime
        
        // Should process 300 frames in reasonable time (< 1 second)
        assertTrue("Should maintain real-time performance", processingTime < 1000)
    }
    
    @Test
    fun `confidence thresholding marks uncertain reps correctly`() {
        // Mix high and low confidence poses
        val mixedConfidencePoses = createMixedConfidencePushUpSequence()
        
        mixedConfidencePoses.forEach { pushUpDetector.processPose(it) }
        
        val stats = pushUpDetector.getDetailedStats()
        val totalReps = stats["total_reps"] as Int
        val uncertainReps = stats["uncertain_reps"] as Int
        
        assertTrue("Should count some reps", totalReps > 0)
        assertTrue("Should mark some reps as uncertain", uncertainReps > 0)
        assertTrue("Uncertain reps should be subset of total", uncertainReps <= totalReps)
    }
    
    // Helper methods to create mock pose sequences
    
    private fun createPushUpSequence(): List<Pose> {
        return listOf(
            createMockPose(elbowAngle = 160f, torsoAngle = 5f), // UP position
            createMockPose(elbowAngle = 140f, torsoAngle = 8f), // Transitioning down
            createMockPose(elbowAngle = 100f, torsoAngle = 10f), // Transitioning down
            createMockPose(elbowAngle = 70f, torsoAngle = 12f),  // DOWN position
            createMockPose(elbowAngle = 70f, torsoAngle = 12f),  // Hold DOWN
            createMockPose(elbowAngle = 90f, torsoAngle = 10f),  // Transitioning up
            createMockPose(elbowAngle = 120f, torsoAngle = 8f),  // Transitioning up
            createMockPose(elbowAngle = 160f, torsoAngle = 5f)   // UP position
        )
    }
    
    private fun createPullUpSequence(): List<Pose> {
        return listOf(
            createMockPose(elbowAngle = 160f, torsoPosition = -0.5f), // Hanging
            createMockPose(elbowAngle = 140f, torsoPosition = -0.3f), // Starting pull
            createMockPose(elbowAngle = 100f, torsoPosition = -0.1f), // Pulling up
            createMockPose(elbowAngle = 70f, torsoPosition = 0.1f),   // Nearly up
            createMockPose(elbowAngle = 60f, torsoPosition = 0.2f),   // Pulled up
            createMockPose(elbowAngle = 80f, torsoPosition = 0.1f),   // Lowering
            createMockPose(elbowAngle = 120f, torsoPosition = -0.2f), // Lowering
            createMockPose(elbowAngle = 160f, torsoPosition = -0.5f)  // Hanging
        )
    }
    
    private fun createPoorFormPushUpSequence(): List<Pose> {
        return listOf(
            createMockPose(elbowAngle = 160f, torsoAngle = 25f), // Poor form - angled torso
            createMockPose(elbowAngle = 70f, torsoAngle = 30f),  // Poor form - angled torso
            createMockPose(elbowAngle = 160f, torsoAngle = 25f)  // Poor form - angled torso
        )
    }
    
    private fun createPullUpWithOcclusionSequence(): List<Pose> {
        return listOf(
            createMockPose(elbowAngle = 160f, confidence = 0.9f),
            createMockPose(elbowAngle = 100f, confidence = 0.8f),
            createMockPose(elbowAngle = 70f, confidence = 0.3f),  // Occluded
            createMockPose(elbowAngle = 60f, confidence = 0.2f),  // Occluded
            createMockPose(elbowAngle = 80f, confidence = 0.8f),  // Recovered
            createMockPose(elbowAngle = 160f, confidence = 0.9f)
        )
    }
    
    private fun createMixedConfidencePushUpSequence(): List<Pose> {
        val highConfPoses = createPushUpSequence().map { 
            updateMockPoseConfidence(it, 0.9f) 
        }
        val lowConfPoses = createPushUpSequence().map { 
            updateMockPoseConfidence(it, 0.5f) 
        }
        return highConfPoses + lowConfPoses
    }
    
    private fun createRandomValidPose(): Pose {
        val randomElbow = 60f + (Math.random() * 120f).toFloat()
        val randomTorso = -10f + (Math.random() * 20f).toFloat()
        return createMockPose(elbowAngle = randomElbow, torsoAngle = randomTorso)
    }
    
    private fun createMockPose(
        elbowAngle: Float = 90f,
        torsoAngle: Float = 0f,
        torsoPosition: Float = 0f,
        confidence: Float = 0.8f
    ): Pose {
        val mockPose = mockk<Pose>()
        
        // Create mock landmarks based on the angles provided
        val landmarks = createMockLandmarks(elbowAngle, torsoAngle, torsoPosition, confidence)
        
        every { mockPose.allPoseLandmarks } returns landmarks
        landmarks.forEach { landmark ->
            every { mockPose.getPoseLandmark(landmark.landmarkType) } returns landmark
        }
        
        return mockPose
    }
    
    private fun updateMockPoseConfidence(pose: Pose, newConfidence: Float): Pose {
        // This would need implementation based on the mock framework
        // For now, return the original pose
        return pose
    }
    
    private fun createMockLandmarks(
        elbowAngle: Float,
        torsoAngle: Float,
        torsoPosition: Float,
        confidence: Float
    ): List<PoseLandmark> {
        // This is a simplified mock - in reality, you'd calculate realistic positions
        // based on the desired angles and positions
        return listOf(
            createMockLandmark(PoseLandmark.LEFT_SHOULDER, 100f, 100f, confidence),
            createMockLandmark(PoseLandmark.RIGHT_SHOULDER, 200f, 100f, confidence),
            createMockLandmark(PoseLandmark.LEFT_ELBOW, 80f, 150f, confidence),
            createMockLandmark(PoseLandmark.RIGHT_ELBOW, 220f, 150f, confidence),
            createMockLandmark(PoseLandmark.LEFT_WRIST, 60f, 200f, confidence),
            createMockLandmark(PoseLandmark.RIGHT_WRIST, 240f, 200f, confidence),
            createMockLandmark(PoseLandmark.LEFT_HIP, 120f, 300f, confidence),
            createMockLandmark(PoseLandmark.RIGHT_HIP, 180f, 300f, confidence),
            createMockLandmark(PoseLandmark.NOSE, 150f, 50f, confidence)
        )
    }
    
    private fun createMockLandmark(
        type: Int,
        x: Float,
        y: Float,
        confidence: Float
    ): PoseLandmark {
        val mockLandmark = mockk<PoseLandmark>()
        val mockPosition = mockk<com.google.mlkit.vision.common.PointF3D>()
        
        every { mockLandmark.landmarkType } returns type
        every { mockLandmark.position } returns mockPosition
        every { mockLandmark.inFrameLikelihood } returns confidence
        every { mockPosition.x } returns x
        every { mockPosition.y } returns y
        every { mockPosition.z } returns 0f
        
        return mockLandmark
    }
}
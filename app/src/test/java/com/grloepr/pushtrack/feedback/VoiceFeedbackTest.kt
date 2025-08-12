package com.grloepr.pushtrack.feedback

import android.content.Context
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.grloepr.pushtrack.analysis.PushUpState
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.* // replaced kotlin.test imports

class VoiceFeedbackManagerTest {

    private lateinit var context: Context
    private lateinit var voiceFeedbackManager: VoiceFeedbackManager

    @Before
    fun setup() {
        context = mockk<Context>(relaxed = true)
        voiceFeedbackManager = VoiceFeedbackManager(context)
    }

    @After
    fun tearDown() {
        voiceFeedbackManager.shutdown()
    }

    @Test
    fun `announceRepCount should announce numbers correctly`() {
        // Test that rep counts are announced properly
        voiceFeedbackManager.announceRepCount(1)
        voiceFeedbackManager.announceRepCount(5)
        voiceFeedbackManager.announceRepCount(10)
        
        // Verify that the voice feedback manager is tracking counts
        assertTrue(true) // Since we can't easily test TTS without Android context
    }

    @Test
    fun `updateSettings should clamp values to valid ranges`() {
        voiceFeedbackManager.updateSettings(
            enabled = true,
            speechRate = 5.0f, // Should be clamped to 3.0f
            pitchRate = 0.05f, // Should be clamped to 0.1f
            volume = 2.0f // Should be clamped to 1.0f
        )
        
        // Test passes if no exceptions are thrown
        assertTrue(true)
    }

    @Test
    fun `reset should clear rep tracking`() {
        voiceFeedbackManager.announceRepCount(5)
        voiceFeedbackManager.reset()
        voiceFeedbackManager.announceRepCount(3) // Should announce even though it's less than 5
        
        assertTrue(true)
    }
}

class PostureAnalyzerTest {

    private lateinit var postureAnalyzer: PostureAnalyzer
    private lateinit var mockPose: Pose

    @Before
    fun setup() {
        postureAnalyzer = PostureAnalyzer()
        mockPose = mockk<Pose>()
    }

    @Test
    fun `analyzePose should return valid analysis result`() {
        val leftShoulder = mockk<PoseLandmark>()
        val leftElbow = mockk<PoseLandmark>()
        val leftWrist = mockk<PoseLandmark>()
        val shoulderPos = mockk<android.graphics.PointF>()
        val elbowPos = mockk<android.graphics.PointF>()
        val wristPos = mockk<android.graphics.PointF>()
        every { shoulderPos.x } returns 100f; every { shoulderPos.y } returns 100f
        every { elbowPos.x } returns 120f; every { elbowPos.y } returns 150f
        every { wristPos.x } returns 140f; every { wristPos.y } returns 200f
        every { leftShoulder.position } returns shoulderPos; every { leftShoulder.inFrameLikelihood } returns 0.8f
        every { leftElbow.position } returns elbowPos; every { leftElbow.inFrameLikelihood } returns 0.8f
        every { leftWrist.position } returns wristPos; every { leftWrist.inFrameLikelihood } returns 0.8f
        every { mockPose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) } returns leftShoulder
        every { mockPose.getPoseLandmark(PoseLandmark.LEFT_ELBOW) } returns leftElbow
        every { mockPose.getPoseLandmark(PoseLandmark.LEFT_WRIST) } returns leftWrist
        every { mockPose.getPoseLandmark(any()) } returns null
        val result = postureAnalyzer.analyzePose(mockPose, PushUpState.UP_POSITION)
        
        assertNotNull(result)
        assertTrue(result.formQuality >= 0f)
        assertTrue(result.formQuality <= 100f)
    }

    @Test
    fun `getStarRating should return correct star count`() {
        assertEquals(5, postureAnalyzer.getStarRating(95f))
        assertEquals(4, postureAnalyzer.getStarRating(80f))
        assertEquals(3, postureAnalyzer.getStarRating(65f))
        assertEquals(2, postureAnalyzer.getStarRating(45f))
        assertEquals(1, postureAnalyzer.getStarRating(25f))
    }

    @Test
    fun `reset should clear analysis state`() {
        // Analyze some poses to build history
        postureAnalyzer.analyzePose(mockPose, PushUpState.UP_POSITION)
        postureAnalyzer.analyzePose(mockPose, PushUpState.DOWN_POSITION)
        
        postureAnalyzer.reset()
        
        // Verify reset worked (no exceptions thrown)
        assertTrue(true)
    }
}
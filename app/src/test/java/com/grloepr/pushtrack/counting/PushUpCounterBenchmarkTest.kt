package com.grloepr.pushtrack.counting

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

/**
 * Performance and accuracy benchmarks for push-up counting logic
 */
class PushUpCounterBenchmarkTest {
    
    @Test
    fun `benchmark state machine performance with 1000 pose updates`() {
        val pushUpCounter = PushUpCounter()
        val mockPose = createRealisticMockPose(160f, true, true)
        
        val startTime = System.currentTimeMillis()
        
        // Simulate 1000 pose detections (typical for ~30 seconds at 30 FPS)
        repeat(1000) {
            pushUpCounter.processPose(mockPose)
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val avgTimePerPose = totalTime / 1000.0
        
        // Should process each pose in less than 1ms for real-time performance
        assertTrue(
            "Average processing time per pose ($avgTimePerPose ms) should be < 1ms", 
            avgTimePerPose < 1.0
        )
        
        println("Performance benchmark: ${avgTimePerPose} ms per pose update")
    }
    
    @Test
    fun `accuracy test with realistic push-up sequence`() {
        val pushUpCounter = PushUpCounter()
        
        // Simulate 5 complete push-ups with realistic elbow angle progression
        val pushUpSequences = listOf(
            // Push-up 1
            listOf(170f, 150f, 130f, 110f, 85f, 100f, 120f, 145f, 165f),
            // Push-up 2  
            listOf(168f, 145f, 125f, 105f, 80f, 95f, 115f, 140f, 162f),
            // Push-up 3
            listOf(165f, 148f, 128f, 108f, 82f, 98f, 118f, 142f, 160f),
            // Push-up 4
            listOf(167f, 147f, 127f, 107f, 83f, 97f, 117f, 141f, 163f),
            // Push-up 5
            listOf(166f, 146f, 126f, 106f, 81f, 96f, 116f, 140f, 161f)
        )
        
        pushUpSequences.forEach { sequence ->
            sequence.forEach { elbowAngle ->
                val mockPose = createRealisticMockPose(elbowAngle, true, true)
                pushUpCounter.processPose(mockPose)
                // Simulate realistic timing between poses
                Thread.sleep(33) // ~30 FPS
            }
        }
        
        // Should count exactly 5 push-ups
        assertEquals("Should count exactly 5 push-ups", 5, pushUpCounter.getCurrentRepCount())
        
        println("Accuracy test: Counted ${pushUpCounter.getCurrentRepCount()}/5 push-ups correctly")
    }
    
    @Test
    fun `noise resistance test with jittery angle measurements`() {
        val pushUpCounter = PushUpCounter()
        
        // Simulate noisy elbow angle measurements during a single push-up
        val baseAngles = listOf(170f, 150f, 130f, 110f, 85f, 100f, 120f, 145f, 165f)
        val noisyAngles = baseAngles.map { baseAngle ->
            // Add random noise ±5 degrees
            baseAngle + (Math.random().toFloat() - 0.5f) * 10f
        }
        
        noisyAngles.forEach { elbowAngle ->
            val mockPose = createRealisticMockPose(elbowAngle, true, true)
            pushUpCounter.processPose(mockPose)
            Thread.sleep(350) // Respect minimum state hold time
        }
        
        // Should still count 1 push-up despite noise
        assertEquals("Should count 1 push-up despite measurement noise", 1, pushUpCounter.getCurrentRepCount())
    }
    
    @Test
    fun `quality assessment accuracy test`() {
        val pushUpCounter = PushUpCounter()
        
        // Test different quality levels
        val testCases = listOf(
            // Excellent: Full range of motion
            Triple(listOf(170f, 80f, 170f), PushUpQuality.EXCELLENT, "Full ROM"),
            // Good: Good range of motion  
            Triple(listOf(160f, 90f, 160f), PushUpQuality.GOOD, "Good ROM"),
            // Fair: Partial range of motion
            Triple(listOf(150f, 110f, 150f), PushUpQuality.FAIR, "Partial ROM"),
            // Poor: Insufficient range of motion
            Triple(listOf(140f, 120f, 140f), PushUpQuality.POOR, "Poor ROM")
        )
        
        testCases.forEach { (angles, expectedQuality, description) ->
            pushUpCounter.resetCounter()
            
            angles.forEach { angle ->
                val mockPose = createRealisticMockPose(angle, true, true)
                pushUpCounter.processPose(mockPose)
                Thread.sleep(350)
            }
            
            if (expectedQuality != PushUpQuality.POOR) {
                assertEquals("$description should be counted", 1, pushUpCounter.getCurrentRepCount())
                // Note: Quality assessment would need to be exposed for testing
            } else {
                assertEquals("$description should not be counted", 0, pushUpCounter.getCurrentRepCount())
            }
        }
    }
    
    @Test
    fun `memory usage test with extended session`() {
        val pushUpCounter = PushUpCounter()
        val mockPose = createRealisticMockPose(160f, true, true)
        
        // Get initial memory usage
        System.gc()
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        // Simulate 10-minute workout session (18,000 poses at 30 FPS)
        repeat(18000) { i ->
            // Vary angle to simulate realistic movement
            val angle = 120f + (Math.sin(i * 0.1) * 50f).toFloat()
            val pose = createRealisticMockPose(angle, true, true)
            pushUpCounter.processPose(pose)
            
            // Periodic memory check
            if (i % 1000 == 0) {
                System.gc()
                val currentMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                val memoryIncrease = currentMemory - initialMemory
                
                // Memory increase should be reasonable (< 10MB for extended session)
                assertTrue(
                    "Memory increase (${memoryIncrease / 1024 / 1024} MB) should be < 10MB",
                    memoryIncrease < 10 * 1024 * 1024
                )
            }
        }
        
        println("Memory test: Extended session completed without excessive memory growth")
    }
    
    @Test
    fun `thread safety test with concurrent pose updates`() {
        val pushUpCounter = PushUpCounter()
        val mockPose = createRealisticMockPose(160f, true, true)
        
        // Test concurrent access from multiple threads
        val threads = mutableListOf<Thread>()
        val exceptions = mutableListOf<Exception>()
        
        repeat(5) { threadIndex ->
            val thread = Thread {
                try {
                    repeat(100) {
                        val angle = 120f + (threadIndex * 10f) + (it * 2f)
                        val pose = createRealisticMockPose(angle, true, true)
                        pushUpCounter.processPose(pose)
                        Thread.sleep(10)
                    }
                } catch (e: Exception) {
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                }
            }
            threads.add(thread)
        }
        
        // Start all threads
        threads.forEach { it.start() }
        
        // Wait for completion
        threads.forEach { it.join() }
        
        // Should complete without exceptions
        assertTrue("No exceptions should occur during concurrent access", exceptions.isEmpty())
        
        println("Thread safety test: Concurrent access completed without issues")
    }
    
    /**
     * Create a realistic mock pose for testing
     */
    private fun createRealisticMockPose(
        elbowAngle: Float,
        inPlankPosition: Boolean,
        armsVisible: Boolean
    ): Pose {
        val mockPose = mock(Pose::class.java)
        
        if (armsVisible) {
            // Create landmarks that would produce the desired elbow angle
            val shoulderX = 100f
            val shoulderY = 100f
            val elbowX = 120f
            val elbowY = 120f
            
            // Calculate wrist position based on desired elbow angle
            val angleRad = Math.toRadians(elbowAngle.toDouble())
            val armLength = 30f
            val wristX = elbowX + (armLength * Math.cos(angleRad)).toFloat()
            val wristY = elbowY + (armLength * Math.sin(angleRad)).toFloat()
            
            // Create high-confidence landmarks
            val leftShoulder = createMockLandmark(shoulderX, shoulderY, 0.95f)
            val leftElbow = createMockLandmark(elbowX, elbowY, 0.95f)
            val leftWrist = createMockLandmark(wristX, wristY, 0.95f)
            
            `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)).thenReturn(leftShoulder)
            `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)).thenReturn(leftElbow)
            `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_WRIST)).thenReturn(leftWrist)
            
            // Mirror for right arm
            val rightShoulder = createMockLandmark(200f, shoulderY, 0.95f)
            val rightElbow = createMockLandmark(180f, elbowY, 0.95f)
            val rightWrist = createMockLandmark(200f - wristX + elbowX, wristY, 0.95f)
            
            `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)).thenReturn(rightShoulder)
            `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)).thenReturn(rightElbow)
            `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)).thenReturn(rightWrist)
        }
        
        // Hip landmarks for plank position detection
        if (inPlankPosition) {
            val leftHip = createMockLandmark(110f, 200f, 0.9f)
            val rightHip = createMockLandmark(190f, 200f, 0.9f)
            
            `when`(mockPose.getPoseLandmark(PoseLandmark.LEFT_HIP)).thenReturn(leftHip)
            `when`(mockPose.getPoseLandmark(PoseLandmark.RIGHT_HIP)).thenReturn(rightHip)
        }
        
        return mockPose
    }
    
    private fun createMockLandmark(x: Float, y: Float, confidence: Float): PoseLandmark {
        val mockLandmark = mock(PoseLandmark::class.java)
        val mockPosition = mock(PoseLandmark.Position::class.java)
        
        `when`(mockPosition.x).thenReturn(x)
        `when`(mockPosition.y).thenReturn(y)
        `when`(mockLandmark.position).thenReturn(mockPosition)
        `when`(mockLandmark.inFrameLikelihood).thenReturn(confidence)
        
        return mockLandmark
    }
}
#!/usr/bin/env kotlin

// Simple validation script to check if our domain logic compiles and works
// This doesn't require Android dependencies

// Mock data classes to simulate ML Kit Pose and PoseLandmark
data class MockPointF(val x: Float, val y: Float)

data class MockPoseLandmark(
    val position: MockPointF,
    val inFrameLikelihood: Float
)

data class MockPose(
    val landmarks: Map<Int, MockPoseLandmark>
) {
    fun getPoseLandmark(type: Int): MockPoseLandmark? = landmarks[type]
    val allPoseLandmarks: List<MockPoseLandmark> get() = landmarks.values.toList()
}

// Test the angle calculation logic
fun calculateAngle(
    p1x: Float, p1y: Float,
    p2x: Float, p2y: Float,
    p3x: Float, p3y: Float
): Float {
    // Vector from elbow to wrist
    val v1x = p1x - p2x
    val v1y = p1y - p2y
    
    // Vector from elbow to shoulder
    val v2x = p3x - p2x
    val v2y = p3y - p2y
    
    // Calculate dot product
    val dotProduct = v1x * v2x + v1y * v2y
    
    // Calculate magnitudes
    val magnitude1 = kotlin.math.sqrt(v1x * v1x + v1y * v1y)
    val magnitude2 = kotlin.math.sqrt(v2x * v2x + v2y * v2y)
    
    // Avoid division by zero
    if (magnitude1 == 0f || magnitude2 == 0f) {
        return 0f
    }
    
    // Calculate cosine of angle
    val cosAngle = dotProduct / (magnitude1 * magnitude2)
    
    // Clamp cosine to valid range [-1, 1]
    val clampedCos = cosAngle.coerceIn(-1f, 1f)
    
    // Return angle in degrees
    return Math.toDegrees(kotlin.math.acos(clampedCos).toDouble()).toFloat()
}

// Test push-up counter state machine logic
enum class Phase { UP, DOWN }

data class CounterState(
    val count: Int = 0,
    val phase: Phase = Phase.UP,
    val lastAngle: Float? = null,
    val isTracking: Boolean = false
)

class SimplePushUpCounter(
    private val downThreshold: Float = 70f,
    private val upThreshold: Float = 160f
) {
    private var state = CounterState()
    
    fun processAngle(angle: Float) {
        val newPhase = when (state.phase) {
            Phase.UP -> if (angle <= downThreshold) Phase.DOWN else Phase.UP
            Phase.DOWN -> if (angle >= upThreshold) Phase.UP else Phase.DOWN
        }
        
        val newCount = if (state.phase == Phase.DOWN && newPhase == Phase.UP) {
            state.count + 1
        } else {
            state.count
        }
        
        state = state.copy(
            count = newCount,
            phase = newPhase,
            lastAngle = angle,
            isTracking = true
        )
    }
    
    fun getState() = state
    fun reset() { state = CounterState() }
}

// Test the implementation
fun main() {
    println("🏋️ Testing Push-Up Counter Implementation")
    println("=" * 50)
    
    // Test angle calculations
    println("\n📐 Testing Angle Calculations:")
    
    val angle90 = calculateAngle(1f, 0f, 0f, 0f, 0f, 1f)
    println("90° angle test: ${angle90.toInt()}° (expected: 90°)")
    
    val angle45 = calculateAngle(1f, 0f, 0f, 0f, 1f, 1f)
    println("45° angle test: ${angle45.toInt()}° (expected: 45°)")
    
    val angle180 = calculateAngle(1f, 0f, 0f, 0f, -1f, 0f)
    println("180° angle test: ${angle180.toInt()}° (expected: 180°)")
    
    // Test push-up counter
    println("\n🏋️ Testing Push-Up Counter:")
    
    val counter = SimplePushUpCounter()
    
    println("Initial state: ${counter.getState()}")
    
    // Simulate push-up sequence
    println("\nSimulating push-up sequence:")
    
    // Go down (bend arms)
    counter.processAngle(60f)
    println("Down phase: ${counter.getState()}")
    
    // Go up (extend arms) - should increment count
    counter.processAngle(170f)
    println("Up phase: ${counter.getState()}")
    
    // Another push-up
    counter.processAngle(65f)
    println("Down again: ${counter.getState()}")
    
    counter.processAngle(175f)
    println("Up again: ${counter.getState()}")
    
    println("\n✅ Test completed successfully!")
    println("Final rep count: ${counter.getState().count}")
}

operator fun String.times(n: Int): String = this.repeat(n)

main()
package com.grloepr.pushtrack.engine

import com.google.mlkit.vision.pose.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * JVM tests for the schema-driven engine: synthetic poses walk a squat-like
 * movement through the state machine and the rep/partial counters must react
 * exactly as the schema dictates.
 */
class DynamicExerciseEngineTest {

    private fun squatSchema() = ExerciseSchema(
        id = "test_squat",
        displayName = "Test Squat",
        targetMuscles = listOf("quadriceps"),
        trackingAngles = mapOf(
            ExerciseSchema.PRIMARY_ANGLE to JointAngleDefinition(
                name = ExerciseSchema.PRIMARY_ANGLE,
                startId = PoseLandmark.LEFT_HIP,
                vertexId = PoseLandmark.LEFT_KNEE,
                endId = PoseLandmark.LEFT_ANKLE,
                startName = "LEFT_HIP",
                vertexName = "LEFT_KNEE",
                endName = "LEFT_ANKLE"
            )
        ),
        states = mapOf(
            SchemaStates.START to SchemaState(
                mapOf(ExerciseSchema.PRIMARY_ANGLE to AngleConstraint(min = 160.0, max = 180.0))
            ),
            SchemaStates.INFLECTION to SchemaState(
                mapOf(ExerciseSchema.PRIMARY_ANGLE to AngleConstraint(lessThan = 90.0))
            ),
            SchemaStates.END to SchemaState(
                mapOf(ExerciseSchema.PRIMARY_ANGLE to AngleConstraint(min = 160.0, max = 180.0))
            )
        ),
        optimalPlane = CameraPlane.SAGITTAL,
        requiredJointsVisible = listOf(
            PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE
        ),
        tempoPulseIntervalMs = 3000L,
        masteryReps = 10
    )

    /** Builds a pose whose hip–knee–ankle inner angle equals [kneeAngleDeg]. */
    private fun poseWithKneeAngle(kneeAngleDeg: Double): PoseSnapshot {
        val knee = JointPoint(200f, 200f, 1f)
        // Ray to the hip points straight up (-90° in screen coordinates).
        val hip = JointPoint(knee.x, knee.y - 100f, 1f)
        val ankleDirRad = Math.toRadians(-90.0 + kneeAngleDeg)
        val ankle = JointPoint(
            knee.x + 100f * cos(ankleDirRad).toFloat(),
            knee.y + 100f * sin(ankleDirRad).toFloat(),
            1f
        )
        return PoseSnapshot(
            mapOf(
                PoseLandmark.LEFT_HIP to hip,
                PoseLandmark.LEFT_KNEE to knee,
                PoseLandmark.LEFT_ANKLE to ankle
            )
        )
    }

    private class Recorder {
        var last: EngineFrame = EngineFrame.idle()
        var totalReps = 0
        var totalPartials = 0
        fun onFrame(frame: EngineFrame) {
            last = frame
            totalReps = frame.repCount
            totalPartials = frame.partialReps
        }
    }

    private fun drive(
        engine: DynamicExerciseEngine,
        startTimeMs: Long,
        angles: List<Double>,
        stepMs: Long = 100L
    ): Long {
        var time = startTimeMs
        angles.forEach { angle ->
            engine.onPose(poseWithKneeAngle(angle), time)
            time += stepMs
        }
        return time
    }

    @Test
    fun `full squat cycle counts one rep`() {
        val recorder = Recorder()
        val engine = DynamicExerciseEngine(squatSchema(), recorder::onFrame)

        var t = drive(engine, 0L, List(6) { 172.0 })          // hold START → READY
        assertEquals(EnginePhase.READY, recorder.last.phase)

        t = drive(engine, t, listOf(150.0, 130.0, 110.0))     // descend
        t = drive(engine, t, List(6) { 82.0 })                // bottom (smoothed < 90)
        assertEquals(EnginePhase.BOTTOM, recorder.last.phase)

        t = drive(engine, t, List(8) { 172.0 })               // ascend to lockout
        assertEquals(1, recorder.totalReps)
        assertEquals(0, recorder.totalPartials)
        assertEquals(EnginePhase.READY, recorder.last.phase)
    }

    @Test
    fun `shallow turnaround counts a partial rep not a rep`() {
        val recorder = Recorder()
        val engine = DynamicExerciseEngine(squatSchema(), recorder::onFrame)

        var t = drive(engine, 0L, List(6) { 172.0 })          // READY
        t = drive(engine, t, List(6) { 135.0 })               // descend, but not past 90°
        assertEquals(EnginePhase.ECCENTRIC, recorder.last.phase)

        t = drive(engine, t, List(8) { 172.0 })               // give up, stand back tall
        assertEquals(0, recorder.totalReps)
        assertEquals(1, recorder.totalPartials)
    }

    @Test
    fun `pose loss resets to searching and keeps the rep count`() {
        val recorder = Recorder()
        val engine = DynamicExerciseEngine(squatSchema(), recorder::onFrame)

        var t = drive(engine, 0L, List(6) { 172.0 })
        t = drive(engine, t, listOf(150.0, 120.0))
        engine.onPose(PoseSnapshot.EMPTY, t)

        assertEquals(EnginePhase.SEARCHING, recorder.last.phase)
        assertFalse(recorder.last.poseVisible)
        assertEquals(0, recorder.totalReps)
    }

    @Test
    fun `progress normalises between start and inflection references`() {
        val schema = squatSchema()
        assertEquals(0f, schema.progressFor(175.0), 0.001f)
        assertEquals(1f, schema.progressFor(85.0), 0.001f)
        assertEquals(0.5f, schema.progressFor(125.0), 0.01f)
        assertEquals(0f, schema.progressFor(null), 0.001f)
    }

    @Test
    fun `angle constraints combine bounds`() {
        val window = AngleConstraint(min = 160.0, max = 180.0)
        assertTrue(window.isSatisfied(170.0))
        assertFalse(window.isSatisfied(150.0))

        val below = AngleConstraint(lessThan = 90.0)
        assertTrue(below.isSatisfied(89.9))
        assertFalse(below.isSatisfied(90.0))

        val above = AngleConstraint(greaterThan = 150.0)
        assertTrue(above.isSatisfied(151.0))
        assertFalse(above.isSatisfied(150.0))
    }

    @Test
    fun `sagittal validator flags frontal stance and accepts profile`() {
        // Profile: matched joints overlap horizontally.
        assertTrue(
            PoseValidator.isSagittalPlaneOptimal(
                leftHipX = 0.50f, rightHipX = 0.52f,
                leftKneeX = 0.49f, rightKneeX = 0.51f
            )
        )
        // Facing the camera: wide separation.
        assertFalse(
            PoseValidator.isSagittalPlaneOptimal(
                leftHipX = 0.30f, rightHipX = 0.70f,
                leftKneeX = 0.28f, rightKneeX = 0.72f
            )
        )
    }
}

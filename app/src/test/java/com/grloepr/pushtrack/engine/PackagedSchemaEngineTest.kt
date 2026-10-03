package com.grloepr.pushtrack.engine

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.math.*

/** Actual bundled JSON + production decoder/engine. Synthetic coherent limb chains, not ML Kit accuracy. */
@RunWith(Parameterized::class)
class PackagedSchemaEngineTest(private val schema: ExerciseSchema) {
    companion object {
    @JvmStatic @Parameterized.Parameters(name = "{0}")
    fun schemas(): List<Array<ExerciseSchema>> {
        val directory = listOf(File("src/main/assets/exercises"), File("app/src/main/assets/exercises"))
            .first { it.isDirectory }
        val files = directory.listFiles()!!.filter { it.extension == "json" }.sortedBy { it.name }
        assertEquals(10, files.size)
        return files.map { arrayOf(SchemaParser.parse(JSONObject(it.readText()))) }
    }
    }
    private fun point(origin: JointPoint, toward: JointPoint, angle: Double): JointPoint {
        val direction = atan2((toward.y - origin.y).toDouble(), (toward.x - origin.x).toDouble()) + Math.toRadians(angle)
        return JointPoint(origin.x + (100 * cos(direction)).toFloat(), origin.y + (100 * sin(direction)).toFloat(), 1f)
    }
    private fun fixture(schema: ExerciseSchema, inflection: Boolean, primaryOverride: Double? = null): Map<Int, JointPoint> {
        val state = schema.states.getValue(if (inflection) SchemaStates.INFLECTION else SchemaStates.START)
        fun desired(vertex: String): Double {
            if (primaryOverride != null && vertex == schema.primaryAngle.vertexName) return primaryOverride
            val definitions = schema.trackingAngles.values.filter { it.vertexName == vertex }
            val constraints = definitions.mapNotNull { state.constraints[it.name] } +
                if (schema.isHold && inflection) definitions.mapNotNull { schema.states.getValue(SchemaStates.START).constraints[it.name] } else emptyList()
            return (20..175).map { it.toDouble() }.firstOrNull { angle -> constraints.all { it.isSatisfied(angle - 1.0) && it.isSatisfied(angle + 1.0) } }
                ?: error("No coherent fixture angle for ${schema.id}/$vertex")
        }
        val shoulder = JointPoint(300f, 300f, 1f)
        val elbow = JointPoint(400f, 300f, 1f)
        val wrist = point(elbow, shoulder, desired("LEFT_ELBOW"))
        val hip = point(shoulder, elbow, desired("LEFT_SHOULDER"))
        val knee = point(hip, shoulder, desired("LEFT_HIP"))
        val ankle = point(knee, hip, desired("LEFT_KNEE"))
        val named = mapOf("LEFT_SHOULDER" to shoulder, "LEFT_ELBOW" to elbow, "LEFT_WRIST" to wrist,
            "LEFT_HIP" to hip, "LEFT_KNEE" to knee, "LEFT_ANKLE" to ankle)
        val result = mutableMapOf<Int, JointPoint>()
        schema.trackingAngles.values.forEach { definition ->
            listOf(definition.startId to definition.startName, definition.vertexId to definition.vertexName,
                definition.endId to definition.endName).forEach { (id, name) -> result[id] = named.getValue(name) }
        }
        schema.requiredJointsVisible.forEach { id -> result.putIfAbsent(id, JointPoint(450f + id * 3, 400f, 1f)) }
        schema.trackingAngles.forEach { (name, definition) ->
            val angle = calculateAngle(result[definition.startId], result[definition.vertexId], result[definition.endId])
            assertNotNull("${schema.id}: degenerate $name", angle)
            if (primaryOverride == null) state.constraints[name]?.let { assertTrue("${schema.id}: $name=$angle", it.isSatisfied(angle!!)) }
        }
        return result
    }
    @Test fun everyPackagedSchemaCountsItsCoherentMovementAndRejectsInterruptedInvalidStreams() {
        run {
            var frame = EngineFrame.idle()
            val engine = DynamicExerciseEngine(schema) { frame = it }
            var time = 0L
            val start = PoseSnapshot(fixture(schema, false))
            val bottomMap = fixture(schema, true)
            val bottom = PoseSnapshot(bottomMap)
            fun feed(pose: PoseSnapshot, duration: Long) {
                repeat((duration / 100).toInt()) { engine.onPose(pose, time); time += 100 }
            }
            fun finish() {
                feed(start, 1500)
                feed(bottom, (schema.holdTargetMs ?: 1000L) + 1500)
                if (!schema.isHold) feed(start, 2000)
            }
            finish()
            assertEquals("${schema.id}: positive sequence", 1, frame.repCount)
            val previous = frame
            engine.onPose(bottom, time - 100)
            engine.onPose(bottom, time - 200)
            assertEquals("${schema.id}: stale callback mutated state", previous, frame)
            engine.interrupt(time)
            assertEquals("${schema.id}: interrupt preserves completion", 1, frame.repCount)
            assertEquals(EnginePhase.SEARCHING, frame.phase)
            engine.reset()
            assertEquals(0, frame.repCount)
            feed(start, 1500)
            feed(bottom, 100)
            engine.interrupt(time)
            // A lost frame or interrupted partial attempt cannot become completed on return.
            feed(PoseSnapshot.EMPTY, 500)
            assertEquals(0, frame.repCount)
            val missing = bottomMap - schema.primaryAngle.vertexId
            feed(PoseSnapshot(missing), (schema.holdTargetMs ?: 1000L) + 1000)
            assertEquals("${schema.id}: missing scoring joint", 0, frame.repCount)
            val invalid = bottomMap.mapValues { (_, value) -> value.copy(x = Float.NaN) }
            feed(PoseSnapshot(invalid), 1000)
            assertEquals("${schema.id}: invalid geometry", 0, frame.repCount)
            assertFalse(frame.poseVisible)
            engine.reset()
            val startConstraint = schema.states.getValue(SchemaStates.START).constraints.getValue(schema.primaryAngleName)
            val bottomConstraint = schema.states.getValue(SchemaStates.INFLECTION).constraints.getValue(schema.primaryAngleName)
            val wrongAngle = (5..175).map { it.toDouble() }.first {
                !startConstraint.isSatisfied(it - 1) && !startConstraint.isSatisfied(it + 1) &&
                    !bottomConstraint.isSatisfied(it - 1) && !bottomConstraint.isSatisfied(it + 1)
            }
            val wrong = PoseSnapshot(fixture(schema, false, wrongAngle))
            feed(wrong, (schema.holdTargetMs ?: 1000L) + 2000)
            assertTrue("${schema.id}: finite wrong pose must remain visible", frame.poseVisible)
            assertEquals("${schema.id}: visible wrong posture cannot count", 0, frame.repCount)
            assertEquals(EnginePhase.SEARCHING, frame.phase)
            engine.reset()
            finish()
            assertEquals("${schema.id}: repeat after reset", 1, frame.repCount)
        }
    }
}

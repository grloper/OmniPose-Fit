package com.grloepr.pushtrack

import androidx.test.platform.app.InstrumentationRegistry
import com.grloepr.pushtrack.engine.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Exercises the actual packaged catalog and Android JSON parser, not a copied test schema. */
class ExerciseCatalogInstrumentedTest {
    @Test fun everyBundledExerciseParsesAndRejectsMissingPoseWithoutInventedCounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = context.assets.list("exercises")!!.filter { it.endsWith(".json") }
        assertEquals("An omitted/corrupt packaged exercise must not silently disappear", 10, files.size)
        val ids = mutableSetOf<String>()
        for (file in files) {
            val json = context.assets.open("exercises/$file").bufferedReader().use { it.readText() }
            val schema = SchemaParser.parse(JSONObject(json))
            assertTrue("Duplicate schema: $file", ids.add(schema.id))
            assertTrue(schema.displayName.isNotBlank())
            assertTrue(schema.masteryReps > 0)
            assertTrue(schema.tempoPulseIntervalMs > 0L)
            assertTrue(schema.trackingAngles.isNotEmpty())
            assertTrue(schema.requiredJointsVisible.isNotEmpty())
            assertNotNull(schema.states[SchemaStates.START])
            assertNotNull(schema.states[SchemaStates.INFLECTION])
            var frame = EngineFrame.idle()
            val engine = DynamicExerciseEngine(schema) { frame = it }
            repeat(20) { engine.onPose(PoseSnapshot.EMPTY, it * 100L) }
            assertEquals("Missing pose counted $file", 0, frame.repCount)
            assertEquals(0, frame.partialReps)
            assertFalse(frame.poseVisible)
            assertEquals(EnginePhase.SEARCHING, frame.phase)
            engine.reset()
            assertEquals(0, frame.repCount)
            assertEquals(schema, ExerciseLibrary.get(context, schema.id))
        }
        assertEquals(ids, ExerciseLibrary.all(context).map { it.id }.toSet())
    }
}

package com.grloepr.pushtrack.practice

import com.grloepr.pushtrack.progression.CalisthenicsSkillGraph
import org.junit.Assert.*
import org.junit.Test

class MotionTrackingLimitationsTest {
    @Test fun everyCatalogExerciseExplainsEstimateAndVisibilityLimits() {
        for (node in CalisthenicsSkillGraph.nodes) {
            val text = MotionTrackingLimitations.forExercise(node)
            assertTrue(text.contains("counts do not certify technique"))
            assertTrue(text.contains("left-side joints"))
            assertTrue(text.contains("manual practice"))
            assertEquals(node.schemaId != node.id, text.contains("shared motion model"))
        }
    }
    @Test fun holdDescriptionsDoNotClaimUnmeasuredSupportOrGravity() {
        fun text(id: String) = MotionTrackingLimitations.forExercise(CalisthenicsSkillGraph.byId(id)!!)
        assertTrue(text("handstand").contains("do not verify inversion"))
        assertTrue(text("wall_handstand").contains("wall support"))
        assertTrue(text("dead_hang").contains("whether your weight is supported"))
        assertTrue(text("front_lever").contains("relative to gravity"))
        assertTrue(text("tuck_front_lever").contains("relative to gravity"))
    }
}

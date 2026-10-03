package com.grloepr.pushtrack.practice

import com.grloepr.pushtrack.progression.SkillNode

/** Describes implemented angle checks, not independently verified exercise recognition. */
object MotionTrackingLimitations {
    fun forExercise(node: SkillNode): String {
        val detail = when (node.schemaId) {
            "handstand", "wall_handstand" -> "Joint angles do not verify inversion, balance or wall support."
            "dead_hang" -> "Joint angles do not verify a bar, grip or whether your weight is supported."
            "front_lever", "tuck_front_lever" -> "Joint angles do not verify a horizontal hold relative to gravity or bar support."
            "frog_stand" -> "Joint angles do not verify balance, hand support or feet leaving the ground."
            "scapular_pulls" -> "Joint angles do not verify bar support or isolated shoulder-blade movement."
            "pullup" -> "Joint angles do not verify bar contact, assistance or clearance above the bar."
            "pushup" -> "Joint angles do not verify hand placement, contact with a wall or floor, or how your weight is supported."
            "squat" -> "Joint angles do not verify squat depth relative to the ground, load or balance."
            else -> "This exercise has no dedicated recognition validation."
        }
        val variant = if (node.schemaId != node.id)
            " This variation uses a shared motion model and is not independently recognized." else ""
        return "Experimental joint-position estimate; counts do not certify technique. $detail$variant " +
            "The current model uses left-side joints; an opposite view, occlusion or missing joints can prevent counting. " +
            "Choose manual practice when tracking does not match your movement."
    }
}

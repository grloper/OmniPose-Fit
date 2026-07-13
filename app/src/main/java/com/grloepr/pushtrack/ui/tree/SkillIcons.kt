package com.grloepr.pushtrack.ui.tree

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector
import com.grloepr.pushtrack.progression.SkillIcon

/** Resolves the model-layer icon flavor to an actual vector asset. */
fun SkillIcon.asVector(): ImageVector = when (this) {
    SkillIcon.PUSH -> Icons.Rounded.FitnessCenter
    SkillIcon.PULL -> Icons.Rounded.ArrowUpward
    SkillIcon.LEGS -> Icons.Rounded.DirectionsRun
    SkillIcon.BALANCE -> Icons.Rounded.SelfImprovement
    SkillIcon.LEVER -> Icons.Rounded.Bolt
    SkillIcon.HANG -> Icons.Rounded.Anchor
    SkillIcon.CAPSTONE -> Icons.Rounded.MilitaryTech
}

package com.grloepr.pushtrack.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.grloepr.pushtrack.engine.EngineFrame

/** Shared training counter presentation, including completed holds and current hold duration. */
@Composable
fun SessionCounter(frame: EngineFrame, target: Int, holdTargetMs: Long?) {
    Box(Modifier.testTag("session-counter").semantics {
        stateDescription = "${frame.repCount} completed of $target"
    }) {
        RepCounterDial(
            repCount = if (holdTargetMs != null) (frame.holdMs / 1000L).toInt() else frame.repCount,
            goalReps = if (holdTargetMs != null) (holdTargetMs / 1000L).toInt() else target,
            progress = frame.progress,
            unitLabel = if (holdTargetMs != null) "sec hold" else "reps"
        )
    }
}

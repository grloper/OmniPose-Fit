package com.grloepr.pushtrack.ui.screen

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/** Test-only structured observation; this does not add spoken accessibility text. */
val AnalysisTimestampMs = SemanticsPropertyKey<Long>("AnalysisTimestampMs")
var SemanticsPropertyReceiver.analysisTimestampMs by AnalysisTimestampMs

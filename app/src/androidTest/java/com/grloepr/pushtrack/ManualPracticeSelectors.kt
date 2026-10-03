package com.grloepr.pushtrack

import androidx.compose.ui.test.*

/** Catalog motion chips can share text with unit controls; constrain actions to the dialog. */
internal fun SemanticsNodeInteractionsProvider.manualControl(text: String): SemanticsNodeInteraction {
    val matcher = hasText(text) and hasAnyAncestor(hasTestTag("manual-practice-dialog"))
    onAllNodes(matcher).assertCountEquals(1)
    return onNode(matcher)
}

internal fun manualGoalMatcher(): SemanticsMatcher = hasSetTextAction() and
    hasText("Personal goal", substring = true) and hasAnyAncestor(hasTestTag("manual-practice-dialog"))

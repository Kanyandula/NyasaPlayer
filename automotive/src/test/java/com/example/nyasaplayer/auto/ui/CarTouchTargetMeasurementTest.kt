package com.example.nyasaplayer.auto.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PRD §12 exit criterion 2 (NFR-1): automated measurement returns **zero** interactive controls
 * below 76dp.
 *
 * Every node with a click, long-click, toggle or set-progress (slider) action, in both the merged
 * and the unmerged tree so a clickable nested inside another is not hidden by its parent, on every
 * case in [carUiCases]. Measured on the node's own laid-out size — not its touch bounds, which Compose
 * pads out to 48dp and would pass things the design says are too small.
 *
 * Collects every violation and fails once, so one run is the whole list.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = MeasurementQualifiers)
class CarTouchTargetMeasurementTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `no interactive control on any car screen is below 76dp`() {
        val minPx = with(composeRule.density) { CarTouchTargetSize.roundToPx() }
        val density = composeRule.density.density
        val violations = mutableListOf<String>()
        var measured = 0

        composeRule.forEachCarUiCase { case ->
            val matcher = case.scope?.let { Interactive and it } ?: Interactive
            val nodes = listOf(false, true)
                .flatMap { unmerged -> composeRule.onAllNodes(matcher, unmerged).fetchSemanticsNodes() }
                .distinctBy { it.id }
            measured += nodes.size
            nodes.filter { it.size.width < minPx || it.size.height < minPx }.forEach { node ->
                violations += "${case.name} | ${node.label()} | " +
                    "${dp(node.size.width, density)} x ${dp(node.size.height, density)} dp " +
                    "at (${dp(node.boundsInWindow.left, density)}, ${dp(node.boundsInWindow.top, density)})"
            }
        }

        println("Touch targets: ${carUiCases.size} cases, $measured interactive nodes, ${violations.size} below 76dp")
        assertTrue(
            "${violations.size} interactive controls below 76dp across ${carUiCases.size} cases " +
                "(case | node | width x height):\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private companion object {
        val Interactive = SemanticsMatcher("has a click, long-click, toggle or set-progress action") {
            SemanticsActions.OnClick in it.config ||
                SemanticsActions.OnLongClick in it.config ||
                SemanticsProperties.ToggleableState in it.config ||
                SemanticsActions.SetProgress in it.config
        }

        fun dp(px: Number, density: Float): String = "%.0f".format(px.toFloat() / density)

        /** What a reader can find the node by: its own text or description, else its nearest child's. */
        fun SemanticsNode.label(): String {
            val found = generateSequence(listOf(this)) { level -> level.flatMap { it.children }.ifEmpty { null } }
                .flatten()
                .map { it.describe() }
                .firstOrNull { it.isNotEmpty() }
            val role = config.getOrNull(SemanticsProperties.Role)
            val disabled = if (SemanticsProperties.Disabled in config) " (disabled)" else ""
            return (found ?: "(unlabelled${role?.let { " $it" }.orEmpty()})") + disabled
        }

        fun SemanticsNode.describe(): String = listOfNotNull(
            config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.let { "text=\"$it\"" },
            config.getOrNull(SemanticsProperties.EditableText)?.let { "field=\"$it\"" },
            config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")?.let { "desc=\"$it\"" },
            config.getOrNull(SemanticsProperties.TestTag)?.let { "tag=$it" },
        ).joinToString(" ")
    }
}

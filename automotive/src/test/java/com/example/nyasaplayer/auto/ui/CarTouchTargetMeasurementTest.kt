package com.example.nyasaplayer.auto.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import org.junit.Assert.assertEquals
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
    fun `no interactive control on any car screen is below 76dp or clipped`() {
        val minPx = with(composeRule.density) { CarTouchTargetSize.roundToPx() }
        val density = composeRule.density.density
        val violations = mutableListOf<String>()
        val clipped = mutableListOf<String>()
        var measured = 0
        var frames = 0

        composeRule.forEachCarUiCase { case ->
            frames++
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
            nodes.filter { it.isClipped() }.forEach { node ->
                clipped += "${case.name} | ${node.label()} | laid out " +
                    "${dp(node.size.width, density)} x ${dp(node.size.height, density)} dp, " +
                    "visible ${dp(node.boundsInWindow.width, density)} x " +
                    "${dp(node.boundsInWindow.height, density)} dp"
            }
        }

        println(
            "Touch targets: ${carUiCases.size} cases in $frames frames, $measured interactive nodes, " +
                "${violations.size} below 76dp, ${clipped.size} clipped",
        )
        assertTrue(
            "${violations.size} interactive controls below 76dp across ${carUiCases.size} cases " +
                "(case | node | width x height):\n" + violations.joinToString("\n") +
                "\n${clipped.size} interactive controls drawn only in part, so a driver cannot read " +
                "them whole (case | node | laid out | visible):\n" + clipped.joinToString("\n"),
            violations.isEmpty() && clipped.isEmpty(),
        )
    }

    /**
     * The cramped confirmation fits its card **without scrolling**.
     *
     * `CarModalCard` scrolls as a last resort, and the clipping check above exempts anything inside
     * a scroll container — so between them they would report a squashed modal as healthy. This is
     * the assertion that fails if the compact padding and width stop carrying the card: a card that
     * has to scroll is the fallback doing the work, not the fix.
     */
    @Test
    fun `the cramped remove-all confirmation fits its card without scrolling`() {
        val cramped = carUiCases.filter { "cramped slot" in it.name }
        check(cramped.size == 1) { "expected one cramped case, found ${cramped.map { it.name }}" }

        composeRule.forEachCarUiCase(cramped) { case ->
            val card = composeRule
                .onAllNodes(IsModalCard, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .singleOrNull()
            checkNotNull(card) { "${case.name}: expected one scrollable card holding the dialog" }
            val range = card.config[SemanticsProperties.VerticalScrollAxisRange]
            assertEquals(
                "${case.name}: the card scrolls, so its content does not fit the slot",
                0f,
                range.maxValue(),
                0f,
            )
        }
    }

    private companion object {
        /** The modal card: the one vertically scrollable node holding the dialog's title. */
        val IsModalCard = SemanticsMatcher("is the scrollable modal card") { node ->
            SemanticsProperties.VerticalScrollAxisRange in node.config &&
                generateSequence(listOf(node)) { level -> level.flatMap { it.children }.ifEmpty { null } }
                    .flatten()
                    .any { child ->
                        child.config.getOrNull(SemanticsProperties.Text)
                            ?.any { "Remove all downloads?" in it.text } == true
                    }
        }

        val Interactive = SemanticsMatcher("has a click, long-click, toggle or set-progress action") {
            SemanticsActions.OnClick in it.config ||
                SemanticsActions.OnLongClick in it.config ||
                SemanticsProperties.ToggleableState in it.config ||
                SemanticsActions.SetProgress in it.config
        }

        fun dp(px: Number, density: Float): String = "%.0f".format(px.toFloat() / density)

        /**
         * True when a container that cannot scroll cuts the node off: its laid-out size is larger
         * than what the window shows of it. Such a control still takes a tap, so only measurement
         * catches it — the Remove-all confirmation's buttons were cut in half on a 768dp-tall head
         * unit and no gate noticed (T30).
         *
         * A row half out of a scrolling list is not this: the driver scrolls and sees it whole. Only
         * clipping with no scroll to undo it counts.
         */
        fun SemanticsNode.isClipped(): Boolean {
            val slack = 1f
            val cut = boundsInWindow.width < size.width - slack ||
                boundsInWindow.height < size.height - slack
            return cut && !inScrollable()
        }

        fun SemanticsNode.inScrollable(): Boolean = generateSequence(parent) { it.parent }.any {
            SemanticsActions.ScrollBy in it.config ||
                SemanticsActions.ScrollToIndex in it.config ||
                SemanticsProperties.VerticalScrollAxisRange in it.config ||
                SemanticsProperties.HorizontalScrollAxisRange in it.config
        }

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

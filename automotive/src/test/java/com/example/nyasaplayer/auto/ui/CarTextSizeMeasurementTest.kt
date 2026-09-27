package com.example.nyasaplayer.auto.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnitType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * PRD §12 exit criterion 1, the part of `docs/aaos-DESIGN.md` → Typography that a measurement can
 * settle: no text renders below 14sp, and no interactive label below 18sp.
 *
 * Read from what was actually laid out — `GetTextLayoutResult`'s style — not from the source, so a
 * size inherited from a theme or overridden downstream is judged as the driver sees it.
 *
 * The 18sp floor is for **a control's own label** — the single piece of text inside a button or
 * pill, which is the whole of what a driver reads to know what it does. It is not for every string
 * inside something clickable: a track row is one big control, and its artist line and duration are
 * metadata the design sets at 15 and 16sp deliberately. So the floor applies when the nearest
 * clickable ancestor holds exactly one text.
 *
 * Rail tabs used to be exempt, because an 80dp rail could not hold "Favourites" at 18sp. The rail
 * is 176dp now and its labels sit beside their icons at 18sp, so the exemption is gone and NFR-3
 * is enforced as written — shrinking a rail label back below the floor fails here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = MeasurementQualifiers)
class CarTextSizeMeasurementTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `no car text is below 14sp and no interactive label below 18sp`() {
        val violations = mutableListOf<String>()
        var measured = 0
        var skipped = 0
        var frames = 0

        composeRule.forEachCarUiCase { case ->
            frames++
            val matcher = case.scope?.let { HasText and it } ?: HasText
            composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val text = node.text()
                if (text.isBlank()) return@forEach
                // No layout result, or a size in Em or unspecified: unmeasurable, and counted as
                // such rather than passing quietly. Compose's own 14sp default would clear the
                // text floor but not the 18sp one.
                val sp = node.fontSizeSp() ?: run {
                    skipped++
                    return@forEach
                }
                measured++
                val interactive = node.isInteractiveLabel()
                val floor = if (interactive) InteractiveLabelFloorSp else TextFloorSp
                if (sp < floor - Slack) {
                    val kind = if (interactive) "interactive label" else "text"
                    // The message is interpolated, never a format string: the text it quotes can
                    // contain a percent. Locale.ROOT so a comma-decimal locale still prints "15.5".
                    val shown = "%.1f".format(Locale.ROOT, sp).removeSuffix(".0")
                    violations += "${case.name} | \"$text\" | $kind at ${shown}sp, floor ${floor.toInt()}sp"
                }
            }
        }

        println(
            "Text size: ${carUiCases.size} cases in $frames frames, $measured text nodes measured, " +
                "$skipped with no measurable size, ${violations.size} below their floor",
        )
        assertTrue(
            "${violations.size} texts below the type floors across ${carUiCases.size} cases " +
                "(case | text | measured | floor):\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private companion object {
        const val TextFloorSp = 14f
        const val InteractiveLabelFloorSp = 18f

        /** Sub-point differences are rounding in the layout, not a design decision. */
        const val Slack = 0.5f

        val HasText = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

        fun SemanticsNode.text(): String =
            config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.trim().orEmpty()

        /** The size the text was laid out at, asked of the node itself rather than assumed. */
        fun SemanticsNode.fontSizeSp(): Float? {
            val action = config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: return null
            val results = mutableListOf<TextLayoutResult>()
            action.action?.invoke(results)
            val size = results.firstOrNull()?.layoutInput?.style?.fontSize ?: return null
            return if (size.type == TextUnitType.Sp) size.value else null
        }

        fun SemanticsNode.isInteractiveLabel(): Boolean {
            val control = generateSequence(this) { it.parent }.firstOrNull { node ->
                SemanticsActions.OnClick in node.config ||
                    SemanticsActions.OnLongClick in node.config ||
                    SemanticsProperties.ToggleableState in node.config
            } ?: return false
            return control.textDescendants() == 1
        }

        /** How many separate texts the control holds: one means this text is its label. */
        fun SemanticsNode.textDescendants(): Int =
            generateSequence(listOf(this)) { level -> level.flatMap { it.children }.ifEmpty { null } }
                .flatten()
                .count { SemanticsProperties.Text in it.config }
    }
}

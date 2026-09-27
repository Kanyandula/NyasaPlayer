package com.example.nyasaplayer.auto.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * No text a driver reads is cut off by the space its screen gives it.
 *
 * The sibling suites measure how big text is, how legible against its background and how large a
 * target it sits in. None of them asks whether it is *whole*: an ellipsised string lays out at its
 * truncated size, so its bounds equal its size and every existing gate passes it. The full player
 * shipped a 48sp title in a 536dp column and cut "I Will Sing of Your Joy Forever" down to "I will
 * sing of your joy for…" on the AVD, green the whole way.
 *
 * Read from what was laid out — `GetTextLayoutResult`'s `hasVisualOverflow` — so it judges the
 * string the driver actually sees, whatever maxLines or width the source asked for.
 *
 * **What is exempt.** Truncation is a legitimate design choice for metadata that has no bound: an
 * album name, a queue's origin. The fixtures push those past any real length on purpose, so those
 * exact strings are exempt by name in [DeliberatelyLong] and nothing else is. A title, a label, a
 * button's word, a real artist name: none of those may be cut. Lengthening a fixture is therefore
 * a deliberate act — either the layout holds it, or the string joins the list with a reason.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = MeasurementQualifiers)
class CarTextOverflowMeasurementTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `no car text is cut off by the space its screen gives it`() {
        val violations = mutableListOf<String>()
        var measured = 0
        var skipped = 0
        var exempt = 0
        var frames = 0

        composeRule.forEachCarUiCase { case ->
            frames++
            val matcher = case.scope?.let { HasText and it } ?: HasText
            composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val text = node.text()
                if (text.isBlank()) return@forEach
                // No layout result: unmeasurable, and counted as such rather than passing quietly.
                val overflows = node.overflows() ?: run {
                    skipped++
                    return@forEach
                }
                measured++
                if (text in DeliberatelyLong) {
                    exempt++
                    return@forEach
                }
                // Interpolated, never a format string: the text it quotes can contain a percent.
                if (overflows) violations += "${case.name} | \"$text\""
            }
        }

        println(
            "Text overflow: ${carUiCases.size} cases in $frames frames, $measured text nodes " +
                "measured, $skipped with no layout result, $exempt exempt, ${violations.size} cut off",
        )
        assertTrue(
            "${violations.size} texts cut off across ${carUiCases.size} cases (case | text):\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private companion object {
        /**
         * Fixture strings deliberately longer than any real one, so a label is measured at its
         * widest. They are expected to ellipsise; every other string is not.
         */
        val DeliberatelyLong = setOf(
            // The queue origin on the full player's top bar, beside a 76dp collapse and a 76dp like.
            "Late Night Drive Through the Rift Valley Highlands",
        )

        val HasText = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

        fun SemanticsNode.text(): String =
            config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.trim().orEmpty()

        /** Whether the layout cut the text, asked of the node itself rather than assumed. */
        fun SemanticsNode.overflows(): Boolean? {
            val action = config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: return null
            val results = mutableListOf<TextLayoutResult>()
            action.action?.invoke(results)
            return results.firstOrNull()?.hasVisualOverflow
        }
    }
}

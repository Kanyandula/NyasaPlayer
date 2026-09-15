package com.example.nyasaplayer.auto.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * PRD §12 exit criterion 3 (NFR-2): every non-disabled text/surface pair measures at least 7:1.
 *
 * Measured from rendered pixels, not from tokens, because a token table cannot see what text
 * actually lands on — alpha, washes, gradients and the ambient layer. For each text node on every
 * case in [carUiCases]: the background is the most frequent colour inside the node's bounds, the
 * foreground is the pixel with the highest contrast against it (anti-aliasing only ever lowers
 * contrast, so that pixel is the glyph colour), and the ratio is WCAG 2.x's.
 *
 * Text on a disabled node or under one is exempt, as the design exempts disabled text. The only
 * other exceptions are the two destructive pairs the design records at AA — see [isRecordedAaPair].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = MeasurementQualifiers)
class CarTextContrastMeasurementTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `every non-disabled text on every car screen measures at least 7 to 1`() {
        val violations = mutableListOf<String>()
        var measured = 0
        var exempt = 0
        var clipped = 0

        composeRule.forEachCarUiCase { case ->
            val window = composeRule.captureWindow()
            val matcher = case.scope?.let { HasText and it } ?: HasText
            composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val text = node.text()
                if (text.isBlank()) return@forEach
                if (node.isDisabledOrUnderDisabled()) {
                    exempt++
                    return@forEach
                }
                val pair = window.measure(node) ?: run {
                    clipped++
                    return@forEach
                }
                measured++
                if (pair.ratio < AaaRatio && !isRecordedAaPair(case.name, text, pair.ratio)) {
                    violations += "${case.name} | \"$text\" | fg ${hex(pair.fg)} | bg ${hex(pair.bg)} | " +
                        "%.2f:1".format(pair.ratio)
                }
            }
        }

        println(
            "Contrast: ${carUiCases.size} cases, $measured text nodes measured, $exempt disabled (exempt), " +
                "$clipped partly out of view (skipped), ${violations.size} below 7:1",
        )
        assertTrue(
            "${violations.size} text/surface pairs below 7:1 across ${carUiCases.size} cases " +
                "(case | text | foreground | background | ratio):\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /** The ratio function checked against values the design doc and WCAG publish. */
    @Test
    fun `contrast ratio matches WCAG reference values`() {
        assertEquals(21.0, contrastRatio(WHITE, BLACK), 0.001)
        // docs/aaos-DESIGN.md: secondary #ACACBC on raised #1E1E2A is 7.4:1.
        assertEquals(7.4, contrastRatio(0xFFACACBC.toInt(), 0xFF1E1E2A.toInt()), 0.05)
        assertEquals(1.0, contrastRatio(WHITE, WHITE), 0.001)
    }

    private class Measured(val fg: Int, val bg: Int, val ratio: Double)

    /**
     * The node's visible pixels. Background is the most frequent colour on the box's edge rather than
     * across the whole box: a large glyph over a gradient can out-number any one background shade.
     */
    private fun Bitmap.measure(node: SemanticsNode): Measured? {
        val bounds = node.boundsInWindow
        val left = bounds.left.roundToInt().coerceIn(0, width)
        val top = bounds.top.roundToInt().coerceIn(0, height)
        val right = bounds.right.roundToInt().coerceIn(0, width)
        val bottom = bounds.bottom.roundToInt().coerceIn(0, height)
        // Partly scrolled out of view: the visible sliver may hold no glyph at all. Every such node is
        // measured whole in another case — the unscrolled one or the scrolled-to-end one.
        if (right - left < node.size.width - 1 || bottom - top < node.size.height - 1) return null

        val w = right - left
        val h = bottom - top
        val pixels = IntArray(w * h)
        getPixels(pixels, 0, w, left, top, w, h)
        val edge = pixels.filterIndexed { i, _ -> i % w == 0 || i % w == w - 1 || i < w || i >= w * (h - 1) }
        val bg = edge.groupingBy { it }.eachCount().maxBy { it.value }.key
        val fg = pixels.distinct().maxBy { contrastRatio(it, bg) }
        return Measured(fg, bg, contrastRatio(fg, bg))
    }

    private companion object {
        const val AaaRatio = 7.0
        const val AaRatio = 4.5
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()

        val HasText = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) or
            SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

        /**
         * The two destructive pairs `docs/aaos-DESIGN.md` → "Contrast, measured" records at AA rather
         * than AAA, allowed by owner decision (2026-09-15). Each is one node, named by case and text,
         * and still has to clear AA — the level the design records for it.
         */
        fun isRecordedAaPair(case: String, text: String, ratio: Double): Boolean = ratio >= AaRatio && (
            // "Sign-out red #EF5350 on its 15% wash over chrome — 4.6:1, AA": CarSignOutRow in Settings.
            (case.startsWith("CarSettingsScreen/") && text == "Sign Out") ||
                // "White on sign-out fill #C62828 — 5.2:1, AA": CarSignOutConfirmation's confirm button.
                (case.startsWith("CarSignOutConfirmation") && text == "Sign Out")
            )

        fun SemanticsNode.text(): String =
            config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
                ?: config.getOrNull(SemanticsProperties.EditableText)?.text
                ?: ""

        fun SemanticsNode.isDisabledOrUnderDisabled(): Boolean =
            generateSequence(this) { it.parent }.any { SemanticsProperties.Disabled in it.config }

        fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

        /** WCAG 2.x relative luminance of an opaque sRGB colour. */
        fun luminance(argb: Int): Double {
            fun channel(shift: Int): Double {
                val c = (argb shr shift and 0xFF) / 255.0
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }

        fun contrastRatio(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }
    }
}

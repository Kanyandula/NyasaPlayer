package com.example.nyasaplayer.auto.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.nyasaplayer.auto.ui.theme.CarChrome
import com.example.nyasaplayer.auto.ui.theme.CarGlass
import com.example.nyasaplayer.auto.ui.theme.CarObsidian
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarSignOutRed
import com.example.nyasaplayer.auto.ui.theme.CarSignOutRedSolid
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * PRD §12 exit criterion 3 (NFR-2): every non-disabled text/surface pair measures at least 7:1.
 *
 * Measured from rendered pixels, not from tokens, because a token table cannot see what text
 * actually lands on — alpha, washes, gradients and the ambient layer. For each text node on every
 * case in [carUiCases]: the background is the most frequent colour on the edge of the node's bounds,
 * the foreground is the pixel inside them with the highest contrast against it (anti-aliasing only
 * ever lowers contrast, so that pixel is the glyph colour), and the ratio is WCAG 2.x's.
 *
 * Text on a disabled node or under one is exempt, as the design exempts disabled text. The only
 * other exceptions are the two destructive pairs the design records at AA — see [isRecordedAaPair].
 * Cases that sit on the ambient glow also run at the drift's lowest frame, not only the first, and
 * long lists are measured after every scroll step. Text only ever seen partly in view fails the run.
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
        var frames = 0
        // Text seen only partly in view, and text measured whole, by (screen state, text).
        val clipped = mutableSetOf<Pair<String, String>>()
        val whole = mutableSetOf<Pair<String, String>>()

        composeRule.forEachCarUiCase { case ->
            frames++
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
                    clipped += case.family to text
                    return@forEach
                }
                measured++
                whole += case.family to text
                if (pair.ratio < AaaRatio && !isRecordedAaPair(pair)) {
                    violations += "${case.name} | \"$text\" | fg ${hex(pair.fg)} | bg ${hex(pair.bg)} | " +
                        "%.2f:1".format(pair.ratio)
                }
            }
        }

        // A text only ever seen clipped was never measured at all.
        val neverWhole = (clipped - whole).map { (family, text) -> "$family | \"$text\"" }
        println(
            "Contrast: ${carUiCases.size} cases in $frames frames, $measured text nodes measured, " +
                "$exempt disabled (exempt), ${clipped.size} seen partly out of view, " +
                "${neverWhole.size} never measured whole, ${violations.size} below 7:1",
        )
        assertTrue(
            "${violations.size} text/surface pairs below 7:1 across ${carUiCases.size} cases " +
                "(case | text | foreground | background | ratio):\n" + violations.joinToString("\n") +
                "\n${neverWhole.size} texts only ever partly in view, so never measured (screen state | text):\n" +
                neverWhole.joinToString("\n"),
            violations.isEmpty() && neverWhole.isEmpty(),
        )
    }

    /**
     * The ambient glow's brightest pixel behind content, at the first frame and at the drift's lowest,
     * is no lighter than [CarRaised] — the surface [com.example.nyasaplayer.auto.ui.theme.CarTextSecondary]
     * is measured on at 7.4:1, so anything no lighter keeps it there.
     */
    @Test
    fun `ambient glow behind content is never lighter than CarRaised`() {
        val ceiling = luminance(CarRaised.toArgb())
        val over = mutableListOf<String>()
        composeRule.forEachCarUiCase(ambientCases) { case ->
            val window = composeRule.captureWindow()
            val slot = composeRule.onNodeWithTag(ContentSlotTag).fetchSemanticsNode().boundsInWindow
            val w = slot.width.roundToInt()
            val h = slot.height.roundToInt()
            val pixels = IntArray(w * h)
            window.getPixels(pixels, 0, w, slot.left.roundToInt(), slot.top.roundToInt(), w, h)
            val brightest = pixels.distinct().maxBy(::luminance)
            val l = luminance(brightest)
            println("Ambient: ${case.name}: brightest ${hex(brightest)}, L %.4f vs %.4f".format(l, ceiling))
            if (l > ceiling) over += "${case.name}: ${hex(brightest)}"
        }
        assertTrue("Ambient lighter than CarRaised behind content: $over", over.isEmpty())
    }

    /** The ratio function checked against values the design doc and WCAG publish. */
    @Test
    fun `contrast ratio matches WCAG reference values`() {
        assertEquals(21.0, contrastRatio(WHITE, BLACK), 0.001)
        // docs/aaos-DESIGN.md: secondary #ACACBC on raised #1E1E2A is 7.4:1.
        assertEquals(7.4, contrastRatio(0xFFACACBC.toInt(), 0xFF1E1E2A.toInt()), 0.05)
        assertEquals(1.0, contrastRatio(WHITE, WHITE), 0.001)
    }

    class Measured(val fg: Int, val bg: Int, val ratio: Double)

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
        // Partly out of view: the visible sliver may hold no glyph at all, so it is not measured here.
        // The caller records it, and fails the run unless the same text is measured whole in another
        // frame of the same screen state — another scroll step, or the unscrolled frame.
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

        /** The surfaces a destructive wash is laid over. */
        val Surfaces = listOf(CarObsidian, CarChrome, CarGlass, CarRaised, NyasaBackground)

        /**
         * The two destructive pairs `docs/aaos-DESIGN.md` → "Contrast, measured" records at AA rather
         * than AAA, allowed wherever a destructive action uses them (owner decision, 2026-09-15). Named
         * by the measured pair, not by screen, and each still has to clear AA — the level recorded.
         */
        fun isRecordedAaPair(pair: Measured): Boolean = pair.ratio >= AaRatio && (
            // CarSignOutRed text on its own 15% wash, over whichever surface the wash sits on.
            (pair.fg == CarSignOutRed.toArgb() && Surfaces.any { pair.bg.isNear(SignOutWash.compositeOver(it)) }) ||
                // White text on a CarSignOutRedSolid fill.
                (pair.fg == WHITE && pair.bg == CarSignOutRedSolid.toArgb())
            )

        val SignOutWash = CarSignOutRed.copy(alpha = 0.15f)

        /** Within rounding of the compositor: 2 per channel. */
        fun Int.isNear(color: Color): Boolean {
            val other = color.toArgb()
            return listOf(16, 8, 0).all { shift -> abs((this shr shift and 0xFF) - (other shr shift and 0xFF)) <= 2 }
        }

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

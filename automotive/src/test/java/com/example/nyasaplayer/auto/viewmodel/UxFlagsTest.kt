package com.example.nyasaplayer.auto.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform type CarUxRestrictions is compileOnly and its stubs throw, so the
 * mapping is tested through raw values instead. Flag literals are mirrored in
 * [UxFlags] and verified against the platform at runtime by assertUxFlagsMatchPlatform().
 */
class UxFlagsTest {

    private fun map(
        flags: Int = 0,
        distractionOptimized: Boolean = false,
        depth: Int = Int.MAX_VALUE,
        items: Int = Int.MAX_VALUE,
    ) = toUxState(flags, distractionOptimized, depth, items)

    // --- the regression this whole task exists for ---

    @Test
    fun noTextMessageFlagAlone_doesNotSetNoTextEntry() {
        val state = map(flags = UxFlags.NO_TEXT_MESSAGE)
        assertFalse(
            "NO_TEXT_MESSAGE is the messaging restriction, not the keyboard one",
            state.noTextEntry,
        )
    }

    @Test
    fun noKeyboardFlag_setsNoTextEntry() {
        assertTrue(map(flags = UxFlags.NO_KEYBOARD).noTextEntry)
    }

    // --- individual flags ---

    @Test
    fun noSetupFlag_setsNoSetup() {
        assertTrue(map(flags = UxFlags.NO_SETUP).noSetup)
    }

    @Test
    fun noVideoFlag_setsNoVideo() {
        assertTrue(map(flags = UxFlags.NO_VIDEO).noVideo)
    }

    @Test
    fun noFilteringFlag_setsNoFiltering() {
        assertTrue(map(flags = UxFlags.NO_FILTERING).noFiltering)
    }

    @Test
    fun baselineFlags_setNothing() {
        val state = map(flags = 0)
        assertFalse(state.noTextEntry)
        assertFalse(state.noSetup)
        assertFalse(state.noVideo)
        assertFalse(state.noFiltering)
    }

    @Test
    fun combinedFlags_setAllMatchingFields() {
        val state = map(flags = UxFlags.NO_KEYBOARD or UxFlags.NO_SETUP or UxFlags.NO_VIDEO)
        assertTrue(state.noTextEntry)
        assertTrue(state.noSetup)
        assertTrue(state.noVideo)
        assertFalse(state.noFiltering)
    }

    // --- caps pass through ---

    @Test
    fun contentCaps_passThroughUnchanged() {
        val state = map(depth = 1, items = 21)
        assertEquals(1, state.maxContentDepth)
        assertEquals(21, state.maxCumulativeContentItems)
    }

    // --- distraction optimization comes from the platform, not derived ---

    @Test
    fun isDistractionOptimized_followsPlatformValue_notFlags() {
        assertTrue(map(flags = 0, distractionOptimized = true).isDistractionOptimized)
        assertFalse(
            "must not be re-derived by ORing flags",
            map(flags = UxFlags.NO_KEYBOARD or UxFlags.NO_FILTERING, distractionOptimized = false)
                .isDistractionOptimized,
        )
    }
}

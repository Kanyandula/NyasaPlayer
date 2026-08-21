package com.example.nyasaplayer.auto.ui

import com.example.nyasaplayer.auto.ui.navigation.CarOverlay
import com.example.nyasaplayer.auto.ui.navigation.CarScreen
import com.example.nyasaplayer.auto.ui.navigation.CarSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The search half of the location mapping, plus the overlay stack rules that feed it. The gate's
 * own decisions live in CarRestrictionGateTest; this covers what AutomotiveApp hands it.
 */
class CarUiLocationTest {

    private fun location(
        sheet: CarSheet? = null,
        editing: Boolean = false,
        overlay: CarOverlay? = null,
    ) = carUiLocation(
        tab = CarScreen.Home,
        overlay = overlay,
        drillDown = null,
        sheet = sheet,
        searchTextEntryActive = editing,
    )

    @Test
    fun `no sheet when search is closed`() {
        assertNull(location().sheet)
    }

    @Test
    fun `open search is the Search sheet`() {
        assertEquals(CarSheet.Search, location(sheet = CarSheet.Search).sheet)
    }

    @Test
    fun `open but idle search is not text entry`() {
        assertEquals(false, location(sheet = CarSheet.Search, editing = false).textEntryActive)
    }

    @Test
    fun `open and editing search is text entry`() {
        assertEquals(true, location(sheet = CarSheet.Search, editing = true).textEntryActive)
    }

    @Test
    fun `a stale editing flag cannot report text entry once the sheet is closed`() {
        assertEquals(false, location(sheet = null, editing = true).textEntryActive)
    }

    // --- overlay stack ---

    @Test
    fun `the full player replaces whatever was open`() {
        val stack = overlaysWith(listOf(CarOverlay.Queue), CarOverlay.FullPlayer)

        assertEquals(listOf(CarOverlay.FullPlayer), stack)
    }

    @Test
    fun `the queue opens above the full player rather than replacing it`() {
        val stack = overlaysWith(listOf(CarOverlay.FullPlayer), CarOverlay.Queue)

        assertEquals(listOf(CarOverlay.FullPlayer, CarOverlay.Queue), stack)
    }

    @Test
    fun `closing the queue reveals the full player it covered`() {
        val stack = overlaysWith(listOf(CarOverlay.FullPlayer), CarOverlay.Queue)

        assertEquals(listOf(CarOverlay.FullPlayer), stack.dropLast(1))
    }

    @Test
    fun `the queue opened from the mini player is the whole stack`() {
        assertEquals(listOf(CarOverlay.Queue), overlaysWith(emptyList(), CarOverlay.Queue))
    }

    @Test
    fun `opening the queue twice does not stack two of them`() {
        val once = overlaysWith(listOf(CarOverlay.FullPlayer), CarOverlay.Queue)

        assertEquals(once, overlaysWith(once, CarOverlay.Queue))
    }
}

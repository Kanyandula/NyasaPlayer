package com.example.nyasaplayer.auto.ui

import com.example.nyasaplayer.auto.ui.navigation.CarScreen
import com.example.nyasaplayer.auto.ui.navigation.CarSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The A6 search half of the location mapping. The gate's own decisions live in
 * CarRestrictionGateTest; this covers what AutomotiveApp hands it.
 */
class CarUiLocationTest {

    private fun location(showSearch: Boolean, editing: Boolean = false) = carUiLocation(
        tab = CarScreen.Home,
        showFullPlayer = false,
        showQueue = false,
        drillDown = null,
        showSearch = showSearch,
        searchTextEntryActive = editing,
    )

    @Test
    fun `no sheet when search is closed`() {
        assertNull(location(showSearch = false).sheet)
    }

    @Test
    fun `open search is the Search sheet`() {
        assertEquals(CarSheet.Search, location(showSearch = true).sheet)
    }

    @Test
    fun `open but idle search is not text entry`() {
        assertEquals(false, location(showSearch = true, editing = false).textEntryActive)
    }

    @Test
    fun `open and editing search is text entry`() {
        assertEquals(true, location(showSearch = true, editing = true).textEntryActive)
    }

    @Test
    fun `a stale editing flag cannot report text entry once the sheet is closed`() {
        assertEquals(false, location(showSearch = false, editing = true).textEntryActive)
    }
}

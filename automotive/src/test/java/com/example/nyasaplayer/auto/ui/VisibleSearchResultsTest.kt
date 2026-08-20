package com.example.nyasaplayer.auto.ui

import com.example.nyasaplayer.auto.viewmodel.UxRestrictionState
import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cap that decides both what the driver sees and what a tap plays. A wrong list here means a
 * tap starts a track the driver was never shown.
 */
class VisibleSearchResultsTest {

    private val results = List(10) { Song(mediaId = "$it", title = "Song $it") }

    @Test
    fun `parked search shows everything the repository returned`() {
        val parked = UxRestrictionState(
            requiresDistractionOptimization = false,
            maxCumulativeContentItems = 3,
        )

        assertEquals(10, visibleSearchResults(results, parked).size)
    }

    @Test
    fun `a moving vehicle sees only the platform cap`() {
        val driving = UxRestrictionState(
            requiresDistractionOptimization = true,
            maxCumulativeContentItems = 3,
        )

        assertEquals(listOf("0", "1", "2"), visibleSearchResults(results, driving).map { it.mediaId })
    }

    @Test
    fun `a cap above the result count is not padding`() {
        val driving = UxRestrictionState(
            requiresDistractionOptimization = true,
            maxCumulativeContentItems = 50,
        )

        assertEquals(10, visibleSearchResults(results, driving).size)
    }
}

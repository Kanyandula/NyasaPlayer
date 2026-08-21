package com.example.nyasaplayer.auto.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cap that decides what the driver sees — and, for search, what a tap plays. A wrong list
 * here means a tap starts something the driver was never shown.
 */
class RestrictionCapTest {

    private val items = List(10) { "item-$it" }

    @Test
    fun `parked shows everything, even though the platform still reports a cap`() {
        val parked = UxRestrictionState(
            requiresDistractionOptimization = false,
            maxCumulativeContentItems = 3,
        )

        assertEquals(10, parked.cap(items).size)
    }

    @Test
    fun `a moving vehicle sees only the platform cap`() {
        val driving = UxRestrictionState(
            requiresDistractionOptimization = true,
            maxCumulativeContentItems = 3,
        )

        assertEquals(listOf("item-0", "item-1", "item-2"), driving.cap(items))
    }

    @Test
    fun `a cap above the item count is not padding`() {
        val driving = UxRestrictionState(
            requiresDistractionOptimization = true,
            maxCumulativeContentItems = 50,
        )

        assertEquals(10, driving.cap(items).size)
    }

    @Test
    fun `a negative cap empties the list instead of throwing`() {
        val driving = UxRestrictionState(
            requiresDistractionOptimization = true,
            maxCumulativeContentItems = -1,
        )

        assertEquals(emptyList<String>(), driving.cap(items))
    }
}

package com.example.nyasaplayer.auto.ui.navigation

import com.example.nyasaplayer.auto.viewmodel.UxRestrictionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarRestrictionGateTest {

    private val parked = UxRestrictionState()

    private val driving = UxRestrictionState(
        noTextEntry = true,
        noSetup = true,
        noFiltering = true,
        maxContentDepth = 1,
        maxCumulativeContentItems = 21,
        requiresDistractionOptimization = true,
    )

    private fun at(
        tab: CarScreen = CarScreen.Home,
        overlay: CarOverlay? = null,
        drillDepth: Int = 0,
        sheet: CarSheet? = null,
        textEntryActive: Boolean = false,
    ) = CarUiLocation(tab, overlay, drillDepth, sheet, textEntryActive)

    // --- parked permits everything ---

    @Test
    fun parked_allowsEveryLocation() {
        val locations = listOf(
            at(),
            at(sheet = CarSheet.Settings),
            at(sheet = CarSheet.Profile),
            at(sheet = CarSheet.Search, textEntryActive = true),
            at(drillDepth = 2),
            at(overlay = CarOverlay.FullPlayer),
            at(overlay = CarOverlay.Queue),
        )
        locations.forEach { location ->
            assertEquals("$location must be allowed while parked", GateResult.Allowed, gate(location, parked))
        }
    }

    // --- driving denies setup ---

    @Test
    fun driving_deniesSettings() {
        assertTrue(gate(at(sheet = CarSheet.Settings), driving) is GateResult.Denied)
    }

    @Test
    fun driving_deniesProfile() {
        assertTrue(gate(at(sheet = CarSheet.Profile), driving) is GateResult.Denied)
    }

    // --- driving denies text entry but not browsing search ---

    @Test
    fun driving_deniesSearchWithTextEntry() {
        assertTrue(gate(at(sheet = CarSheet.Search, textEntryActive = true), driving) is GateResult.Denied)
    }

    @Test
    fun driving_allowsSearchWithoutTextEntry() {
        assertEquals(GateResult.Allowed, gate(at(sheet = CarSheet.Search), driving))
    }

    // --- driving denies drilling past the cap ---

    @Test
    fun driving_deniesDrillDepthAboveCap() {
        assertTrue(gate(at(drillDepth = 2), driving) is GateResult.Denied)
    }

    @Test
    fun driving_allowsDrillDepthAtCap() {
        assertEquals(GateResult.Allowed, gate(at(drillDepth = 1), driving))
    }

    // --- playback control stays available while driving ---

    @Test
    fun driving_allowsTabRoots() {
        CarScreen.entries.forEach { tab ->
            assertEquals(GateResult.Allowed, gate(at(tab = tab), driving))
        }
    }

    @Test
    fun driving_allowsFullPlayer() {
        assertEquals(GateResult.Allowed, gate(at(overlay = CarOverlay.FullPlayer), driving))
    }

    @Test
    fun driving_allowsQueueLocation_mutationIsGatedElsewhere() {
        // The queue may be VIEWED while driving, so the location is allowed.
        // Remove/reorder/clear are actions, gated by the screen reading UxRestrictionState —
        // see the spec, "Location gating vs action gating". Not this function's job.
        assertEquals(GateResult.Allowed, gate(at(overlay = CarOverlay.Queue), driving))
    }

    // --- eviction ---

    @Test
    fun denial_evictsToCurrentTabRoot_notHome() {
        val result = gate(at(tab = CarScreen.Library, sheet = CarSheet.Settings), driving)
        val denied = result as GateResult.Denied
        assertEquals(CarUiLocation(CarScreen.Library), denied.evictTo)
    }

    @Test
    fun evictionTarget_isItselfAllowed_soEvictionTerminates() {
        val denied = gate(at(tab = CarScreen.Browse, drillDepth = 3), driving) as GateResult.Denied
        assertEquals(GateResult.Allowed, gate(denied.evictTo, driving))
    }

    @Test
    fun denial_carriesANonEmptyReason() {
        val denied = gate(at(sheet = CarSheet.Settings), driving) as GateResult.Denied
        assertTrue(denied.reason.isNotBlank())
    }
}

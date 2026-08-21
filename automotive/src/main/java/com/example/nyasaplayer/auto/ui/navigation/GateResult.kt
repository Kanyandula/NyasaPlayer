package com.example.nyasaplayer.auto.ui.navigation

import com.example.nyasaplayer.auto.viewmodel.UxRestrictionState

/** Outcome of asking whether a location may be shown under the current restrictions. */
sealed interface GateResult {
    data object Allowed : GateResult

    /**
     * The location is not permitted. [reason] is shown to the driver; [evictTo] is
     * where to send them, and is always itself allowed.
     */
    data class Denied(val reason: String, val evictTo: CarUiLocation) : GateResult
}

private const val ReasonSettings = "Settings can only be changed while the vehicle is parked."
private const val ReasonProfile = "Profiles can only be switched while the vehicle is parked."
private const val ReasonTextEntry =
    "Typing is unavailable while driving. Use voice search instead."
private const val ReasonDepth =
    "Browsing this far into your library is limited while the vehicle is moving."

/**
 * Decides whether [location] may be shown under [state].
 *
 * Entry refusal alone is not sufficient: a vehicle can start moving at any moment, so
 * callers must re-evaluate the current location whenever restrictions change and act on
 * [GateResult.Denied.evictTo]. See AAOS_PRD.md FR-2.5.
 *
 * Playback transport, seeking, queue view/skip-to and tab switching are never denied.
 *
 * The Search branch is live from A6: AutomotiveApp's carUiLocation() sets
 * `sheet = CarSheet.Search` while the sheet is open, and `textEntryActive` from the search
 * ViewModel's editing flag. The Settings and Profile branches stay unreachable until A7 —
 * do not delete them as dead code, they are the contract that slice is written against, and
 * the gate tests already cover them.
 *
 * This function gates LOCATIONS, not ACTIONS. Queue remove/reorder/clear and download
 * deletion are parked-only, but they are actions inside a permitted location, so they are
 * not expressible here. The owning screen reads UxRestrictionState.isDistractionOptimized
 * directly — see CarQueueScreen, which already does exactly this. Do not add action cases
 * to this function.
 */
fun gate(location: CarUiLocation, state: UxRestrictionState): GateResult {
    if (!state.isDistractionOptimized) return GateResult.Allowed

    val reason = when {
        state.noSetup && location.sheet == CarSheet.Settings -> ReasonSettings
        state.noSetup && location.sheet == CarSheet.Profile -> ReasonProfile
        state.noTextEntry && location.sheet == CarSheet.Search && location.textEntryActive ->
            ReasonTextEntry
        location.drillDepth > state.maxContentDepth -> ReasonDepth
        else -> null
    }

    return if (reason == null) GateResult.Allowed else GateResult.Denied(reason, location.tabRoot())
}

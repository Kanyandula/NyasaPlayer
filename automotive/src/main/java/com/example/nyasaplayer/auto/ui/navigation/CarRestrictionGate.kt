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

private const val REASON_SETTINGS = "Settings can only be changed while the vehicle is parked."
private const val REASON_PROFILE = "Profiles can only be switched while the vehicle is parked."
private const val REASON_TEXT_ENTRY =
    "Typing is unavailable while driving. Use voice search instead."
private const val REASON_DEPTH =
    "Browsing this far into your library is limited while the vehicle is moving."

/**
 * Decides whether [location] may be shown under [state].
 *
 * Entry refusal alone is not sufficient: a vehicle can start moving at any moment, so
 * callers must re-evaluate the current location whenever restrictions change and act on
 * [GateResult.Denied.evictTo]. See Task 10 in the implementation plan.
 *
 * Playback transport, seeking, queue view/skip-to and tab switching are never denied.
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
        state.noSetup && location.sheet == CarSheet.Settings -> REASON_SETTINGS
        state.noSetup && location.sheet == CarSheet.Profile -> REASON_PROFILE
        state.noTextEntry && location.sheet == CarSheet.Search && location.textEntryActive ->
            REASON_TEXT_ENTRY
        location.drillDepth > state.maxContentDepth -> REASON_DEPTH
        else -> null
    }

    return if (reason == null) GateResult.Allowed else GateResult.Denied(reason, location.tabRoot())
}

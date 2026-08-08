package com.example.nyasaplayer.auto.ui.navigation

/** Conditional overlays shown above a tab. Not navigation destinations. */
enum class CarOverlay { FullPlayer, Queue }

/** Full-screen sheets reached from the system bar rather than the rail. */
enum class CarSheet { Settings, Profile, Search }

/**
 * Where the user is, as a single value.
 *
 * Derived from state that lives in three different owners: local rememberSaveable
 * values in AutomotiveApp and the search query in AutomotiveContentViewModel.
 * Derived rather than authoritative, so existing screens keep working unchanged.
 */
data class CarUiLocation(
    val tab: CarScreen,
    val overlay: CarOverlay? = null,
    val drillDepth: Int = 0,
    val sheet: CarSheet? = null,
    val textEntryActive: Boolean = false,
) {
    /**
     * The root of the tab the user is already on.
     *
     * This is the eviction target. Chosen over evicting to Home, which moves the
     * user somewhere they did not choose, and over evicting to the previous
     * location, which can land on another restricted location and loop.
     */
    fun tabRoot(): CarUiLocation = CarUiLocation(tab = tab)
}

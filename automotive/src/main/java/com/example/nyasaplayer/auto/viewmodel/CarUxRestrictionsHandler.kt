package com.example.nyasaplayer.auto.viewmodel

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the Car API's distraction rules as a reactive StateFlow.
 *
 * Uses [CarUxRestrictionsManager] to observe driving restriction changes
 * in real time. The [restrictions] flow updates whenever the vehicle
 * transitions between parked and driving states.
 */
@Singleton
class CarUxRestrictionsHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _restrictions = MutableStateFlow(UxRestrictionState())
    val restrictions: StateFlow<UxRestrictionState> = _restrictions.asStateFlow()

    private var car: Car? = null
    private var restrictionsManager: CarUxRestrictionsManager? = null
    private var listener: CarUxRestrictionsManager.OnUxRestrictionsChangedListener? = null

    @Suppress("TooGenericExceptionCaught")
    fun connect() {
        if (restrictionsManager != null) return
        assertUxFlagsMatchPlatform()
        try {
            val carInstance = car ?: Car.createCar(context) ?: return
            car = carInstance
            // Release the half-open connection rather than leaving `car` assigned: the next
            // connect() would otherwise reuse this same instance forever and never recover
            // if the connection itself is what went wrong.
            val manager = carInstance.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                as? CarUxRestrictionsManager
                ?: run {
                    carInstance.disconnect()
                    car = null
                    return
                }
            restrictionsManager = manager
            _restrictions.value = manager.currentCarUxRestrictions.toUxState()
            val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                _restrictions.value = restrictions.toUxState()
            }
            listener = uxListener
            manager.registerListener(uxListener)
        } catch (e: Exception) {
            Log.e("CarUxRestrictions", "Failed to connect to Car service", e)
            car?.disconnect()
            car = null
            restrictionsManager = null
        }
    }

    fun disconnect() {
        restrictionsManager?.unregisterListener()
        listener = null
        restrictionsManager = null
        car?.disconnect()
        car = null
    }
}

/**
 * Thin platform adapter. Holds no logic — see [toUxState] for the tested mapping.
 */
private fun CarUxRestrictions.toUxState(): UxRestrictionState = toUxState(
    activeRestrictions = activeRestrictions,
    requiresDistractionOptimization = isRequiresDistractionOptimization,
    maxContentDepth = maxContentDepth,
    maxCumulativeContentItems = maxCumulativeContentItems,
)

/**
 * Fails loudly on device if the mirrored literals in [UxFlags] ever drift from the
 * platform. They cannot be referenced directly from unit tests, so this is where
 * they are proven correct.
 */
private fun assertUxFlagsMatchPlatform() {
    check(UxFlags.NO_FILTERING == CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING) { "NO_FILTERING drift" }
    check(UxFlags.NO_KEYBOARD == CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD) { "NO_KEYBOARD drift" }
    check(UxFlags.NO_VIDEO == CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) { "NO_VIDEO drift" }
    check(UxFlags.NO_SETUP == CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP) { "NO_SETUP drift" }
    check(UxFlags.NO_TEXT_MESSAGE == CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE) { "NO_TEXT_MESSAGE drift" }
}

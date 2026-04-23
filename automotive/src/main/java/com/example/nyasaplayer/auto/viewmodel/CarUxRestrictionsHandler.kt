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

data class UxRestrictionState(
    val noTextEntry: Boolean = false,
    val limitedContentItems: Int = Int.MAX_VALUE,
    val noVideo: Boolean = false,
    val noFiltering: Boolean = false,
) {
    /**
     * True when the car is in motion and distraction-optimised UX rules apply.
     * Maps to the same flags the AAOS platform uses to gate text entry + filtering.
     */
    val isDistractionOptimized: Boolean get() = noFiltering || noTextEntry
}

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
        if (car != null) return
        try {
            car = Car.createCar(context)?.also { carInstance ->
                val manager = carInstance.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                    as? CarUxRestrictionsManager ?: return
                restrictionsManager = manager
                _restrictions.value = manager.currentCarUxRestrictions.toState()
                val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                    _restrictions.value = restrictions.toState()
                }
                listener = uxListener
                manager.registerListener(uxListener)
            }
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

private fun CarUxRestrictions.toState(): UxRestrictionState {
    val flags = activeRestrictions
    return UxRestrictionState(
        noTextEntry = flags and CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE != 0,
        limitedContentItems = maxCumulativeContentItems,
        noVideo = flags and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO != 0,
        noFiltering = flags and CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING != 0,
    )
}

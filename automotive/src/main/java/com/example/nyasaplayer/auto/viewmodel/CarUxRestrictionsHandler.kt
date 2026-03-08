package com.example.nyasaplayer.auto.viewmodel

import android.content.Context
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
)

/**
 * Wraps the Car API's distraction rules as a reactive StateFlow.
 *
 * The `android.car.*` APIs are only available on AAOS system images. This class
 * uses reflection to connect to the Car service so the module compiles on
 * standard SDK builds. On non-automotive devices, restrictions remain at their
 * permissive defaults.
 */
@Singleton
class CarUxRestrictionsHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _restrictions = MutableStateFlow(UxRestrictionState())
    val restrictions: StateFlow<UxRestrictionState> = _restrictions.asStateFlow()

    @Suppress("TooGenericExceptionCaught")
    fun connect() {
        try {
            val carClass = Class.forName("android.car.Car")
            val createCar = carClass.getMethod("createCar", Context::class.java)
            val car = createCar.invoke(null, context) ?: return

            val managerObj = carClass.getMethod("getCarManager", String::class.java)
                .invoke(car, "car_ux_restriction_service") ?: return

            val restrictionsObj = managerObj.javaClass
                .getMethod("getCurrentCarUxRestrictions")
                .invoke(managerObj) ?: return

            _restrictions.value = parseRestrictions(restrictionsObj)
        } catch (_: Exception) {
            // Not running on automotive — keep permissive defaults
        }
    }

    fun disconnect() {
        // Listener cleanup handled by system when process ends
    }

    @Suppress("TooGenericExceptionCaught", "MagicNumber")
    private fun parseRestrictions(restrictionsObj: Any): UxRestrictionState {
        return try {
            val flags = restrictionsObj.javaClass
                .getMethod("getActiveRestrictions")
                .invoke(restrictionsObj) as Int
            val maxItems = restrictionsObj.javaClass
                .getMethod("getMaxCumulativeContentItems")
                .invoke(restrictionsObj) as Int

            UxRestrictionState(
                noTextEntry = flags and 0x1 != 0,
                limitedContentItems = maxItems,
                noVideo = flags and 0x8 != 0,
                noFiltering = flags and 0x4 != 0,
            )
        } catch (_: Exception) {
            UxRestrictionState()
        }
    }
}

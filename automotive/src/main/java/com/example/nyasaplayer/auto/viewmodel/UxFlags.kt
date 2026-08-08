package com.example.nyasaplayer.auto.viewmodel

/**
 * Distraction rules for the current vehicle state.
 *
 * [isDistractionOptimized] comes from the platform's own answer rather than being
 * re-derived from flags, which is both indirect and wrong when a flag changes meaning.
 */
data class UxRestrictionState(
    val noTextEntry: Boolean = false,
    val noSetup: Boolean = false,
    val noVideo: Boolean = false,
    val noFiltering: Boolean = false,
    val maxContentDepth: Int = Int.MAX_VALUE,
    val maxCumulativeContentItems: Int = Int.MAX_VALUE,
    val requiresDistractionOptimization: Boolean = false,
) {
    val isDistractionOptimized: Boolean get() = requiresDistractionOptimization
}

/**
 * Mirrors android.car.drivingstate.CarUxRestrictions flag values.
 *
 * Mirrored rather than referenced because android.car.jar is compileOnly and absent
 * from the unit-test classpath. Values verified against the SDK stub;
 * assertUxFlagsMatchPlatform() re-checks them on device at connect time.
 */
internal object UxFlags {
    const val NO_FILTERING = 2
    const val NO_KEYBOARD = 8
    const val NO_VIDEO = 16
    const val NO_SETUP = 64
    const val NO_TEXT_MESSAGE = 128
}

/**
 * Pure mapping from raw restriction values to [UxRestrictionState].
 *
 * Takes primitives so it can be unit tested without the platform type.
 *
 * The caps are clamped at zero because they cross a trust boundary: they come from the
 * vehicle's HAL, nothing in the platform contract forbids a negative, and both consumers
 * break badly on one. `List.take()` throws on a negative count, which would crash every
 * browse screen at the moment the vehicle starts moving; and a negative depth cap would
 * make gate() deny its own eviction target, stranding the driver behind a dialog.
 */
internal fun toUxState(
    activeRestrictions: Int,
    requiresDistractionOptimization: Boolean,
    maxContentDepth: Int,
    maxCumulativeContentItems: Int,
): UxRestrictionState = UxRestrictionState(
    noTextEntry = activeRestrictions and UxFlags.NO_KEYBOARD != 0,
    noSetup = activeRestrictions and UxFlags.NO_SETUP != 0,
    noVideo = activeRestrictions and UxFlags.NO_VIDEO != 0,
    noFiltering = activeRestrictions and UxFlags.NO_FILTERING != 0,
    maxContentDepth = maxContentDepth.coerceAtLeast(0),
    maxCumulativeContentItems = maxCumulativeContentItems.coerceAtLeast(0),
    requiresDistractionOptimization = requiresDistractionOptimization,
)

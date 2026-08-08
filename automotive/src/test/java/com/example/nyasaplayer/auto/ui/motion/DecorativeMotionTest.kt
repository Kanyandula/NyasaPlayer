package com.example.nyasaplayer.auto.ui.motion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole of A2's motion rule, as a truth table.
 *
 * Decorative motion is allowed only while parked and only when the platform has animations
 * enabled. Both conditions matter independently, hence four cases rather than two.
 */
class DecorativeMotionTest {

    @Test
    fun parkedWithAnimationsOn_isEnabled() {
        assertTrue(decorativeMotionEnabled(isDistractionOptimized = false, animatorScale = 1f))
    }

    @Test
    fun driving_isDisabled() {
        assertFalse(decorativeMotionEnabled(isDistractionOptimized = true, animatorScale = 1f))
    }

    @Test
    fun animationsOff_isDisabledEvenParked() {
        assertFalse(decorativeMotionEnabled(isDistractionOptimized = false, animatorScale = 0f))
    }

    @Test
    fun drivingAndAnimationsOff_isDisabled() {
        assertFalse(decorativeMotionEnabled(isDistractionOptimized = true, animatorScale = 0f))
    }

    @Test
    fun idlingCountsAsDriving_becauseOverFreezingIsTheSafeDirection() {
        // The platform reports isDistractionOptimized = true while merely idling, with only
        // NO_VIDEO set. Restriction code must not gate on that flag alone, but decoration
        // must: freezing an idling vehicle's background costs nothing.
        assertFalse(decorativeMotionEnabled(isDistractionOptimized = true, animatorScale = 1f))
    }
}

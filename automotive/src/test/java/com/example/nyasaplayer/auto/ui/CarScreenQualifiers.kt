package com.example.nyasaplayer.auto.ui

/**
 * A car-sized canvas for Robolectric. The default device is phone-shaped, which pushes car
 * layouts off screen and makes `assertIsDisplayed()` fail on content that is present and correct.
 */
internal const val CarScreenQualifiers = "w1280dp-h800dp-mdpi"

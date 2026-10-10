package com.example.nyasaplayer.auto.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Both system-bar controls are live from A7.
 *
 * Settings spent A2–A6 rendering disabled, which is the correct way to show a control with nowhere
 * to go (FR-2.6) and exactly the state this locks the bar out of now that it has a sheet: a
 * disabled control swallows its click, so the click would count zero. Profile was the third
 * control until it moved to the rail (D77); `CarNavRailTest` covers it there.
 */
@RunWith(RobolectricTestRunner::class)
class CarSystemBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `search and settings each reach their own callback`() {
        var searches = 0
        var settings = 0

        composeRule.setContent {
            CarSystemBar(
                onSearchClick = { searches++ },
                onSettingsClick = { settings++ },
            )
        }

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        assertEquals(1, searches)
        assertEquals(1, settings)
    }
}

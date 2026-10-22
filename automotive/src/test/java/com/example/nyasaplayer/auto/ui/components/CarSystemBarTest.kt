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
 * Both system-bar controls are live. A disabled control — the right way to show one with nowhere to
 * go (FR-2.6, D3) — swallows its click, so a regression to it would count zero here. The profile
 * entry is in the rail (D82); `CarNavRailTest` covers it there.
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

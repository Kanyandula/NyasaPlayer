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
 * All three system-bar controls are live. A disabled control — the right way to show one with
 * nowhere to go (FR-2.6, D3) — swallows its click, so a regression to it would count zero here.
 */
@RunWith(RobolectricTestRunner::class)
class CarSystemBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `search, settings and profile each reach their own callback`() {
        var searches = 0
        var settings = 0
        var avatars = 0

        composeRule.setContent {
            CarSystemBar(
                onSearchClick = { searches++ },
                onSettingsClick = { settings++ },
                onAvatarClick = { avatars++ },
            )
        }

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithContentDescription("Profile").performClick()

        assertEquals(1, searches)
        assertEquals(1, settings)
        assertEquals(1, avatars)
    }
}

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
 * All three system-bar controls are live from A7.
 *
 * Settings and profile spent A2–A6 rendering disabled, which is the correct way to show a control
 * with nowhere to go (FR-2.6) and exactly the state this locks the bar out of now that they have
 * sheets: a disabled control swallows its click, so these clicks would count zero.
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

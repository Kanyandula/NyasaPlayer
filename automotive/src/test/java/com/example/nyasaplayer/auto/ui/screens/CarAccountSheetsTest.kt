package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Screens 14 and 20, and the one thing that had to leave the Library for them to exist.
 *
 * Both sheets only *request* sign-out — the confirmation lives in the shell (D68), so what
 * these assert is that the request reaches the caller, not that a modal appeared.
 */
@RunWith(RobolectricTestRunner::class)
class CarAccountSheetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var signOuts = 0
    private var closes = 0

    @Test
    fun `settings shows the account and the version, and closes`() {
        composeRule.setContent {
            CarSettingsScreen(
                displayName = "Ada",
                appVersion = "1.4.2",
                onSignOut = { signOuts++ },
                onClose = { closes++ },
            )
        }

        composeRule.onNodeWithText("Ada").assertIsDisplayed()
        composeRule.onNodeWithText("1.4.2").assertIsDisplayed()

        composeRule.onNodeWithText("Sign Out").performClick()
        assertEquals(1, signOuts)

        composeRule.onNodeWithContentDescription("Close Settings").performClick()
        assertEquals(1, closes)
    }

    /**
     * A signed-in driver with no display name still gets a row that reads as an account rather
     * than an empty value — Google accounts without one are not rare.
     */
    @Test
    fun `settings falls back when the account has no display name`() {
        composeRule.setContent {
            CarSettingsScreen(displayName = "", appVersion = "1.0", onSignOut = {}, onClose = {})
        }

        composeRule.onNodeWithText("Your account").assertIsDisplayed()
    }

    /**
     * D66: the app remembers one account, so the screen says so. If this text ever disappears
     * the screen is implying a switcher it does not have.
     */
    @Test
    fun `profile says it holds one account at a time and offers sign out`() {
        composeRule.setContent {
            CarProfileSwitcherScreen(
                displayName = "Ada",
                onSignOut = { signOuts++ },
                onClose = { closes++ },
            )
        }

        composeRule.onNodeWithText("Ada").assertIsDisplayed()
        composeRule.onNodeWithText(
            "one account at a time",
            substring = true,
        ).assertIsDisplayed()

        composeRule.onNodeWithText("Sign Out").performClick()
        assertEquals(1, signOuts)

        composeRule.onNodeWithContentDescription("Close Profile").performClick()
        assertEquals(1, closes)
    }

    /** D14 closed: sign-out has one home now, and it is not the Library. */
    @Test
    fun `library offers no sign out`() {
        composeRule.setContent {
            CarLibraryScreen(
                recentlyPlayed = emptyList(),
                playlists = emptyList(),
                albums = emptyList(),
                favoriteArtists = emptyList(),
                likedSongCount = 0,
                onSongClick = { _, _ -> },
                onPlaylistClick = {},
                onAlbumClick = {},
                onArtistClick = {},
                onFavouritesClick = {},
                onBrowseClick = {},
            )
        }

        composeRule.onNodeWithText("Your Library").assertIsDisplayed()
        composeRule.onNodeWithText("Sign Out").assertDoesNotExist()
        composeRule.onNodeWithText("Signed in as", substring = true).assertDoesNotExist()
    }
}

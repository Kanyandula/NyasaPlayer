package com.example.nyasaplayer.auto.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.nyasaplayer.auto.viewmodel.AutomotiveContentState
import com.example.nyasaplayer.auto.viewmodel.UxRestrictionState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Screen 8 reads the favourites loading/error pair, not the catalogue's.
 *
 * Point [CarFavouritesRoute] at `contentState.errorMessage` and the first test fails; at
 * `contentState.isLoading` and the second does. That is the A4 review row #8 defect, which until
 * T1 nothing executable could catch.
 */
@RunWith(RobolectricTestRunner::class)
class CarFavouritesRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `favourites error is shown and the catalogue error is not`() {
        renderRoute(
            AutomotiveContentState(
                likedSongsLoaded = true,
                errorMessage = "catalogue error",
                favouritesError = "favourites error",
            ),
        )

        composeRule.onNodeWithText("favourites error").assertIsDisplayed()
        composeRule.onNodeWithText("catalogue error").assertDoesNotExist()
    }

    @Test
    fun `loaded favourites show the empty state while the catalogue is still loading`() {
        renderRoute(
            AutomotiveContentState(
                likedSongsLoaded = true,
                isLoading = true,
            ),
        )

        composeRule.onNodeWithText("No favourites yet").assertIsDisplayed()
    }

    private fun renderRoute(contentState: AutomotiveContentState) {
        composeRule.setContent {
            CarFavouritesRoute(
                contentState = contentState,
                restrictions = UxRestrictionState(),
                onSongClick = { _, _ -> },
                onPlayTracks = {},
                onShuffleTracks = {},
                onLikeToggle = { _, _ -> },
                onBrowseClick = {},
                onRetry = {},
            )
        }
    }
}

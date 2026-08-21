package com.example.nyasaplayer.auto.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nyasaplayer.auto.ui.CarScreenQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val VoicePrompt =
    "Typing is off while the vehicle is moving. Ask your assistant to play something instead."

/** Screen 5 — the search sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = CarScreenQualifiers)
class CarSearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var shellClicks = 0
    private var genreBrowses = 0

    /**
     * Under `NO_KEYBOARD` the sheet keeps no editable field, no Search CTA and no Songs chip —
     * each would be a control that cannot do anything (FR-2.6, spec D31). A6 checked this by hand.
     */
    @Test
    fun `no keyboard hides the field and its CTA but keeps browse-by`() {
        // A query typed before the vehicle moved survives in state; nothing may offer to run it
        // once the field showing it is gone.
        renderSheet(query = "worship", canType = false)

        composeRule.onNodeWithText(VoicePrompt).assertIsDisplayed()
        composeRule.onNodeWithText("Search").assertDoesNotExist()
        composeRule.onNodeWithText("Songs").assertDoesNotExist()
        composeRule.onNodeWithText("Search songs, artists, albums").assertDoesNotExist()
        composeRule.onNodeWithText("Browse by").assertIsDisplayed()
        composeRule.onNodeWithText("Genres").assertIsDisplayed()
    }

    /**
     * T6: the sheet is drawn over the shell, and its empty space used to be transparent to
     * touches, so a tap between its rows started playback on the list underneath. Drop
     * `carConsumeTouches()` from the screen and this fails.
     *
     * The helper is exercised through its caller on purpose. A synthetic layout cannot tell
     * consumption from an inert `pointerInput`: either one makes the sheet a hit-test candidate,
     * which is already enough to stop a sibling below it from being hit.
     */
    @Test
    fun `sheet swallows stray taps without disarming its own controls`() {
        renderSheet(query = "", canType = true)

        composeRule.onNodeWithTag("shell").performClick()
        assertEquals(0, shellClicks)

        composeRule.onNodeWithText("Genres").performClick()
        assertEquals(1, genreBrowses)
    }

    private fun renderSheet(query: String, canType: Boolean) {
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                // Stands in for BrowseShell: anything clickable behind the sheet.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { shellClicks++ }
                        .testTag("shell"),
                )
                CarSearchScreen(
                    query = query,
                    recentQueries = emptyList(),
                    canType = canType,
                    onQueryChange = {},
                    onSubmit = {},
                    onClearQuery = {},
                    onEditingChange = {},
                    onRecentClick = {},
                    onBrowseGenres = { genreBrowses++ },
                    onBrowseLibrary = {},
                    onClose = {},
                )
            }
        }
    }
}

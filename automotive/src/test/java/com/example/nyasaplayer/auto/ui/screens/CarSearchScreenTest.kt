package com.example.nyasaplayer.auto.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.nyasaplayer.auto.ui.CarScreenQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val VoicePrompt =
    "Typing is off while the vehicle is moving. Ask your assistant to play something instead."

/**
 * Under `NO_KEYBOARD` screen 5 keeps no editable field, no Search CTA and no Songs chip — each
 * would be a control that cannot do anything (FR-2.6, spec D31). A6 verified this by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = CarScreenQualifiers)
class CarSearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `no keyboard hides the field and its CTA but keeps browse-by`() {
        composeRule.setContent {
            CarSearchScreen(
                // A query typed before the vehicle moved survives in state; nothing may offer to
                // run it once the field showing it is gone.
                query = "worship",
                recentQueries = emptyList(),
                canType = false,
                onQueryChange = {},
                onSubmit = {},
                onClearQuery = {},
                onEditingChange = {},
                onRecentClick = {},
                onBrowseGenres = {},
                onBrowseLibrary = {},
                onClose = {},
            )
        }

        composeRule.onNodeWithText(VoicePrompt).assertIsDisplayed()
        composeRule.onNodeWithText("Search").assertDoesNotExist()
        composeRule.onNodeWithText("Songs").assertDoesNotExist()
        composeRule.onNodeWithText("Search songs, artists, albums").assertDoesNotExist()
        composeRule.onNodeWithText("Browse by").assertIsDisplayed()
        composeRule.onNodeWithText("Genres").assertIsDisplayed()
    }
}

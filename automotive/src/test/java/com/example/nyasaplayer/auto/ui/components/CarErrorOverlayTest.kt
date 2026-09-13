package com.example.nyasaplayer.auto.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nyasaplayer.core.playback.PlayerError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which actions the error overlay offers (A8). Retry and Skip next only for an error about the current
 * item (`isRetryable`); Skip next also only when the caller has somewhere to skip to.
 */
@RunWith(RobolectricTestRunner::class)
class CarErrorOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val retryable = PlayerError(title = "No Connection", message = "m", isRetryable = true)
    private val notRetryable = PlayerError(title = "No Connection", message = "m", isRetryable = false)

    @Test
    fun `skip next is offered only when the caller passes it`() {
        composeRule.setContent {
            CarErrorOverlay(error = retryable, onDismiss = {}, onRetry = {}, onSkipNext = null)
        }

        composeRule.onNodeWithText("Skip next").assertDoesNotExist()
    }

    @Test
    fun `skip next calls back when tapped`() {
        var skips = 0
        composeRule.setContent {
            CarErrorOverlay(error = retryable, onDismiss = {}, onRetry = {}, onSkipNext = { skips++ })
        }

        composeRule.onNodeWithText("Skip next").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(1, skips) }
    }

    @Test
    fun `skip next is not offered for an error about something other than the current item`() {
        // A failed like or an empty genre is not about the playing track; skipping would act on the
        // queue for an error that has nothing to do with it. Same rule as Retry.
        composeRule.setContent {
            CarErrorOverlay(error = notRetryable, onDismiss = {}, onRetry = {}, onSkipNext = {})
        }

        composeRule.onNodeWithText("Skip next").assertDoesNotExist()
    }

    @Test
    fun `retry is offered only for a retryable error`() {
        composeRule.setContent {
            CarErrorOverlay(error = notRetryable, onDismiss = {}, onRetry = {})
        }

        composeRule.onNodeWithText("Retry").assertDoesNotExist()
        composeRule.onNodeWithText("Dismiss").assertIsDisplayed()
    }
}

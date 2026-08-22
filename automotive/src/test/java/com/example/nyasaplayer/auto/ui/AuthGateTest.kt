package com.example.nyasaplayer.auto.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.nyasaplayer.auto.viewmodel.CarAuthUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The user-visible half of T2: a session dying has to take the driver out of the shell.
 *
 * Before T2 the gate read a snapshot taken once at construction, so a revoked session left the
 * signed-in UI up with its user-scoped screens silently frozen.
 */
@RunWith(RobolectricTestRunner::class)
class AuthGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(CarAuthUiState(isAuthenticated = true))

    @Test
    fun `a session that dies replaces the shell with the sign-in screen`() {
        renderGate()
        composeRule.onNodeWithText("shell").assertIsDisplayed()

        state = CarAuthUiState(isAuthenticated = false)

        composeRule.onNodeWithText("shell").assertDoesNotExist()
        composeRule.onNodeWithText("Sign in to access your music library").assertIsDisplayed()
    }

    @Test
    fun `signing back in returns to the shell`() {
        state = CarAuthUiState(isAuthenticated = false)
        renderGate()
        composeRule.onNodeWithText("Sign in to access your music library").assertIsDisplayed()

        state = CarAuthUiState(isAuthenticated = true)

        composeRule.onNodeWithText("shell").assertIsDisplayed()
    }

    private fun renderGate() {
        composeRule.setContent {
            AuthGate(
                authState = state,
                onGoogleToken = {},
                onGoogleError = {},
            ) {
                Text("shell")
            }
        }
    }
}

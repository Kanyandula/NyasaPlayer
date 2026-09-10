package com.example.nyasaplayer.auto.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The scrim dismisses, the card does not.
 *
 * A card with no touch guard leaks its taps to the scrim and dismisses the modal the driver is
 * still reading. Both `carConsumeTouches` and a disabled `clickable` block that; what this pins is
 * that [CarModalCard] keeps *some* guard, which is the thing a future edit is liable to drop.
 */
@RunWith(RobolectricTestRunner::class)
class CarModalTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a tap on the card does not dismiss the modal`() {
        var dismissals = 0
        composeRule.setContent {
            CarModalScrim(onDismiss = { dismissals++ }) {
                CarModalCard { Text(text = "Sign Out?") }
            }
        }

        composeRule.onNodeWithText("Sign Out?").performClick()

        composeRule.runOnIdle { assertEquals(0, dismissals) }
    }

    @Test
    fun `a tap outside the card dismisses the modal`() {
        var dismissals = 0
        composeRule.setContent {
            CarModalScrim(onDismiss = { dismissals++ }) {
                CarModalCard { Text(text = "Sign Out?") }
            }
        }

        composeRule.onRoot().performTouchInput { click(Offset(1f, 1f)) }

        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }
}

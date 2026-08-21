package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A surface that blocks touches from reaching the shell must not block its own content.
 *
 * The first implementation consumed every pointer change on the main pass, which on device
 * stopped the search sheet's result list from scrolling at all — the sections below the fold were
 * unreachable, and the queue's list had the same problem.
 */
@RunWith(RobolectricTestRunner::class)
class CarConsumeTouchesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a list inside a touch-blocking surface still scrolls`() {
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize().carConsumeTouches()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items((1..40).toList()) { index ->
                        Text(text = "Row $index", modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        composeRule.onNodeWithText("Row 1").assertIsDisplayed()
        composeRule.onNodeWithText("Row 1").performTouchInput { swipeUp() }

        composeRule.onNodeWithText("Row 1").assertDoesNotExist()
    }
}

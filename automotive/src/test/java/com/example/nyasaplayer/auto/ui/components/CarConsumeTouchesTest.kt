package com.example.nyasaplayer.auto.ui.components

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.auto.ui.CarScreenQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T6's defect in miniature: a sheet drawn over the shell is not a hit-test candidate unless it
 * takes pointer input, so touches on its empty space reached the rows underneath.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = CarScreenQualifiers)
class CarConsumeTouchesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var shellClicks = 0
    private var sheetClicks = 0

    @Test
    fun `empty sheet space does not reach the shell but the sheet's own controls still work`() {
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { shellClicks++ }
                        .testTag("shell"),
                )
                Box(modifier = Modifier.fillMaxSize().carConsumeTouches()) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clickable { sheetClicks++ }
                            .testTag("sheetButton"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("shell").performClick()
        assertEquals(0, shellClicks)

        composeRule.onNodeWithTag("sheetButton").performClick()
        assertEquals(1, sheetClicks)
    }
}

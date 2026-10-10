package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.auto.ui.navigation.CarScreen
import com.example.nyasaplayer.auto.ui.theme.CarMiniPlayerHeight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The profile row at the bottom of the rail (D77). Measured between node bounds, so it reads
 * layout only and not Robolectric's font metrics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1440dp-h800dp")
class CarNavRailTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var profileClicks = 0

    @Test
    fun `the profile row's centre sits on the mini-player's centre`() {
        setRail(displayName = "Miracle Banda")

        val rail = composeRule.onNodeWithTag(RailTag).getBoundsInRoot()
        val row = composeRule.onNodeWithText("Miracle").getBoundsInRoot()
        // The bar is bottom-anchored beside the rail, so its centre is half its height up.
        val rowCentreAboveBottom = rail.bottom - (row.top + row.bottom) / 2
        assertEquals((CarMiniPlayerHeight / 2).value, rowCentreAboveBottom.value, 0.5f)
    }

    @Test
    fun `the profile row is a button with one text, not a tab`() {
        setRail(displayName = "Miracle Banda")

        val row = composeRule.onNodeWithText("Miracle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        // The avatar's initial is decorative. Merged in, it would make this a two-text control,
        // which the text-size suite stops checking against the 18sp label floor.
        val texts = row.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Miracle"), texts)

        row.performClick()
        assertEquals(1, profileClicks)
    }

    @Test
    fun `without a display name the row says Profile`() {
        setRail(displayName = "")

        composeRule.onNodeWithText("Profile").assertIsDisplayed()
    }

    @Test
    fun `the first name and initial come from the display name`() {
        assertEquals("Miracle", firstName("Miracle Banda"))
        assertEquals("Miracle", firstName("Miracle"))
        assertEquals("Miracle", firstName("  Miracle Banda"))
        assertEquals("", firstName("   "))
        assertEquals("M", initialOf(firstName("miracle banda")))
        assertEquals("", initialOf(firstName("")))
    }

    private fun setRail(displayName: String) {
        composeRule.setContent {
            CarNavRail(
                currentScreen = CarScreen.Home,
                onSelectTab = {},
                displayName = displayName,
                photoUrl = "",
                onProfileClick = { profileClicks++ },
                modifier = Modifier
                    .height(RailHeight)
                    .testTag(RailTag),
            )
        }
    }
}

/** The reference head unit's rail: the 628dp window less the 80dp system bar. */
private val RailHeight = 548.dp
private const val RailTag = "rail"

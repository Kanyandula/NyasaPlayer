package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Screen 15 — Downloads.
 *
 * The behaviour under test is the PRD's "viewable while driving, mutable only while parked": every
 * assertion here is either that the screen still shows the driver their downloads, or that it
 * refuses to change them in motion without silently pretending to.
 */
@RunWith(RobolectricTestRunner::class)
class CarDownloadsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val removed = mutableListOf<String>()
    private val retried = mutableListOf<String>()
    private val played = mutableListOf<String>()
    private var removedAll = 0

    private fun item(
        id: String,
        status: DownloadStatus = DownloadStatus.Completed,
        progress: Int = 0,
        sizeBytes: Long = 5_000_000L,
    ) = CarDownloadItem(
        song = Song(mediaId = id, title = "Title $id", artistName = "Artist $id"),
        status = status,
        progress = progress,
        sizeBytes = sizeBytes,
    )

    private fun render(
        items: List<CarDownloadItem>,
        isDriving: Boolean,
        maxItems: Int = 21,
    ) {
        composeRule.setContent {
            CarDownloadsScreen(
                items = items,
                isDriving = isDriving,
                maxItems = maxItems,
                onBackClick = {},
                onSongClick = { _, song -> played += song.mediaId },
                onRemove = { removed += it },
                onRemoveAll = { removedAll++ },
                onRetry = { retried += it },
                onBrowseClick = {},
            )
        }
    }

    // ── Parked ──

    @Test
    fun `parked, removing one download reaches the caller`() {
        render(listOf(item("s1")), isDriving = false)

        composeRule.onNodeWithContentDescription("Remove download of Title s1").performClick()

        assertEquals(listOf("s1"), removed)
    }

    @Test
    fun `parked, remove all asks before it acts and then acts`() {
        render(listOf(item("s1"), item("s2")), isDriving = false)

        composeRule.onNodeWithText("Remove All").performClick()

        composeRule.onNodeWithText("Remove all downloads?").assertIsDisplayed()
        assertEquals("nothing is deleted until the driver confirms", 0, removedAll)

        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(1, removedAll)
        composeRule.onNodeWithText("Remove all downloads?").assertDoesNotExist()
    }

    @Test
    fun `parked, cancelling the confirmation removes nothing`() {
        render(listOf(item("s1")), isDriving = false)

        composeRule.onNodeWithText("Remove All").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, removedAll)
        composeRule.onNodeWithText("Remove all downloads?").assertDoesNotExist()
    }

    @Test
    fun `parked, a failed download can be retried`() {
        render(listOf(item("s1", DownloadStatus.Failed)), isDriving = false)

        composeRule.onNodeWithText("Couldn't download").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Retry download of Title s1").performClick()

        assertEquals(listOf("s1"), retried)
    }

    @Test
    fun `a finished download plays on tap`() {
        render(listOf(item("s1")), isDriving = false)

        composeRule.onNodeWithText("Title s1").performClick()

        assertEquals(listOf("s1"), played)
    }

    @Test
    fun `parked keeps the banner off the screen and remove all live`() {
        render(listOf(item("s1")), isDriving = false)

        composeRule.onNodeWithText("Park the car", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Remove All").assertIsEnabled()
    }

    // ── Driving ──

    @Test
    fun `driving still shows the downloads and what they weigh`() {
        render(listOf(item("s1"), item("s2")), isDriving = true)

        composeRule.onNodeWithText("Title s1").assertIsDisplayed()
        composeRule.onNodeWithText("Title s2").assertIsDisplayed()
        composeRule.onNodeWithText("2 songs · 10 MB").assertIsDisplayed()
    }

    @Test
    fun `driving disables every mutation and says why`() {
        render(listOf(item("s1"), item("s2", DownloadStatus.Failed)), isDriving = true)

        composeRule.onNodeWithContentDescription("Remove download of Title s1").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Retry download of Title s2").assertIsNotEnabled()
        // Not a silent no-op: the label changes and a banner explains it (FR-2.6).
        composeRule.onNodeWithText("Locked").assertIsDisplayed()
        composeRule.onNodeWithText("Park the car", substring = true).assertIsDisplayed()
    }

    @Test
    fun `driving, the disabled controls do not fire`() {
        render(listOf(item("s1"), item("s2", DownloadStatus.Failed)), isDriving = true)

        composeRule.onNodeWithContentDescription("Remove download of Title s1").performClick()
        composeRule.onNodeWithContentDescription("Retry download of Title s2").performClick()
        composeRule.onNodeWithText("Locked").performClick()

        assertTrue(removed.isEmpty())
        assertTrue(retried.isEmpty())
        assertEquals(0, removedAll)
        // And tapping the locked pill did not open the confirmation either.
        composeRule.onNodeWithText("Remove all downloads?").assertDoesNotExist()
    }

    @Test
    fun `driving truncates the list to the item cap`() {
        render(List(8) { item("s$it") }, isDriving = true, maxItems = 3)

        composeRule.onNodeWithText("Title s0").assertIsDisplayed()
        composeRule.onNodeWithText("Title s2").assertIsDisplayed()
        composeRule.onNodeWithText("Title s3").assertDoesNotExist()
    }

    // ── The transition (FR-2.5) ──

    /**
     * The gap that gating entry alone leaves open: the driver opens the confirmation at the kerb,
     * the car starts moving, and the dialog is still sitting there with a live Remove under the
     * driver's thumb. Drop the `isDriving` key from the screen's `remember` and this fails.
     */
    @Test
    fun `starting to drive closes an open remove-all confirmation`() {
        var driving by mutableStateOf(false)
        composeRule.setContent {
            CarDownloadsScreen(
                items = listOf(item("s1"), item("s2")),
                isDriving = driving,
                maxItems = 21,
                onBackClick = {},
                onSongClick = { _, _ -> },
                onRemove = { removed += it },
                onRemoveAll = { removedAll++ },
                onRetry = {},
                onBrowseClick = {},
            )
        }

        composeRule.onNodeWithText("Remove All").performClick()
        composeRule.onNodeWithText("Remove all downloads?").assertIsDisplayed()

        driving = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Remove all downloads?").assertDoesNotExist()
        assertEquals(0, removedAll)
    }

    // ── Other states ──

    @Test
    fun `no downloads offers a way to find some`() {
        render(emptyList(), isDriving = false)

        composeRule.onNodeWithText("No downloads yet").assertIsDisplayed()
        composeRule.onNodeWithText("Browse Music").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing downloaded yet").assertIsDisplayed()
    }

    @Test
    fun `an in-progress download reports its progress and does not play`() {
        render(listOf(item("s1", DownloadStatus.Downloading, progress = 40, sizeBytes = 0L)), isDriving = false)

        composeRule.onNodeWithText("Downloading · 40%").assertIsDisplayed()
        composeRule.onNodeWithText("Title s1").performClick()

        assertTrue(played.isEmpty())
    }
}

package com.example.nyasaplayer.auto.ui.screens

import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Downloads screen's decisions, as pure functions over primitives (PRD §6.5). */
class CarDownloadItemTest {

    private fun item(
        id: String,
        status: DownloadStatus = DownloadStatus.Completed,
        progress: Int = 0,
        sizeBytes: Long = 0L,
    ) = CarDownloadItem(
        song = Song(mediaId = id, title = id.uppercase()),
        status = status,
        progress = progress,
        sizeBytes = sizeBytes,
    )

    // ── Truncation (FR-2.4) ──

    @Test
    fun `parked shows every row`() {
        val items = List(30) { item("s$it") }

        assertEquals(30, downloadDisplayItems(items, maxItems = 5, isDriving = false).size)
    }

    @Test
    fun `driving truncates to the item cap`() {
        val items = List(30) { item("s$it") }

        val shown = downloadDisplayItems(items, maxItems = 5, isDriving = true)

        assertEquals(5, shown.size)
        assertEquals(listOf("s0", "s1", "s2", "s3", "s4"), shown.map { it.song.mediaId })
    }

    @Test
    fun `driving list shorter than the cap is returned whole`() {
        val items = List(3) { item("s$it") }

        assertEquals(3, downloadDisplayItems(items, maxItems = 21, isDriving = true).size)
    }

    @Test
    fun `a cap of zero or less shows nothing rather than throwing`() {
        val items = List(3) { item("s$it") }

        assertTrue(downloadDisplayItems(items, maxItems = 0, isDriving = true).isEmpty())
        assertTrue(downloadDisplayItems(items, maxItems = -4, isDriving = true).isEmpty())
    }

    // ── Parked-only mutations ──

    @Test
    fun `mutations are refused while driving and allowed while parked`() {
        assertFalse(canMutateDownloads(isDriving = true))
        assertTrue(canMutateDownloads(isDriving = false))
    }

    @Test
    fun `remove all is refused while driving even with downloads present`() {
        val items = listOf(item("s1"), item("s2"))

        assertFalse(canRemoveAllDownloads(items, isDriving = true))
        assertTrue(canRemoveAllDownloads(items, isDriving = false))
    }

    @Test
    fun `remove all is pointless with nothing finished to remove`() {
        assertFalse(canRemoveAllDownloads(emptyList(), isDriving = false))
        assertFalse(
            canRemoveAllDownloads(
                listOf(item("s1", DownloadStatus.Downloading), item("s2", DownloadStatus.Failed)),
                isDriving = false,
            ),
        )
        // One finished row among unfinished ones is still something to remove.
        assertTrue(
            canRemoveAllDownloads(
                listOf(item("s1", DownloadStatus.Downloading), item("s2", DownloadStatus.Completed)),
                isDriving = false,
            ),
        )
    }

    // ── Sizes ──

    @Test
    fun `sizes read in the unit a driver expects`() {
        assertEquals("0 MB", formatDownloadSize(0L))
        assertEquals("0 MB", formatDownloadSize(-1L))
        assertEquals("1 KB", formatDownloadSize(1L))
        assertEquals("512 KB", formatDownloadSize(512_000L))
        assertEquals("4 MB", formatDownloadSize(3_500_000L))
        assertEquals("148 MB", formatDownloadSize(148_000_000L))
        assertEquals("1.4 GB", formatDownloadSize(1_400_000_000L))
    }

    @Test
    fun `the storage line counts finished downloads only`() {
        val items = listOf(
            item("s1", sizeBytes = 100_000_000L),
            item("s2", sizeBytes = 48_000_000L),
            // In flight and failed rows weigh nothing and are not "downloaded".
            item("s3", DownloadStatus.Downloading, progress = 40),
            item("s4", DownloadStatus.Failed),
        )

        assertEquals("2 songs · 148 MB", formatDownloadSummary(items))
    }

    @Test
    fun `the storage line stays singular for one song and says so when empty`() {
        assertEquals("1 song · 5 MB", formatDownloadSummary(listOf(item("s1", sizeBytes = 5_000_000L))))
        assertEquals("Nothing downloaded yet", formatDownloadSummary(emptyList()))
        assertEquals(
            "Nothing downloaded yet",
            formatDownloadSummary(listOf(item("s1", DownloadStatus.Failed))),
        )
    }

    // ── The album Download control (screen 11) ──

    private val tracks = listOf(Song(mediaId = "s1"), Song(mediaId = "s2"))

    @Test
    fun `an undownloaded album offers Download while parked`() {
        val control = albumDownloadControl(tracks, downloads = emptyList(), isDriving = false)

        assertEquals("Download", control.label)
        assertTrue(control.enabled)
    }

    @Test
    fun `driving disables the control and the label says why`() {
        val control = albumDownloadControl(tracks, downloads = emptyList(), isDriving = true)

        assertEquals("Parked only", control.label)
        assertFalse(control.enabled)
    }

    @Test
    fun `a fully downloaded album reads Downloaded whether moving or not`() {
        val downloads = listOf(item("s1"), item("s2"))

        listOf(true, false).forEach { driving ->
            val control = albumDownloadControl(tracks, downloads, isDriving = driving)
            assertEquals("Downloaded", control.label)
            assertFalse(control.enabled)
        }
    }

    @Test
    fun `a partly downloaded album is not Downloaded`() {
        val control = albumDownloadControl(tracks, listOf(item("s1")), isDriving = false)

        assertEquals("Download", control.label)
        assertTrue(control.enabled)
    }

    @Test
    fun `work in flight disables the control without claiming completion`() {
        val downloading = albumDownloadControl(
            tracks,
            listOf(item("s1", DownloadStatus.Downloading, progress = 10)),
            isDriving = false,
        )
        assertEquals("Downloading", downloading.label)
        assertFalse(downloading.enabled)

        val queued = albumDownloadControl(tracks, listOf(item("s1", DownloadStatus.Pending)), isDriving = false)
        assertEquals("Downloading", queued.label)
        assertFalse(queued.enabled)
    }

    @Test
    fun `a failed track leaves the album downloadable again`() {
        val control = albumDownloadControl(tracks, listOf(item("s1", DownloadStatus.Failed)), isDriving = false)

        assertEquals("Download", control.label)
        assertTrue(control.enabled)
    }

    @Test
    fun `an album with no tracks offers nothing to download`() {
        val control = albumDownloadControl(emptyList(), downloads = emptyList(), isDriving = false)

        assertFalse(control.enabled)
    }
}

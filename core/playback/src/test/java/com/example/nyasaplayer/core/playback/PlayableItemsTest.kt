package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.Song
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T31: what an external controller gets when it asks for songs by media id.
 *
 * `onAddMediaItems` serves Assistant, Bluetooth, system media resumption and the car template's
 * `playFromMediaId`. Before T31 it handed back catalogue URLs, so a downloaded song would not play
 * offline with its file sitting on the device.
 *
 * Robolectric because `toMediaItem` builds a `Bundle` and parses a `Uri`.
 */
@RunWith(RobolectricTestRunner::class)
class PlayableItemsTest {

    @get:Rule
    val downloadsDir = TemporaryFolder()

    private lateinit var songRepo: TestSongRepository
    private lateinit var downloadRepo: TestDownloadRepository

    @Before
    fun setUp() {
        songRepo = TestSongRepository()
        downloadRepo = TestDownloadRepository()
        songRepo.songs.value = listOf(song("a"), song("b"))
    }

    @Test
    fun downloadedSong_isRequestedFromTheLocalFile() = runTest {
        val file = downloadsDir.newFile("b.audio")
        downloadRepo.paths["b"] = file.absolutePath

        val items = songRepo.playableItems(listOf("a", "b"), downloadRepo)

        assertEquals(file.toURI().toString(), items.single { it.mediaId == "b" }.uriString())
    }

    @Test
    fun undownloadedSong_keepsItsStreamUrl() = runTest {
        downloadRepo.paths["b"] = downloadsDir.newFile("b.audio").absolutePath

        val items = songRepo.playableItems(listOf("a", "b"), downloadRepo)

        assertEquals("https://cdn.example/a.mp3", items.single { it.mediaId == "a" }.uriString())
    }

    /** The record outlives the file when storage is cleared; the stream is the fallback. */
    @Test
    fun downloadRecordedButFileDeleted_keepsTheStreamUrl() = runTest {
        downloadRepo.paths["b"] = downloadsDir.root.resolve("gone.audio").absolutePath

        val items = songRepo.playableItems(listOf("b"), downloadRepo)

        assertTrue(items.single().uriString().startsWith("https://"))
    }

    @Test
    fun anIdTheCatalogueDoesNotHave_isDropped() = runTest {
        val items = songRepo.playableItems(listOf("a", "nope"), downloadRepo)

        assertEquals(listOf("a"), items.map { it.mediaId })
    }

    private fun song(id: String) =
        Song(mediaId = id, title = id.uppercase(), audioUrl = "https://cdn.example/$id.mp3")

    private fun androidx.media3.common.MediaItem.uriString(): String =
        requireNotNull(localConfiguration?.uri).toString()
}

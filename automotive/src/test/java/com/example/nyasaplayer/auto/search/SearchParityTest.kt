package com.example.nyasaplayer.auto.search

import com.example.nyasaplayer.auto.fake.FakeAlbumRepository
import com.example.nyasaplayer.auto.fake.FakeArtistRepository
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakeGenreRepository
import com.example.nyasaplayer.auto.fake.FakePlaylistRepository
import com.example.nyasaplayer.auto.fake.FakeSongRepository
import com.example.nyasaplayer.auto.fake.FakeUserRepository
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.playback.MediaBrowseTree
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The launcher's song section and `MediaBrowseTree.search()` are one search with two surfaces.
 *
 * Assistant reads the browse tree; the driver reads the launcher. If trimming, the limit or the
 * ordering forks between them, the same spoken and typed query answer differently — which is the
 * parity boundary T4 committed to (spec 3.5). Non-song cards are launcher-only enrichment and
 * are deliberately absent from the media-session results.
 *
 * What this proves precisely: neither *caller* adds divergence on top of one shared repository
 * call — which is exactly the shape of both forks T4 found. It does not exercise the DAO, since
 * both paths run through the same fake; the repository's own normalization is covered by
 * `CatalogSearchDaoTest`, and in production both callers reach the one `OfflineSongRepository`.
 *
 * Robolectric because `MediaItem` metadata carries a `Bundle`.
 */
@RunWith(RobolectricTestRunner::class)
class SearchParityTest {

    private val songs = FakeSongRepository()
    private val artists = FakeArtistRepository()

    private val browseTree = MediaBrowseTree(
        songRepository = songs,
        artistRepository = artists,
        genreRepository = FakeGenreRepository(),
        userRepository = FakeUserRepository(),
        authRepository = FakeAuthRepository(),
    )

    private val launcherSearch = AutomotiveCatalogSearch(
        songRepository = songs,
        albumRepository = FakeAlbumRepository(),
        artistRepository = artists,
        playlistRepository = FakePlaylistRepository(),
        authRepository = FakeAuthRepository(),
    )

    /**
     * The launcher lifts one card into its "Top result" slot, which the media-session path has no
     * concept of. Parity is therefore this: same songs, same limit, and the section under the
     * featured card in the same order Assistant would read them.
     */
    @Test
    fun `both surfaces answer the same query with the same songs in the same order`() = runTest {
        // More matches than either path's limit, so a limit that forks shows up as a size gap.
        songs.songs.value = List(60) { index ->
            Song(mediaId = "s$index", title = "Grace $index", popularity = index)
        }

        val assistant = browseTree.search("grace").map { it.mediaId }
        val results = launcherSearch.search("grace")
        val featured = results.featured?.stableId?.removePrefix("song:")

        assertEquals(50, assistant.size)
        assertEquals(assistant.size, results.songQueue.size)
        assertEquals(assistant.toSet(), results.songQueue.map { it.mediaId }.toSet())
        assertEquals(
            assistant.filterNot { it == featured },
            results.songs.map { it.song.mediaId },
        )
    }

    @Test
    fun `a query with stray whitespace means the same thing on both surfaces`() = runTest {
        songs.songs.value = listOf(
            Song(mediaId = "s1", title = "Grace Abounds"),
            Song(mediaId = "s2", title = "Something Else"),
        )

        val assistant = browseTree.search("  grace  ").map { it.mediaId }
        val launcher = launcherSearch.search("  grace  ").songQueue.map { it.mediaId }

        assertEquals(listOf("s1"), assistant)
        assertEquals(assistant, launcher)
    }
}

package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.LikedSong
import com.example.nyasaplayer.core.common.models.PlaybackState
import com.example.nyasaplayer.core.common.models.RecentlyPlayedEntry
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.models.UserProfile
import com.example.nyasaplayer.core.data.api.ArtistRepository
import com.example.nyasaplayer.core.data.api.AuthRepository
import com.example.nyasaplayer.core.data.api.AuthResult
import com.example.nyasaplayer.core.data.api.GenreRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import com.example.nyasaplayer.core.data.api.UserRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaBrowseTreeTest {

    private lateinit var songRepo: TestSongRepository
    private lateinit var artistRepo: TestArtistRepository
    private lateinit var genreRepo: TestGenreRepository
    private lateinit var userRepo: TestUserRepository
    private lateinit var authRepo: TestAuthRepository
    private lateinit var browseTree: MediaBrowseTree

    @Before
    fun setUp() {
        songRepo = TestSongRepository()
        artistRepo = TestArtistRepository()
        genreRepo = TestGenreRepository()
        userRepo = TestUserRepository()
        authRepo = TestAuthRepository()
        browseTree = MediaBrowseTree(songRepo, artistRepo, genreRepo, userRepo, authRepo)
    }

    // ── Root ──

    @Test
    fun rootItem_hasCorrectMediaId() {
        assertEquals(MediaBrowseTree.ROOT_ID, browseTree.rootItem.mediaId)
    }

    @Test
    fun getRootChildren_returnsFiveCategories() {
        val children = browseTree.getRootChildren()
        assertEquals(5, children.size)
        assertEquals(
            listOf(
                MediaBrowseTree.RECENTLY_PLAYED_ID,
                MediaBrowseTree.LIKED_SONGS_ID,
                MediaBrowseTree.GENRES_ID,
                MediaBrowseTree.ARTISTS_ID,
                MediaBrowseTree.ALL_SONGS_ID,
            ),
            children.map { it.mediaId },
        )
    }

    // Content-style hints are verified on the AAOS emulator — Bundle ops no-op under
    // isReturnDefaultValues = true.

    @Test
    fun getChildren_root_returnsSameAsGetRootChildren() = runTest {
        val children = browseTree.getChildren(MediaBrowseTree.ROOT_ID)
        assertEquals(browseTree.getRootChildren().map { it.mediaId }, children.map { it.mediaId })
    }

    // ── All Songs ──

    @Test
    fun getChildren_allSongs_returnsPlayableItems() = runTest {
        songRepo.songs.value = listOf(
            testSong(mediaId = "1", title = "Song A", popularity = 100),
            testSong(mediaId = "2", title = "Song B", popularity = 50),
        )
        val children = browseTree.getChildren(MediaBrowseTree.ALL_SONGS_ID)
        assertEquals(2, children.size)
        assertEquals("1", children[0].mediaId)
        assertEquals("2", children[1].mediaId)
        assertTrue(children.all { it.mediaMetadata.isPlayable == true })
        assertTrue(children.all { it.mediaMetadata.isBrowsable == false })
    }

    @Test
    fun getChildren_allSongs_emptyRepo_returnsEmpty() = runTest {
        val children = browseTree.getChildren(MediaBrowseTree.ALL_SONGS_ID)
        assertTrue(children.isEmpty())
    }

    // ── Genres ──

    @Test
    fun getChildren_genres_returnsBrowsableItems() = runTest {
        genreRepo.genres.value = listOf(
            testGenre(id = "g1", name = "Rock", popularity = 90),
            testGenre(id = "g2", name = "Jazz", popularity = 70),
        )
        val children = browseTree.getChildren(MediaBrowseTree.GENRES_ID)
        assertEquals(2, children.size)
        assertEquals("${MediaBrowseTree.GENRE_PREFIX}g1", children[0].mediaId)
        assertEquals("${MediaBrowseTree.GENRE_PREFIX}g2", children[1].mediaId)
        assertTrue(children.all { it.mediaMetadata.isBrowsable == true })
        assertTrue(children.all { it.mediaMetadata.isPlayable == false })
    }

    @Test
    fun getChildren_genreSongs_returnsFilteredSongs() = runTest {
        songRepo.songs.value = listOf(
            testSong(mediaId = "1", genreIds = listOf("g1")),
            testSong(mediaId = "2", genreIds = listOf("g2")),
            testSong(mediaId = "3", genreIds = listOf("g1", "g2")),
        )
        val children = browseTree.getChildren("${MediaBrowseTree.GENRE_PREFIX}g1")
        assertEquals(2, children.size)
        assertEquals(listOf("1", "3"), children.map { it.mediaId })
    }

    // ── Artists ──

    @Test
    fun getChildren_artists_returnsBrowsableItems() = runTest {
        artistRepo.artists.value = listOf(
            testArtist(id = "a1", name = "Artist One", popularity = 80),
        )
        val children = browseTree.getChildren(MediaBrowseTree.ARTISTS_ID)
        assertEquals(1, children.size)
        assertEquals("${MediaBrowseTree.ARTIST_PREFIX}a1", children[0].mediaId)
    }

    @Test
    fun getChildren_artistSongs_returnsFilteredSongs() = runTest {
        songRepo.songs.value = listOf(
            testSong(mediaId = "1", artistId = "a1"),
            testSong(mediaId = "2", artistId = "a2"),
        )
        val children = browseTree.getChildren("${MediaBrowseTree.ARTIST_PREFIX}a1")
        assertEquals(1, children.size)
        assertEquals("1", children[0].mediaId)
    }

    // ── Unknown parent ──

    @Test
    fun getChildren_unknownParent_returnsEmpty() = runTest {
        val children = browseTree.getChildren("UNKNOWN")
        assertTrue(children.isEmpty())
    }

    // ── Recently Played ──

    @Test
    fun getChildren_recentlyPlayed_noAuth_returnsEmpty() = runTest {
        val children = browseTree.getChildren(MediaBrowseTree.RECENTLY_PLAYED_ID)
        assertTrue(children.isEmpty())
    }

    // ── Liked Songs ──

    @Test
    fun getChildren_likedSongs_noAuth_returnsEmpty() = runTest {
        val children = browseTree.getChildren(MediaBrowseTree.LIKED_SONGS_ID)
        assertTrue(children.isEmpty())
    }

    @Test
    fun getItem_likedSongsCategoryId_returnsBrowsableItem() = runTest {
        val item = browseTree.getItem(MediaBrowseTree.LIKED_SONGS_ID)
        assertNotNull(item)
        assertEquals(MediaBrowseTree.LIKED_SONGS_ID, item!!.mediaId)
        assertTrue(item.mediaMetadata.isBrowsable == true)
    }

    // ── Song metadata enrichment (for OEM template rendering) ──

    @Test
    fun playableSong_setsAlbumTitleAndDurationOnMetadata() = runTest {
        songRepo.songs.value = listOf(
            Song(
                mediaId = "m1",
                title = "Track",
                artistName = "Artist",
                albumName = "Album X",
                durationMs = 240_000L,
                coverUrl = "https://cover",
                audioUrl = "https://audio",
            ),
        )
        val children = browseTree.getChildren(MediaBrowseTree.ALL_SONGS_ID)
        assertEquals(1, children.size)
        val meta = children[0].mediaMetadata
        assertEquals("Album X", meta.albumTitle?.toString())
        assertEquals(240_000L, meta.durationMs)
    }

    // ── getItem ──

    @Test
    fun getItem_root_returnsRootItem() = runTest {
        val item = browseTree.getItem(MediaBrowseTree.ROOT_ID)
        assertNotNull(item)
        assertEquals(MediaBrowseTree.ROOT_ID, item!!.mediaId)
    }

    @Test
    fun getItem_categoryId_returnsBrowsableItem() = runTest {
        val item = browseTree.getItem(MediaBrowseTree.GENRES_ID)
        assertNotNull(item)
        assertEquals(MediaBrowseTree.GENRES_ID, item!!.mediaId)
    }

    @Test
    fun getItem_genrePrefix_returnsGenreItem() = runTest {
        genreRepo.genres.value = listOf(testGenre(id = "g1", name = "Rock"))
        val item = browseTree.getItem("${MediaBrowseTree.GENRE_PREFIX}g1")
        assertNotNull(item)
        assertEquals("Rock", item!!.mediaMetadata.title.toString())
    }

    @Test
    fun getItem_genrePrefix_notFound_returnsNull() = runTest {
        val item = browseTree.getItem("${MediaBrowseTree.GENRE_PREFIX}missing")
        assertNull(item)
    }

    @Test
    fun getItem_artistPrefix_returnsArtistItem() = runTest {
        artistRepo.artists.value = listOf(testArtist(id = "a1", name = "Hendrix"))
        val item = browseTree.getItem("${MediaBrowseTree.ARTIST_PREFIX}a1")
        assertNotNull(item)
        assertEquals("Hendrix", item!!.mediaMetadata.title.toString())
    }

    @Test
    fun getItem_artistPrefix_notFound_returnsNull() = runTest {
        val item = browseTree.getItem("${MediaBrowseTree.ARTIST_PREFIX}missing")
        assertNull(item)
    }

    @Test
    fun getItem_songMediaId_returnsSongItem() = runTest {
        songRepo.songs.value = listOf(testSong(mediaId = "42", title = "My Song"))
        val item = browseTree.getItem("42")
        assertNotNull(item)
        assertEquals("My Song", item!!.mediaMetadata.title.toString())
    }

    @Test
    fun getItem_songMediaId_notFound_returnsNull() = runTest {
        val item = browseTree.getItem("missing")
        assertNull(item)
    }

    // ── Search ──

    @Test
    fun search_returnsMatchingSongs() = runTest {
        songRepo.songs.value = listOf(
            testSong(mediaId = "1", title = "Purple Haze", popularity = 90),
            testSong(mediaId = "2", title = "All Along", popularity = 80),
        )
        val results = browseTree.search("purple")
        assertEquals(1, results.size)
        assertEquals("1", results[0].mediaId)
    }

    @Test
    fun search_noMatch_returnsEmpty() = runTest {
        songRepo.songs.value = listOf(testSong(mediaId = "1", title = "Song"))
        val results = browseTree.search("nomatch")
        assertTrue(results.isEmpty())
    }
}

// ── Test helpers ──

private fun testSong(
    mediaId: String = "1",
    title: String = "Title",
    artistId: String = "artist1",
    artistName: String = "Artist",
    genreIds: List<String> = emptyList(),
    popularity: Int = 50,
) = Song(
    mediaId = mediaId,
    title = title,
    subtitle = artistName,
    artistId = artistId,
    artistName = artistName,
    genreIds = genreIds,
    popularity = popularity,
    coverUrl = "https://cover/$mediaId",
    audioUrl = "https://audio/$mediaId",
)

private fun testGenre(
    id: String = "genre1",
    name: String = "Genre",
    popularity: Int = 50,
    songIds: List<String> = emptyList(),
) = Genre(id = id, name = name, popularity = popularity, songIds = songIds)

private fun testArtist(
    id: String = "artist1",
    name: String = "Artist",
    popularity: Int = 50,
    imageUrl: String = "",
) = Artist(id = id, name = name, popularity = popularity, imageUrl = imageUrl)

// ── Minimal test fakes ──

private class TestSongRepository : SongRepository {
    val songs = MutableStateFlow<List<Song>>(emptyList())

    override fun getSongs(): Flow<List<Song>> = songs

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        val idSet = ids.toSet()
        return songs.value.filter { it.mediaId in idSet }
    }

    override fun getSongsByArtist(artistId: String): Flow<List<Song>> =
        songs.map { list -> list.filter { it.artistId == artistId } }

    override fun getSongsByGenre(genreId: String): Flow<List<Song>> =
        songs.map { list -> list.filter { genreId in it.genreIds } }

    override suspend fun getSongsByPopularity(limit: Int): List<Song> =
        songs.value.sortedByDescending { it.popularity }.take(limit)

    override suspend fun searchSongs(query: String, limit: Int): List<Song> {
        val lowerQuery = query.lowercase()
        return songs.value.filter {
            it.title.lowercase().contains(lowerQuery) ||
                it.artistName.lowercase().contains(lowerQuery)
        }.sortedByDescending { it.popularity }.take(limit)
    }
}

private class TestArtistRepository : ArtistRepository {
    val artists = MutableStateFlow<List<Artist>>(emptyList())

    override fun getArtists(): Flow<List<Artist>> = artists

    override suspend fun getArtistById(artistId: String): Artist? =
        artists.value.find { it.id == artistId }

    override suspend fun getArtistsByPopularity(limit: Int): List<Artist> =
        artists.value.sortedByDescending { it.popularity }.take(limit)

    // T4 adds artist search for the custom launcher; the browse tree stays song-only (spec 5).
    override suspend fun searchArtists(query: String, limit: Int): List<Artist> =
        error("MediaBrowseTree does not search artists")
}

private class TestGenreRepository : GenreRepository {
    val genres = MutableStateFlow<List<Genre>>(emptyList())

    override fun getGenres(): Flow<List<Genre>> = genres

    override suspend fun getGenreById(genreId: String): Genre? =
        genres.value.find { it.id == genreId }

    override suspend fun getGenresByPopularity(limit: Int): List<Genre> =
        genres.value.sortedByDescending { it.popularity }.take(limit)
}

private class TestUserRepository : UserRepository {
    val recentlyPlayedData = MutableStateFlow<Map<String, List<RecentlyPlayedEntry>>>(emptyMap())

    override fun getUserProfile(userId: String): Flow<UserProfile?> =
        MutableStateFlow(null)

    override suspend fun createOrUpdateProfile(profile: UserProfile) = Unit

    override fun getLikedSongs(userId: String): Flow<List<LikedSong>> =
        MutableStateFlow(emptyList())

    override suspend fun likeSong(userId: String, mediaId: String) = Unit
    override suspend fun unlikeSong(userId: String, mediaId: String) = Unit

    override fun isLiked(userId: String, mediaId: String): Flow<Boolean> =
        MutableStateFlow(false)

    override fun getRecentlyPlayed(userId: String, limit: Int): Flow<List<RecentlyPlayedEntry>> =
        recentlyPlayedData.map { it[userId].orEmpty().take(limit) }

    override suspend fun logRecentlyPlayed(userId: String, mediaId: String) = Unit

    override suspend fun savePlaybackState(userId: String, state: PlaybackState) = Unit

    override suspend fun getPlaybackState(userId: String): PlaybackState? = null
}

private class TestAuthRepository : AuthRepository {
    var user: FirebaseUser? = null

    override val currentUser: FirebaseUser? get() = user
    override val currentUserId: String? get() = user?.uid
    override val isAuthenticated: Boolean get() = user != null

    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        AuthResult.Error("Not configured")

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult =
        AuthResult.Error("Not configured")

    override suspend fun signInWithCredential(credential: AuthCredential): AuthResult =
        AuthResult.Error("Not configured")

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.success(Unit)

    override fun signOut() {
        user = null
    }
}
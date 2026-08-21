package com.example.nyasaplayer.core.data.local.dao

import androidx.room.Room
import com.example.nyasaplayer.core.data.local.NyasaDatabase
import com.example.nyasaplayer.core.data.local.entity.AlbumEntity
import com.example.nyasaplayer.core.data.local.entity.ArtistEntity
import com.example.nyasaplayer.core.data.offline.escapeLikeArgument
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The album and artist search ordering, run against real SQLite.
 *
 * The fakes mirror these queries in Kotlin, so testing through them would only prove the mirror
 * agrees with itself — the drift T1 was raised about. This owns the SQL.
 */
@RunWith(RobolectricTestRunner::class)
class CatalogSearchDaoTest {

    private lateinit var database: NyasaDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            NyasaDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `albums rank exact name over prefix over substring over artist match`() = runTest {
        database.albumDao().upsertAll(
            listOf(
                album(id = "substring", name = "Best of Grace"),
                album(id = "artist", name = "Unrelated", artistName = "Grace Singers"),
                album(id = "exact", name = "Grace"),
                album(id = "prefix", name = "Grace Again"),
            ),
        )

        val results = database.albumDao().search("grace", limit = 10)

        assertEquals(listOf("exact", "prefix", "substring", "artist"), results.map { it.id })
    }

    @Test
    fun `albums in the same tier order by popularity then name then id`() = runTest {
        database.albumDao().upsertAll(
            listOf(
                album(id = "b", name = "Grace Volume", popularity = 10),
                album(id = "a", name = "Grace Volume", popularity = 10),
                album(id = "loud", name = "Grace Anthems", popularity = 99),
            ),
        )

        val results = database.albumDao().search("grace ", limit = 10)

        assertEquals(listOf("loud", "a", "b"), results.map { it.id })
    }

    @Test
    fun `album search is case-insensitive and honours the limit`() = runTest {
        database.albumDao().upsertAll(
            listOf(
                album(id = "1", name = "GRACE ONE", popularity = 3),
                album(id = "2", name = "grace two", popularity = 2),
                album(id = "3", name = "Grace Three", popularity = 1),
            ),
        )

        val results = database.albumDao().search("GrAcE", limit = 2)

        assertEquals(listOf("1", "2"), results.map { it.id })
    }

    @Test
    fun `artists rank exact name over prefix over substring`() = runTest {
        database.artistDao().upsertAll(
            listOf(
                artist(id = "substring", name = "The Grace Band"),
                artist(id = "exact", name = "Grace"),
                artist(id = "prefix", name = "Grace Choir"),
            ),
        )

        val results = database.artistDao().search("grace", limit = 10)

        assertEquals(listOf("exact", "prefix", "substring"), results.map { it.id })
    }

    @Test
    fun `artist search does not match the genre column`() = runTest {
        database.artistDao().upsertAll(
            listOf(artist(id = "gospel-artist", name = "Someone", genres = listOf("gospel"))),
        )

        assertEquals(emptyList<String>(), database.artistDao().search("gospel", 10).map { it.id })
    }

    /**
     * A driver searching for "50%" gets the album with that name, not every album whose name
     * starts "50". Each decoy is a row the unescaped wildcard would drag in.
     */
    @Test
    fun `escaped wildcards match literally`() = runTest {
        database.albumDao().upsertAll(
            listOf(
                album(id = "percent", name = "50% Live"),
                album(id = "percent-decoy", name = "500 Miles"),
                album(id = "underscore", name = "Track_1"),
                album(id = "underscore-decoy", name = "TrackA1"),
            ),
        )

        val percent = database.albumDao().search(escapeLikeArgument("50%"), limit = 10)
        val underscore = database.albumDao().search(escapeLikeArgument("track_1"), limit = 10)

        assertEquals(listOf("percent"), percent.map { it.id })
        assertEquals(listOf("underscore"), underscore.map { it.id })
    }

    private fun album(
        id: String,
        name: String,
        artistName: String = "Someone",
        popularity: Int = 0,
    ) = AlbumEntity(
        id = id,
        name = name,
        artistId = "artist-1",
        artistName = artistName,
        imageUrl = "",
        songIds = emptyList(),
        popularity = popularity,
        releaseDate = "",
    )

    private fun artist(
        id: String,
        name: String,
        genres: List<String> = emptyList(),
        popularity: Int = 0,
    ) = ArtistEntity(
        id = id,
        name = name,
        imageUrl = "",
        genres = genres,
        popularity = popularity,
        songCount = 0,
    )
}

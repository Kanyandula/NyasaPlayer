package com.example.nyasaplayer.data.offline

import com.example.nyasaplayer.core.data.offline.OfflineSongRepository
import com.example.nyasaplayer.data.fake.FakeSongDao
import com.example.nyasaplayer.songEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineSongRepositoryTest {

    private lateinit var dao: FakeSongDao
    private lateinit var repo: OfflineSongRepository

    @Before
    fun setUp() {
        dao = FakeSongDao()
        repo = OfflineSongRepository(dao)
    }

    // --- getSongsByIds ---

    @Test
    fun getSongsByIds_emptyList_returnsEmpty() = runTest {
        dao.setSongs(listOf(songEntity(mediaId = "1")))
        assertTrue(repo.getSongsByIds(emptyList()).isEmpty())
    }

    @Test
    fun getSongsByIds_singleId_returnsSong() = runTest {
        dao.setSongs(listOf(songEntity(mediaId = "1"), songEntity(mediaId = "2")))
        val result = repo.getSongsByIds(listOf("1"))
        assertEquals(1, result.size)
        assertEquals("1", result[0].mediaId)
    }

    @Test
    fun getSongsByIds_preservesRequestOrder() = runTest {
        dao.setSongs(
            listOf(
                songEntity(mediaId = "a"),
                songEntity(mediaId = "b"),
                songEntity(mediaId = "c"),
            ),
        )
        val result = repo.getSongsByIds(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), result.map { it.mediaId })
    }

    @Test
    fun getSongsByIds_missingIds_filteredOut() = runTest {
        dao.setSongs(listOf(songEntity(mediaId = "1")))
        val result = repo.getSongsByIds(listOf("1", "missing"))
        assertEquals(1, result.size)
        assertEquals("1", result[0].mediaId)
    }

    @Test
    fun getSongsByIds_allMissing_returnsEmpty() = runTest {
        dao.setSongs(listOf(songEntity(mediaId = "1")))
        assertTrue(repo.getSongsByIds(listOf("x", "y")).isEmpty())
    }

    @Test
    fun getSongsByIds_duplicateIds_returnsDuplicates() = runTest {
        dao.setSongs(listOf(songEntity(mediaId = "1")))
        val result = repo.getSongsByIds(listOf("1", "1", "1"))
        assertEquals(3, result.size)
        assertTrue(result.all { it.mediaId == "1" })
    }

    @Test
    fun getSongsByIds_moreThan999_chunksCorrectly() = runTest {
        val entities = (1..1500).map { songEntity(mediaId = it.toString()) }
        dao.setSongs(entities)
        val ids = (1..1500).map { it.toString() }
        val result = repo.getSongsByIds(ids)
        assertEquals(1500, result.size)
        assertEquals(ids, result.map { it.mediaId })
    }

    // --- getSongs ---

    @Test
    fun getSongs_emptyDao_returnsEmptyFlow() = runTest {
        val result = repo.getSongs().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun getSongs_withData_returnsMappedSongs() = runTest {
        dao.setSongs(listOf(songEntity(mediaId = "1", title = "Song One")))
        val result = repo.getSongs().first()
        assertEquals(1, result.size)
        assertEquals("Song One", result[0].title)
    }

    // --- getSongsByArtist / getSongsByGenre ---

    @Test
    fun getSongsByArtist_filtersCorrectly() = runTest {
        dao.setSongs(
            listOf(
                songEntity(mediaId = "1", artistId = "a1"),
                songEntity(mediaId = "2", artistId = "a2"),
                songEntity(mediaId = "3", artistId = "a1"),
            ),
        )
        val result = repo.getSongsByArtist("a1").first()
        assertEquals(listOf("1", "3"), result.map { it.mediaId })
    }

    @Test
    fun getSongsByGenre_filtersCorrectly() = runTest {
        dao.setSongs(
            listOf(
                songEntity(mediaId = "1", genreIds = listOf("g1", "g2")),
                songEntity(mediaId = "2", genreIds = listOf("g2")),
                songEntity(mediaId = "3", genreIds = listOf("g1")),
            ),
        )
        val result = repo.getSongsByGenre("g1").first()
        assertEquals(listOf("1", "3"), result.map { it.mediaId })
    }
}

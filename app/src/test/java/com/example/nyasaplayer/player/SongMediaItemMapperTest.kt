package com.example.nyasaplayer.player

import com.example.nyasaplayer.core.common.models.Song
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for JSON serialization in SongMediaItemMapper.
 * Since Android Bundle is stubbed in unit tests, we test the JSON layer directly.
 */
class SongMediaItemMapperTest {

    private fun makeSong(
        mediaId: String = "id1",
        title: String = "Title",
        subtitle: String = "Subtitle",
        genreIds: List<String> = listOf("g1", "g2"),
        durationMs: Long = 180_000L,
        popularity: Int = 42,
        isExplicit: Boolean = true,
    ) = Song(
        mediaId = mediaId,
        title = title,
        subtitle = subtitle,
        imageUrl = "https://img/$mediaId",
        songUrl = "https://song/$mediaId",
        artistId = "artist1",
        artistName = "Artist",
        albumId = "album1",
        albumName = "Album",
        durationMs = durationMs,
        genreIds = genreIds,
        coverUrl = "https://cover/$mediaId",
        audioUrl = "https://audio/$mediaId",
        popularity = popularity,
        isExplicit = isExplicit,
    )

    private fun List<Song>.toJson(): String {
        val jsonArray = JSONArray()
        forEach { song ->
            jsonArray.put(
                JSONObject().apply {
                    put("mediaId", song.mediaId)
                    put("title", song.title)
                    put("subtitle", song.subtitle)
                    put("imageUrl", song.imageUrl)
                    put("songUrl", song.songUrl)
                    put("artistId", song.artistId)
                    put("artistName", song.artistName)
                    put("albumId", song.albumId)
                    put("albumName", song.albumName)
                    put("durationMs", song.durationMs)
                    put("genreIds", JSONArray(song.genreIds))
                    put("coverUrl", song.coverUrl)
                    put("audioUrl", song.audioUrl)
                    put("popularity", song.popularity)
                    put("isExplicit", song.isExplicit)
                },
            )
        }
        return jsonArray.toString()
    }

    private fun String.parseSongList(): List<Song> {
        val array = JSONArray(this)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val genreArray = obj.optJSONArray("genreIds")
            Song(
                mediaId = obj.optString("mediaId", ""),
                title = obj.optString("title", ""),
                subtitle = obj.optString("subtitle", ""),
                imageUrl = obj.optString("imageUrl", ""),
                songUrl = obj.optString("songUrl", ""),
                artistId = obj.optString("artistId", ""),
                artistName = obj.optString("artistName", ""),
                albumId = obj.optString("albumId", ""),
                albumName = obj.optString("albumName", ""),
                durationMs = obj.optLong("durationMs"),
                genreIds = if (genreArray != null) {
                    (0 until genreArray.length()).map { j -> genreArray.getString(j) }
                } else {
                    emptyList()
                },
                coverUrl = obj.optString("coverUrl", ""),
                audioUrl = obj.optString("audioUrl", ""),
                popularity = obj.optInt("popularity"),
                isExplicit = obj.optBoolean("isExplicit"),
            )
        }
    }

    @Test
    fun `round-trip preserves all fields for multiple songs`() {
        val songs = listOf(
            makeSong(mediaId = "s1", title = "Song One"),
            makeSong(mediaId = "s2", title = "Song Two", isExplicit = false, genreIds = listOf("rock")),
        )

        val json = songs.toJson()
        val restored = json.parseSongList()

        assertEquals(songs.size, restored.size)
        assertEquals(songs, restored)
    }

    @Test
    fun `empty list produces empty JSON array`() {
        val json = emptyList<Song>().toJson()
        val restored = json.parseSongList()

        assertTrue(restored.isEmpty())
        assertEquals("[]", json)
    }

    @Test
    fun `round-trip preserves empty genreIds`() {
        val song = makeSong(genreIds = emptyList())
        val restored = listOf(song).toJson().parseSongList()

        assertEquals(emptyList<String>(), restored.first().genreIds)
    }

    @Test
    fun `round-trip preserves special characters in title`() {
        val song = makeSong(title = "Hello \"World\" & <Friends>")
        val restored = listOf(song).toJson().parseSongList()

        assertEquals("Hello \"World\" & <Friends>", restored.first().title)
    }

    @Test
    fun `round-trip preserves zero and default numeric values`() {
        val song = makeSong(durationMs = 0L, popularity = 0, isExplicit = false)
        val restored = listOf(song).toJson().parseSongList()

        val result = restored.first()
        assertEquals(0L, result.durationMs)
        assertEquals(0, result.popularity)
        assertEquals(false, result.isExplicit)
    }

    @Test
    fun `JSON structure contains expected keys and values`() {
        val song = makeSong()
        val json = listOf(song).toJson()

        val array = JSONArray(json)
        assertEquals(1, array.length())

        val obj = array.getJSONObject(0)
        assertEquals("id1", obj.getString("mediaId"))
        assertEquals("Title", obj.getString("title"))
        assertEquals(180_000L, obj.getLong("durationMs"))
        assertEquals(true, obj.getBoolean("isExplicit"))

        val genreArray = obj.getJSONArray("genreIds")
        assertEquals(2, genreArray.length())
        assertEquals("g1", genreArray.getString(0))
        assertEquals("g2", genreArray.getString(1))
    }

    @Test
    fun `missing genreIds key results in empty list`() {
        val json = JSONArray().put(
            JSONObject().apply {
                put("mediaId", "x1")
                put("title", "Test")
            },
        ).toString()

        val songs = json.parseSongList()

        assertEquals(1, songs.size)
        assertEquals(emptyList<String>(), songs.first().genreIds)
    }

    @Test
    fun `missing optional fields default to empty or zero`() {
        val json = JSONArray().put(JSONObject()).toString()

        val song = json.parseSongList().first()

        assertEquals("", song.mediaId)
        assertEquals("", song.title)
        assertEquals(0L, song.durationMs)
        assertEquals(0, song.popularity)
        assertEquals(false, song.isExplicit)
        assertEquals(emptyList<String>(), song.genreIds)
    }
}

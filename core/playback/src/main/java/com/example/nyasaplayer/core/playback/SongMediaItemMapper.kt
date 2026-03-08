package com.example.nyasaplayer.core.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.nyasaplayer.core.common.models.Song
import org.json.JSONArray
import org.json.JSONObject

private const val KeyMediaId = "mediaId"
private const val KeyTitle = "title"
private const val KeySubtitle = "subtitle"
private const val KeyImageUrl = "imageUrl"
private const val KeySongUrl = "songUrl"
private const val KeyArtistId = "artistId"
private const val KeyArtistName = "artistName"
private const val KeyAlbumId = "albumId"
private const val KeyAlbumName = "albumName"
private const val KeyDurationMs = "durationMs"
private const val KeyGenreIds = "genreIds"
private const val KeyCoverUrl = "coverUrl"
private const val KeyAudioUrl = "audioUrl"
private const val KeyPopularity = "popularity"
private const val KeyIsExplicit = "isExplicit"
private const val KeySongsJson = "songsJson"

fun Song.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(KeyMediaId, mediaId)
        putString(KeySubtitle, subtitle)
        putString(KeyImageUrl, imageUrl)
        putString(KeySongUrl, songUrl)
        putString(KeyArtistId, artistId)
        putString(KeyArtistName, artistName)
        putString(KeyAlbumId, albumId)
        putString(KeyAlbumName, albumName)
        putLong(KeyDurationMs, durationMs)
        putString(KeyGenreIds, genreIds.joinToString(","))
        putString(KeyCoverUrl, coverUrl)
        putString(KeyAudioUrl, audioUrl)
        putInt(KeyPopularity, popularity)
        putBoolean(KeyIsExplicit, isExplicit)
    }
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(Uri.parse(resolvedAudioUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtist(resolvedArtistName)
                .setArtworkUri(Uri.parse(resolvedCoverUrl))
                .setExtras(extras)
                .build(),
        )
        .build()
}

fun MediaItem.toSong(): Song {
    val extras = mediaMetadata.extras ?: return Song(mediaId = mediaId)
    return Song(
        mediaId = extras.getString(KeyMediaId, ""),
        title = mediaMetadata.title?.toString().orEmpty(),
        subtitle = extras.getString(KeySubtitle, ""),
        imageUrl = extras.getString(KeyImageUrl, ""),
        songUrl = extras.getString(KeySongUrl, ""),
        artistId = extras.getString(KeyArtistId, ""),
        artistName = extras.getString(KeyArtistName, ""),
        albumId = extras.getString(KeyAlbumId, ""),
        albumName = extras.getString(KeyAlbumName, ""),
        durationMs = extras.getLong(KeyDurationMs),
        genreIds = extras.getString(KeyGenreIds, "")
            .split(",").filter { it.isNotBlank() },
        coverUrl = extras.getString(KeyCoverUrl, ""),
        audioUrl = extras.getString(KeyAudioUrl, ""),
        popularity = extras.getInt(KeyPopularity),
        isExplicit = extras.getBoolean(KeyIsExplicit),
    )
}

// Note: Queue is serialized as JSON inside a Bundle. For very large queues (1000+ songs),
// this could approach IPC transaction limits. Current practical limit is ~100-200 songs.
fun List<Song>.toBundle(): Bundle {
    val jsonArray = JSONArray()
    forEach { song ->
        jsonArray.put(
            JSONObject().apply {
                put(KeyMediaId, song.mediaId)
                put(KeyTitle, song.title)
                put(KeySubtitle, song.subtitle)
                put(KeyImageUrl, song.imageUrl)
                put(KeySongUrl, song.songUrl)
                put(KeyArtistId, song.artistId)
                put(KeyArtistName, song.artistName)
                put(KeyAlbumId, song.albumId)
                put(KeyAlbumName, song.albumName)
                put(KeyDurationMs, song.durationMs)
                put(KeyGenreIds, JSONArray(song.genreIds))
                put(KeyCoverUrl, song.coverUrl)
                put(KeyAudioUrl, song.audioUrl)
                put(KeyPopularity, song.popularity)
                put(KeyIsExplicit, song.isExplicit)
            },
        )
    }
    return Bundle().apply { putString(KeySongsJson, jsonArray.toString()) }
}

@Suppress("CyclomaticComplexity")
fun Bundle.toSongList(): List<Song> {
    val json = getString(KeySongsJson) ?: return emptyList()
    val array = JSONArray(json)
    return (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        val genreArray = obj.optJSONArray(KeyGenreIds)
        Song(
            mediaId = obj.optString(KeyMediaId, ""),
            title = obj.optString(KeyTitle, ""),
            subtitle = obj.optString(KeySubtitle, ""),
            imageUrl = obj.optString(KeyImageUrl, ""),
            songUrl = obj.optString(KeySongUrl, ""),
            artistId = obj.optString(KeyArtistId, ""),
            artistName = obj.optString(KeyArtistName, ""),
            albumId = obj.optString(KeyAlbumId, ""),
            albumName = obj.optString(KeyAlbumName, ""),
            durationMs = obj.optLong(KeyDurationMs),
            genreIds = if (genreArray != null) {
                (0 until genreArray.length()).map { j -> genreArray.getString(j) }
            } else {
                emptyList()
            },
            coverUrl = obj.optString(KeyCoverUrl, ""),
            audioUrl = obj.optString(KeyAudioUrl, ""),
            popularity = obj.optInt(KeyPopularity),
            isExplicit = obj.optBoolean(KeyIsExplicit),
        )
    }
}

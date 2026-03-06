package com.example.nyasaplayer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nyasaplayer.core.common.models.Song

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_id")
    val mediaId: String,
    val title: String,
    val subtitle: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String,
    @ColumnInfo(name = "song_url")
    val songUrl: String,
    @ColumnInfo(name = "artist_id")
    val artistId: String,
    @ColumnInfo(name = "artist_name")
    val artistName: String,
    @ColumnInfo(name = "album_id")
    val albumId: String,
    @ColumnInfo(name = "album_name")
    val albumName: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "genre_ids")
    val genreIds: List<String>,
    @ColumnInfo(name = "cover_url")
    val coverUrl: String,
    @ColumnInfo(name = "audio_url")
    val audioUrl: String,
    val popularity: Int,
    @ColumnInfo(name = "is_explicit")
    val isExplicit: Boolean,
) {
    fun toDomain(): Song = Song(
        mediaId = mediaId,
        title = title,
        subtitle = subtitle,
        imageUrl = imageUrl,
        songUrl = songUrl,
        artistId = artistId,
        artistName = artistName,
        albumId = albumId,
        albumName = albumName,
        durationMs = durationMs,
        genreIds = genreIds,
        coverUrl = coverUrl,
        audioUrl = audioUrl,
        popularity = popularity,
        isExplicit = isExplicit,
    )

    companion object {
        fun fromDomain(song: Song): SongEntity = SongEntity(
            mediaId = song.mediaId,
            title = song.title,
            subtitle = song.subtitle,
            imageUrl = song.imageUrl,
            songUrl = song.songUrl,
            artistId = song.artistId,
            artistName = song.artistName,
            albumId = song.albumId,
            albumName = song.albumName,
            durationMs = song.durationMs,
            genreIds = song.genreIds,
            coverUrl = song.coverUrl,
            audioUrl = song.audioUrl,
            popularity = song.popularity,
            isExplicit = song.isExplicit,
        )
    }
}

package com.example.nyasaplayer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nyasaplayer.core.common.models.Album

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "artist_id")
    val artistId: String,
    @ColumnInfo(name = "artist_name")
    val artistName: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String,
    @ColumnInfo(name = "song_ids")
    val songIds: List<String>,
    val popularity: Int,
    @ColumnInfo(name = "release_date")
    val releaseDate: String,
) {
    fun toDomain(): Album = Album(
        id = id,
        name = name,
        artistId = artistId,
        artistName = artistName,
        imageUrl = imageUrl,
        songIds = songIds,
        popularity = popularity,
        releaseDate = releaseDate,
    )

    companion object {
        fun fromDomain(album: Album): AlbumEntity = AlbumEntity(
            id = album.id,
            name = album.name,
            artistId = album.artistId,
            artistName = album.artistName,
            imageUrl = album.imageUrl,
            songIds = album.songIds,
            popularity = album.popularity,
            releaseDate = album.releaseDate,
        )
    }
}

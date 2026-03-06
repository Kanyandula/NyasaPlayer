package com.example.nyasaplayer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nyasaplayer.core.common.models.Artist

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String,
    val genres: List<String>,
    val popularity: Int,
    @ColumnInfo(name = "song_count")
    val songCount: Int,
) {
    fun toDomain(): Artist = Artist(
        id = id,
        name = name,
        imageUrl = imageUrl,
        genres = genres,
        popularity = popularity,
        songCount = songCount,
    )

    companion object {
        fun fromDomain(artist: Artist): ArtistEntity = ArtistEntity(
            id = artist.id,
            name = artist.name,
            imageUrl = artist.imageUrl,
            genres = artist.genres,
            popularity = artist.popularity,
            songCount = artist.songCount,
        )
    }
}

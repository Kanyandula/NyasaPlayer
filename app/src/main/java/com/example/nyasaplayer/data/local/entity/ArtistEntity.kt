package com.example.nyasaplayer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nyasaplayer.data.local.fromPipeDelimited
import com.example.nyasaplayer.data.local.toPipeDelimited
import com.example.nyasaplayer.models.Artist

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String,
    val genres: String,
    val popularity: Int,
    @ColumnInfo(name = "song_count")
    val songCount: Int,
) {
    fun toDomain(): Artist = Artist(
        id = id,
        name = name,
        imageUrl = imageUrl,
        genres = fromPipeDelimited(genres),
        popularity = popularity,
        songCount = songCount,
    )

    companion object {
        fun fromDomain(artist: Artist): ArtistEntity = ArtistEntity(
            id = artist.id,
            name = artist.name,
            imageUrl = artist.imageUrl,
            genres = toPipeDelimited(artist.genres),
            popularity = artist.popularity,
            songCount = artist.songCount,
        )
    }
}

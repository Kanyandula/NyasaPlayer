package com.example.nyasaplayer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nyasaplayer.models.Genre

@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val color: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String,
    val popularity: Int,
    @ColumnInfo(name = "song_ids")
    val songIds: List<String>,
) {
    fun toDomain(): Genre = Genre(
        id = id,
        name = name,
        color = color,
        imageUrl = imageUrl,
        popularity = popularity,
        songIds = songIds,
    )

    companion object {
        fun fromDomain(genre: Genre): GenreEntity = GenreEntity(
            id = genre.id,
            name = genre.name,
            color = genre.color,
            imageUrl = genre.imageUrl,
            popularity = genre.popularity,
            songIds = genre.songIds,
        )
    }
}

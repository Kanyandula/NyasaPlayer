package com.example.nyasaplayer

import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.data.local.entity.ArtistEntity
import com.example.nyasaplayer.core.data.local.entity.GenreEntity
import com.example.nyasaplayer.core.data.local.entity.SongEntity

fun song(
    mediaId: String = "1",
    title: String = "Title",
    subtitle: String = "Subtitle",
    imageUrl: String = "https://img/$mediaId",
    songUrl: String = "https://song/$mediaId",
    artistId: String = "artist1",
    artistName: String = "Artist Name",
    albumId: String = "album1",
    albumName: String = "Album Name",
    durationMs: Long = 210_000L,
    genreIds: List<String> = emptyList(),
    coverUrl: String = "https://cover/$mediaId",
    audioUrl: String = "https://audio/$mediaId",
    popularity: Int = 50,
    isExplicit: Boolean = false,
): Song = Song(
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

fun songEntity(
    mediaId: String = "1",
    title: String = "Title",
    subtitle: String = "Subtitle",
    imageUrl: String = "https://img/$mediaId",
    songUrl: String = "https://song/$mediaId",
    artistId: String = "artist1",
    artistName: String = "Artist Name",
    albumId: String = "album1",
    albumName: String = "Album Name",
    durationMs: Long = 210_000L,
    genreIds: List<String> = emptyList(),
    coverUrl: String = "https://cover/$mediaId",
    audioUrl: String = "https://audio/$mediaId",
    popularity: Int = 50,
    isExplicit: Boolean = false,
): SongEntity = SongEntity.fromDomain(
    song(
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
    ),
)

fun artist(
    id: String = "artist1",
    name: String = "Artist Name",
    imageUrl: String = "https://img/$id",
    genres: List<String> = emptyList(),
    popularity: Int = 80,
    songCount: Int = 10,
): Artist = Artist(
    id = id,
    name = name,
    imageUrl = imageUrl,
    genres = genres,
    popularity = popularity,
    songCount = songCount,
)

fun artistEntity(
    id: String = "artist1",
    name: String = "Artist Name",
    imageUrl: String = "https://img/$id",
    genres: List<String> = emptyList(),
    popularity: Int = 80,
    songCount: Int = 10,
): ArtistEntity = ArtistEntity.fromDomain(
    artist(
        id = id,
        name = name,
        imageUrl = imageUrl,
        genres = genres,
        popularity = popularity,
        songCount = songCount,
    ),
)

fun genre(
    id: String = "genre1",
    name: String = "Genre Name",
    color: String = "#FF0000",
    imageUrl: String = "https://img/$id",
    popularity: Int = 70,
    songIds: List<String> = emptyList(),
): Genre = Genre(
    id = id,
    name = name,
    color = color,
    imageUrl = imageUrl,
    popularity = popularity,
    songIds = songIds,
)

fun genreEntity(
    id: String = "genre1",
    name: String = "Genre Name",
    color: String = "#FF0000",
    imageUrl: String = "https://img/$id",
    popularity: Int = 70,
    songIds: List<String> = emptyList(),
): GenreEntity = GenreEntity.fromDomain(
    genre(
        id = id,
        name = name,
        color = color,
        imageUrl = imageUrl,
        popularity = popularity,
        songIds = songIds,
    ),
)

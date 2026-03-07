package com.example.nyasaplayer.core.common.models

data class Song(
    val mediaId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val imageUrl: String = "",
    val songUrl: String = "",
    val artistId: String = "",
    val artistName: String = "",
    val albumId: String = "",
    val albumName: String = "",
    val durationMs: Long = 0L,
    val genreIds: List<String> = emptyList(),
    val coverUrl: String = "",
    val audioUrl: String = "",
    val popularity: Int = 0,
    val isExplicit: Boolean = false,
) {
    val resolvedCoverUrl: String get() = coverUrl.ifBlank { imageUrl }
    val resolvedAudioUrl: String get() = audioUrl.ifBlank { songUrl }
    val resolvedArtistName: String get() = artistName.ifBlank { subtitle }
}

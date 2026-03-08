package com.example.nyasaplayer.core.common.models

data class Album(
    val id: String = "",
    val name: String = "",
    val artistId: String = "",
    val artistName: String = "",
    val imageUrl: String = "",
    val songIds: List<String> = emptyList(),
    val popularity: Int = 0,
    val releaseDate: String = "",
)

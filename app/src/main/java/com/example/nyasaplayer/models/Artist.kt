package com.example.nyasaplayer.models

data class Artist(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val genres: List<String> = emptyList(),
    val popularity: Int = 0,
    val songCount: Int = 0,
)

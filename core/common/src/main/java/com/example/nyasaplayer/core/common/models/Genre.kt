package com.example.nyasaplayer.core.common.models

data class Genre(
    val id: String = "",
    val name: String = "",
    val color: String = "",
    val imageUrl: String = "",
    val popularity: Int = 0,
    val songIds: List<String> = emptyList(),
)

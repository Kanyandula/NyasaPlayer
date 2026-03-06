package com.example.nyasaplayer.core.common.models

data class HomeFeed(
    val sections: List<FeedSection> = emptyList(),
)

data class FeedSection(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val songIds: List<String> = emptyList(),
    val maxItems: Int = 0,
)

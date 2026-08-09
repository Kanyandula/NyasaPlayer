package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.components.CarEmptyState
import com.example.nyasaplayer.auto.ui.components.CarTrackRow
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.formatDuration

private val SectionSpacing = 16.dp
private val ListPadding = 24.dp
private val TitleSize = 30.sp

/**
 * Liked songs, and nothing else.
 *
 * Deliberately minimal. A4 builds the designed screen — hero, Play all, Shuffle, unlike. This
 * exists so the navigation rail can carry its fourth destination from the moment the chrome
 * contract ships, without two rail items rendering identical content. Artists, albums and
 * sign-out belong to Library and are not repeated here.
 */
@Composable
fun CarFavouriteMusicScreen(
    likedSongs: List<Song>,
    onSongClick: (Song) -> Unit,
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
) {
    if (likedSongs.isEmpty()) {
        CarEmptyState(
            title = "No favourites yet",
            body = "Songs you like will appear here.",
            modifier = modifier,
            actionLabel = "Browse Music",
            onAction = onBrowseClick,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        item {
            Text(
                text = "Favourites",
                color = Color.White,
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
            )
        }
        items(likedSongs, key = { it.mediaId }) { song ->
            CarTrackRow(
                title = song.title,
                artist = song.resolvedArtistName,
                duration = formatDuration(song.durationMs),
                isPlaying = isPlaying && song.mediaId == currentlyPlayingMediaId,
                onClick = { onSongClick(song) },
                coverUrl = song.resolvedCoverUrl,
            )
        }
    }
}

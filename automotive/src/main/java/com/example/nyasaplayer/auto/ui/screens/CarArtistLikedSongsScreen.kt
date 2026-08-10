package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.components.CarPillButton
import com.example.nyasaplayer.auto.ui.components.CarTrackRow
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.formatDuration

private val ButtonRowSpacing = 12.dp

/**
 * Screen 9 — an artist's liked songs, reached by drilling down from Library.
 *
 * Unlike screen 8, this list is a live filter over the caller's liked songs, not a freeze:
 * unliking a row here removes it immediately. `isLiked` is hardcoded true on every row for the
 * same reason — this list *is* that artist's liked songs, so there is no hollow-heart state to
 * render (spec D25).
 */
@Suppress("LongParameterList")
@Composable
fun CarArtistLikedSongsScreen(
    artistName: String,
    likedSongs: List<Song>,
    onBackClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onLikeToggle: (Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            ArtistDetailHeader(
                artistName = artistName,
                songCount = likedSongs.size,
                onBackClick = onBackClick,
            )
        }
        if (likedSongs.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonRowSpacing)) {
                    CarPillButton(label = "Play all", onClick = onPlayAll)
                    CarPillButton(label = "Shuffle", onClick = onShufflePlay, filled = false)
                }
            }
            items(likedSongs, key = { it.mediaId }) { song ->
                CarTrackRow(
                    title = song.title,
                    artist = song.resolvedArtistName,
                    duration = formatDuration(song.durationMs),
                    isPlaying = isPlaying && song.mediaId == currentlyPlayingMediaId,
                    onClick = { onSongClick(song) },
                    coverUrl = song.resolvedCoverUrl,
                    onLikeToggle = { onLikeToggle(song) },
                    isLiked = true,
                )
            }
        }
    }
}

@Composable
private fun ArtistDetailHeader(
    artistName: String,
    songCount: Int,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(CarTouchTargetSize),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
        Text(
            text = artistName,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "$songCount liked songs",
            color = CarTextSecondary,
            fontSize = 18.sp,
        )
    }
}

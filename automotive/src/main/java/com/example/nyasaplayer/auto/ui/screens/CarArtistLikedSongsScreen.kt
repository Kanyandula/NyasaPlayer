package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.ShufflePlayButton
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary

@Suppress("LongParameterList")
@Composable
fun CarArtistLikedSongsScreen(
    artistName: String,
    likedSongs: List<Song>,
    onBackClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onShufflePlay: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground)
            .padding(horizontal = 24.dp),
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
                ShufflePlayButton(
                    onClick = onShufflePlay,
                    height = CarTouchTargetSize,
                )
            }
            items(likedSongs, key = { it.mediaId }) { song ->
                LikedSongItem(
                    song = song,
                    isCurrentTrack = song.mediaId == currentlyPlayingMediaId,
                    isPlaying = isPlaying,
                    onClick = { onSongClick(song) },
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
            color = NyasaTextSecondary,
            fontSize = 18.sp,
        )
    }
}

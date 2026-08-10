package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.auto.ui.components.CarPillButton
import com.example.nyasaplayer.auto.ui.components.CarTrackRow
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.formatDuration

private val ListPadding = 24.dp
private val HeroSpacing = 24.dp
private val HeroArtSize = 200.dp
private val TitleSize = 34.sp
private val SubtitleSize = 20.sp

/**
 * Screen 9 — an artist's liked songs, reached by drilling down from Library.
 *
 * Unlike screen 8, this list is a live filter over the caller's liked songs, not a freeze:
 * unliking a row here removes it on the next emission (spec D25). [pendingUnlikes] still drives
 * the heart, so the row the driver actually hit hollows immediately — otherwise a mis-tap on the
 * heart at the row's edge unlikes a song with no feedback beyond the row disappearing.
 */
@Suppress("LongParameterList")
@Composable
fun CarArtistLikedSongsScreen(
    artistName: String,
    artistCoverUrl: String,
    likedSongs: List<Song>,
    pendingUnlikes: Set<String>,
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
        contentPadding = PaddingValues(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(HeroSpacing),
    ) {
        item {
            ArtistHero(
                artistName = artistName,
                artistCoverUrl = artistCoverUrl,
                likedSongs = likedSongs,
                onBackClick = onBackClick,
                onPlayAll = onPlayAll,
                onShufflePlay = onShufflePlay,
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
                onLikeToggle = { onLikeToggle(song) },
                isLiked = song.mediaId !in pendingUnlikes,
            )
        }
    }
}

/** Same shape as the Favourites hero, with the circular avatar artists get everywhere else. */
@Suppress("LongParameterList")
@Composable
private fun ArtistHero(
    artistName: String,
    artistCoverUrl: String,
    likedSongs: List<Song>,
    onBackClick: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HeroSpacing),
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
        AsyncImage(
            model = artistCoverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(HeroArtSize)
                .clip(CircleShape)
                .background(CarRaised),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HeroSpacing / 2),
        ) {
            Text(
                text = artistName,
                color = Color.White,
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${likedSongs.size} liked songs",
                color = CarTextSecondary,
                fontSize = SubtitleSize,
            )
            if (likedSongs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(HeroSpacing / 2)) {
                    CarPillButton(label = "Play all", onClick = onPlayAll)
                    CarPillButton(label = "Shuffle", onClick = onShufflePlay, filled = false)
                }
            }
        }
    }
}

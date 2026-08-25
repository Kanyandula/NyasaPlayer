package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.auto.ui.components.CarEmptyState
import com.example.nyasaplayer.auto.ui.components.CarPillButton
import com.example.nyasaplayer.auto.ui.components.CarRowSkeleton
import com.example.nyasaplayer.auto.ui.components.CarTrackRow
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.formatDuration

private val SectionSpacing = 16.dp
private val ListPadding = 24.dp
private val HeroSpacing = 24.dp
private val HeroArtSize = 200.dp
private val TitleSize = 34.sp
private val SubtitleSize = 20.sp

/**
 * Screen 8 — liked songs.
 *
 * Rows render from [songs], which the caller supplies as the frozen list when one is in effect and
 * the live list otherwise. A row whose id is in [pendingUnlikes] shows a hollow heart but keeps its
 * place: the contract defers row removal until refresh, because a list that reflows under the
 * driver's finger turns a mis-tap into a wrong action. See spec D19.
 */
@Suppress("LongParameterList")
@Composable
fun CarFavouriteMusicScreen(
    songs: List<Song>,
    pendingUnlikes: Set<String>,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onLikeToggle: (Song) -> Unit,
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    when {
        errorMessage != null && songs.isEmpty() -> CarEmptyState(
            title = "Something went wrong",
            body = errorMessage,
            modifier = modifier,
            actionLabel = "Try again",
            onAction = onRetry,
        )

        isLoading && songs.isEmpty() -> CarRowSkeleton(
            spacing = SectionSpacing,
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = ListPadding),
        )

        songs.isEmpty() -> CarEmptyFavouritesScreen(
            onBrowseClick = onBrowseClick,
            modifier = modifier,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = ListPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            item {
                FavouritesHero(
                    songs = songs,
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                )
            }
            items(songs, key = { it.mediaId }) { song ->
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
}

@Composable
private fun FavouritesHero(
    songs: List<Song>,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HeroSpacing),
    ) {
        AsyncImage(
            model = songs.firstOrNull()?.resolvedCoverUrl.orEmpty(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(HeroArtSize)
                .clip(RoundedCornerShape(CarCardCornerRadius))
                .background(CarRaised),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HeroSpacing / 2),
        ) {
            Text(
                text = "Liked Songs",
                color = Color.White,
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${songs.size} songs",
                color = CarTextSecondary,
                fontSize = SubtitleSize,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HeroSpacing / 2)) {
                CarPillButton(label = "Play all", onClick = onPlayAll)
                CarPillButton(label = "Shuffle", onClick = onShuffle, filled = false)
            }
        }
    }
}

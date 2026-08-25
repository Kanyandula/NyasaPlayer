package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.nyasaplayer.auto.viewmodel.CarDetailState
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.formatDuration

private val HeroArtSize = 200.dp
private val HeroSpacing = 24.dp
private val ListPadding = 24.dp
private val TitleSize = 34.sp
private val SubtitleSize = 20.sp

/**
 * Album detail — screen 11.
 *
 * No Download button: the code that performs a download is `SongDownloadManager`, `@Singleton`
 * in `:app`, which `:automotive` does not and should not depend on. `DownloadRepository` is
 * reachable but is Room bookkeeping only, so wiring it alone would ship a button that
 * permanently claims a download is in progress (D12).
 */
@Composable
fun CarAlbumScreen(
    detail: CarDetailState,
    onBackClick: () -> Unit,
    onPlay: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    onRetry: () -> Unit = {},
) {
    CarDetailBody(
        detail = detail,
        emptyBody = "This album has no playable tracks.",
        onBackClick = onBackClick,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onSongClick = onSongClick,
        modifier = modifier,
        currentlyPlayingMediaId = currentlyPlayingMediaId,
        isPlaying = isPlaying,
        onRetry = onRetry,
    )
}

/**
 * Playlist detail — screen 10.
 *
 * The contract's "save/offline if supported" resolves to *not supported*: `PlaylistRepository`
 * has no offline or save concept, so neither control ships. Artwork is the first resolved
 * track's cover, since `Playlist` has no cover field.
 */
@Composable
fun CarPlaylistScreen(
    detail: CarDetailState,
    onBackClick: () -> Unit,
    onPlay: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    onRetry: () -> Unit = {},
) {
    CarDetailBody(
        detail = detail,
        emptyBody = "This playlist is empty.",
        onBackClick = onBackClick,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onSongClick = onSongClick,
        modifier = modifier,
        currentlyPlayingMediaId = currentlyPlayingMediaId,
        isPlaying = isPlaying,
        onRetry = onRetry,
    )
}

/**
 * Catalogue artist detail, reached from a search result.
 *
 * Not `CarArtistLikedSongsScreen`: that one is scoped to the driver's likes and renders hearts
 * that remove rows on tap. This is every song the catalogue has by the artist, so it reuses the
 * album/playlist body, which has no like affordance (spec 3.4).
 */
@Composable
fun CarArtistScreen(
    detail: CarDetailState,
    onBackClick: () -> Unit,
    onPlay: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    onRetry: () -> Unit = {},
) {
    CarDetailBody(
        detail = detail,
        emptyBody = "This artist has no playable tracks.",
        onBackClick = onBackClick,
        onPlay = onPlay,
        onShuffle = onShuffle,
        onSongClick = onSongClick,
        modifier = modifier,
        currentlyPlayingMediaId = currentlyPlayingMediaId,
        isPlaying = isPlaying,
        onRetry = onRetry,
    )
}

@Suppress("LongParameterList")
@Composable
private fun CarDetailBody(
    detail: CarDetailState,
    emptyBody: String,
    onBackClick: () -> Unit,
    onPlay: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val error = detail.errorMessage
    when {
        error != null -> CarEmptyState(
            title = "Something went wrong",
            body = error,
            modifier = modifier,
            actionLabel = "Try again",
            onAction = onRetry,
        )

        detail.isLoading -> DetailSkeleton(modifier = modifier)

        detail.tracks.isEmpty() -> CarEmptyState(
            title = detail.title,
            body = emptyBody,
            modifier = modifier,
            actionLabel = "Back",
            onAction = onBackClick,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = ListPadding),
            verticalArrangement = Arrangement.spacedBy(HeroSpacing),
        ) {
            item {
                DetailHero(
                    detail = detail,
                    onPlay = { onPlay(detail.tracks) },
                    onShuffle = { onShuffle(detail.tracks) },
                    onBackClick = onBackClick,
                )
            }
            items(detail.tracks, key = { it.mediaId }) { song ->
                CarTrackRow(
                    title = song.title,
                    artist = song.resolvedArtistName,
                    duration = formatDuration(song.durationMs),
                    isPlaying = isPlaying && song.mediaId == currentlyPlayingMediaId,
                    onClick = { onSongClick(detail.tracks, song) },
                    coverUrl = song.resolvedCoverUrl,
                )
            }
        }
    }
}

@Composable
private fun DetailHero(
    detail: CarDetailState,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HeroSpacing),
    ) {
        AsyncImage(
            model = detail.artworkUrl,
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
                text = detail.title,
                color = Color.White,
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detailSubtitle(detail),
                color = CarTextSecondary,
                fontSize = SubtitleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HeroSpacing / 2)) {
                CarPillButton(label = "Play", onClick = onPlay)
                CarPillButton(label = "Shuffle", onClick = onShuffle, filled = false)
                CarPillButton(label = "Back", onClick = onBackClick, filled = false)
            }
        }
    }
}

private fun detailSubtitle(detail: CarDetailState): String {
    val count = "${detail.tracks.size} tracks"
    return if (detail.subtitle.isBlank()) count else "${detail.subtitle} · $count"
}

@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(HeroSpacing),
    ) {
        Box(
            modifier = Modifier
                .size(HeroArtSize)
                .clip(RoundedCornerShape(CarCardCornerRadius))
                .background(CarRaised),
        )
        CarRowSkeleton(spacing = HeroSpacing)
    }
}

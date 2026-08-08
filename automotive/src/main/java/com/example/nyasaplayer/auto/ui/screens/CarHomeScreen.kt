package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.example.nyasaplayer.auto.ui.components.CarSectionHeader
import com.example.nyasaplayer.auto.ui.components.CarTrackRow
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarGlass
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.formatDuration
import com.example.nyasaplayer.core.common.util.greetingResource

private val SectionSpacing = 24.dp
private val ListPadding = 24.dp
private val HeroArtSize = 120.dp
private val HeroPadding = 20.dp
private val SkeletonRowHeight = 80.dp
private val SkeletonSpacing = 12.dp
private val GreetingSize = 30.sp
private val SubtitleSize = 18.sp
private val HeroTitleSize = 24.sp
private val HeroLabelSize = 14.sp
private const val SkeletonRowCount = 4

/**
 * Home.
 *
 * Three sections: a resume hero, Continue Listening, and Popular Now. The design's
 * "Your Mixes" and "Recommended" are deliberately absent rather than renamed — the data layer
 * has neither a mixes nor a recommendation concept, and a section title promising
 * personalisation the backend does not do is worse than one fewer section (A2 spec, D1). The
 * slots remain, so adding a real recommender later is a data-source change.
 *
 * The quick-access grid went with the arrival of the navigation rail (D5): three of its four
 * actions were rail destinations, and two navigation systems on one screen is the
 * muscle-memory problem the chrome contract exists to prevent.
 *
 * [onSongClick] takes the section's own list. `playSong` resolves its start index with
 * `indexOfFirst { … }.coerceAtLeast(0)`, so handing it a list the song is absent from would
 * silently play that list's first track instead.
 */
@Composable
fun CarHomeScreen(
    recentlyPlayed: List<Song>,
    popularSongs: List<Song>,
    isLoading: Boolean,
    errorMessage: String?,
    onSongClick: (List<Song>, Song) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
) {
    when {
        errorMessage != null -> CarEmptyState(
            title = "Something went wrong",
            body = errorMessage,
            modifier = modifier,
            actionLabel = "Try again",
            onAction = onRetry,
        )

        isLoading -> HomeSkeleton(modifier = modifier)

        recentlyPlayed.isEmpty() && popularSongs.isEmpty() -> CarEmptyState(
            title = "Nothing to play yet",
            body = "Your music will appear here once it has synced.",
            modifier = modifier,
            actionLabel = "Retry",
            onAction = onRetry,
        )

        else -> HomeContent(
            recentlyPlayed = recentlyPlayed,
            popularSongs = popularSongs,
            onSongClick = onSongClick,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isPlaying = isPlaying,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeContent(
    recentlyPlayed: List<Song>,
    popularSongs: List<Song>,
    onSongClick: (List<Song>, Song) -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        item { HomeHeader() }

        recentlyPlayed.firstOrNull()?.let { resume ->
            item {
                ResumeHero(song = resume, onClick = { onSongClick(recentlyPlayed, resume) })
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item { CarSectionHeader(title = "Continue Listening") }
            items(recentlyPlayed, key = { "recent_${it.mediaId}" }) { song ->
                HomeTrack(
                    song = song,
                    section = recentlyPlayed,
                    onSongClick = onSongClick,
                    currentlyPlayingMediaId = currentlyPlayingMediaId,
                    isPlaying = isPlaying,
                )
            }
        }

        if (popularSongs.isNotEmpty()) {
            item { CarSectionHeader(title = "Popular Now") }
            items(popularSongs, key = { "popular_${it.mediaId}" }) { song ->
                HomeTrack(
                    song = song,
                    section = popularSongs,
                    onSongClick = onSongClick,
                    currentlyPlayingMediaId = currentlyPlayingMediaId,
                    isPlaying = isPlaying,
                )
            }
        }
    }
}

@Composable
private fun HomeTrack(
    song: Song,
    section: List<Song>,
    onSongClick: (List<Song>, Song) -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    CarTrackRow(
        title = song.title,
        artist = song.resolvedArtistName,
        duration = formatDuration(song.durationMs),
        isPlaying = isPlaying && song.mediaId == currentlyPlayingMediaId,
        // The row passes its own section, never a shared list — see CarHomeScreen's KDoc.
        onClick = { onSongClick(section, song) },
        modifier = modifier,
        coverUrl = song.resolvedCoverUrl,
    )
}

@Composable
private fun HomeHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = greetingResource(),
            color = Color.White,
            fontSize = GreetingSize,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Ready for your drive?",
            color = CarTextSecondary,
            fontSize = SubtitleSize,
        )
    }
}

/** Resume the most recently played track. One large target rather than a list row. */
@Composable
private fun ResumeHero(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CarCardCornerRadius))
            .background(CarGlass)
            .clickable(onClick = onClick)
            .padding(HeroPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HeroPadding),
    ) {
        AsyncImage(
            model = song.resolvedCoverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(HeroArtSize)
                .clip(RoundedCornerShape(CarCardCornerRadius)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PICK UP WHERE YOU LEFT OFF",
                color = CarTextSecondary,
                fontSize = HeroLabelSize,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = song.title,
                color = Color.White,
                fontSize = HeroTitleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = song.resolvedArtistName,
                color = CarTextSecondary,
                fontSize = SubtitleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CarPillButton(label = "Play", onClick = onClick)
    }
}

/**
 * Static placeholders, no shimmer.
 *
 * The ambient layer is the app's only decorative motion, and it is gated on vehicle state; a
 * shimmer that ignored that gating would reintroduce exactly the motion A2 removed.
 */
@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(SkeletonSpacing),
    ) {
        repeat(SkeletonRowCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SkeletonRowHeight)
                    .clip(RoundedCornerShape(CarCardCornerRadius))
                    .background(CarRaised),
            )
        }
    }
}

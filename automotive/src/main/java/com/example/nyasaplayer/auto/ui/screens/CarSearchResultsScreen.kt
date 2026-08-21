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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.nyasaplayer.auto.ui.components.CarTrackRow
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarListRowHeight
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarScreenMargin
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.common.util.formatDuration

private val SectionSpacing = 24.dp
private val RowSpacing = 12.dp
private val TopResultArtSize = 120.dp
private val TopResultSpacing = 20.dp
private val IconSize = 28.dp
private val QuerySize = 26.sp
private val TopResultTitleSize = 26.sp
private val LabelSize = 18.sp
private val BodySize = 20.sp
private const val SkeletonRowCount = 4

/**
 * Screen 6 — results for a submitted query.
 *
 * [results] is the list the driver can actually see, already capped for the current restrictions
 * by the caller. Every tap hands that same list back through [onSongClick], so the queue the
 * driver gets is the list they were looking at — playing a song that is off the bottom of a cap
 * is how a "play this" turns into a different track.
 *
 * Album and artist result cards are deferred to T4 (spec D33); results are songs only.
 */
@Suppress("LongParameterList")
@Composable
fun CarSearchResultsScreen(
    query: String,
    results: List<Song>,
    isLoading: Boolean,
    errorMessage: String?,
    onBackToSearch: () -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onSongClick: (List<Song>, Song) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
) {
    Column(
        // Opaque for the same reason as CarQueueScreen: a sheet occludes the shell behind it.
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground)
            .padding(CarScreenMargin),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        ResultsHeader(query = query, onBackToSearch = onBackToSearch, onClear = onClear)

        when {
            isLoading -> ResultsSkeleton()

            errorMessage != null -> CarEmptyState(
                title = "Search didn't work",
                body = errorMessage,
                actionLabel = "Try again",
                onAction = onRetry,
            )

            results.isEmpty() -> CarEmptyState(
                title = "No results",
                body = "Nothing in your library matches \"$query\".",
                actionLabel = "Edit search",
                onAction = onBackToSearch,
            )

            else -> ResultsList(
                results = results,
                onSongClick = onSongClick,
                currentlyPlayingMediaId = currentlyPlayingMediaId,
                isPlaying = isPlaying,
            )
        }
    }
}

@Composable
private fun ResultsHeader(
    query: String,
    onBackToSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        IconButton(onClick = onBackToSearch, modifier = Modifier.size(CarTouchTargetSize)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to search",
                tint = Color.White,
                modifier = Modifier.size(IconSize),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Results for", color = CarTextSecondary, fontSize = LabelSize)
            Text(
                text = query,
                color = Color.White,
                fontSize = QuerySize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CarPillButton(label = "Clear", onClick = onClear, filled = false)
    }
}

@Composable
private fun ResultsList(
    results: List<Song>,
    onSongClick: (List<Song>, Song) -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = SectionSpacing),
        verticalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        val top = results.first()
        item(key = top.mediaId) {
            TopResult(
                song = top,
                isPlaying = isPlaying && top.mediaId == currentlyPlayingMediaId,
                onClick = { onSongClick(results, top) },
            )
        }
        // Indexed rather than results.drop(1): the drop copies the whole list every time the
        // lazy content runs, and this list is up to the repository's search limit.
        items(count = results.size - 1, key = { results[it + 1].mediaId }) { index ->
            val song = results[index + 1]
            CarTrackRow(
                title = song.title,
                artist = song.resolvedArtistName,
                duration = formatDuration(song.durationMs),
                isPlaying = isPlaying && song.mediaId == currentlyPlayingMediaId,
                // The whole visible list, not just this row — the driver is choosing a
                // starting point in what they can see.
                onClick = { onSongClick(results, song) },
                coverUrl = song.resolvedCoverUrl,
            )
        }
    }
}

/** The first visible result, given more weight because it is the answer most taps want. */
@Composable
private fun TopResult(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CarCardCornerRadius))
            .background(CarRaised)
            .clickable(onClick = onClick)
            .padding(TopResultSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TopResultSpacing),
    ) {
        AsyncImage(
            model = song.resolvedCoverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(TopResultArtSize)
                .clip(RoundedCornerShape(CarCardCornerRadius)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Top result", color = NyasaGold, fontSize = LabelSize)
            Text(
                text = song.title,
                color = if (isPlaying) NyasaGold else Color.White,
                fontSize = TopResultTitleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.resolvedArtistName,
                color = CarTextSecondary,
                fontSize = BodySize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CarPillButton(label = if (isPlaying) "Playing" else "Play", onClick = onClick)
    }
}

/** Static placeholders, no shimmer — same reason as HomeSkeleton. */
@Composable
private fun ResultsSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        repeat(SkeletonRowCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarListRowHeight)
                    .clip(RoundedCornerShape(CarCardCornerRadius))
                    .background(CarRaised),
            )
        }
    }
}

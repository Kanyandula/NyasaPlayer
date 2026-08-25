package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.auto.search.AutomotiveSearchResult
import com.example.nyasaplayer.auto.search.AutomotiveSearchResults
import com.example.nyasaplayer.auto.ui.components.CarCardShape
import com.example.nyasaplayer.auto.ui.components.CarContentCard
import com.example.nyasaplayer.auto.ui.components.CarEmptyState
import com.example.nyasaplayer.auto.ui.components.CarPillButton
import com.example.nyasaplayer.auto.ui.components.CarRowSkeleton
import com.example.nyasaplayer.auto.ui.components.CarSectionHeader
import com.example.nyasaplayer.auto.ui.components.CarTrackRow
import com.example.nyasaplayer.auto.ui.components.carConsumeTouches
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarScreenMargin
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
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

/** Test seam: the vertical result list, so a test can scroll to a section below the fold. */
internal const val SearchResultsListTag = "searchResults"

/**
 * Screen 6 — results for a submitted query.
 *
 * [results] is what the driver can actually see: the caller has already applied the cumulative
 * driving cap, so nothing here can render a card the platform disallowed. Taps carry the typed
 * result back out, because each type goes somewhere different (spec 3.6).
 *
 * Sections keep a fixed order — songs, albums, artists, playlists — so the screen does not
 * reorder itself as different types come back for different queries. Empty ones are omitted.
 */
@Suppress("LongParameterList")
@Composable
fun CarSearchResultsScreen(
    query: String,
    results: AutomotiveSearchResults,
    isLoading: Boolean,
    errorMessage: String?,
    onBackToSearch: () -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onResultClick: (AutomotiveSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
) {
    Column(
        // Opaque for the same reason as CarQueueScreen: a sheet occludes the shell behind it.
        modifier = modifier
            .fillMaxSize()
            .carConsumeTouches()
            .background(NyasaBackground)
            .padding(CarScreenMargin),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        ResultsHeader(query = query, onBackToSearch = onBackToSearch, onClear = onClear)

        when {
            isLoading -> CarRowSkeleton(spacing = RowSpacing, modifier = Modifier.fillMaxSize())

            errorMessage != null -> CarEmptyState(
                title = "Search didn't work",
                body = errorMessage,
                actionLabel = "Try again",
                onAction = onRetry,
            )

            results.isEmpty -> CarEmptyState(
                title = "No results",
                body = "Nothing in your library matches \"$query\".",
                actionLabel = "Edit search",
                onAction = onBackToSearch,
            )

            else -> ResultsList(
                results = results,
                onResultClick = onResultClick,
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
    results: AutomotiveSearchResults,
    onResultClick: (AutomotiveSearchResult) -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(SearchResultsListTag),
        contentPadding = PaddingValues(bottom = SectionSpacing),
        verticalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        val featured = results.featured
        if (featured != null) {
            item(key = featured.stableId) {
                TopResult(
                    result = featured,
                    isPlaying = isPlaying && featured.isCurrent(currentlyPlayingMediaId),
                    onClick = { onResultClick(featured) },
                )
            }
        }

        if (results.songs.isNotEmpty()) {
            item(key = "songs-header") { CarSectionHeader(title = "Songs") }
            items(results.songs, key = { it.stableId }) { result ->
                val song = result.song
                CarTrackRow(
                    title = song.title,
                    artist = song.resolvedArtistName,
                    duration = formatDuration(song.durationMs),
                    isPlaying = isPlaying && song.mediaId == currentlyPlayingMediaId,
                    onClick = { onResultClick(result) },
                    coverUrl = song.resolvedCoverUrl,
                )
            }
        }

        cardSection(
            title = "Albums",
            results = results.albums,
            shape = CarCardShape.Square,
            onResultClick = onResultClick,
        )
        cardSection(
            title = "Artists",
            results = results.artists,
            shape = CarCardShape.Circle,
            onResultClick = onResultClick,
        )
        cardSection(
            title = "Playlists",
            results = results.playlists,
            shape = CarCardShape.Square,
            onResultClick = onResultClick,
        )
    }
}

/**
 * One carousel of cards, the same shape Library and Home already use for these three types.
 *
 * A row rather than a vertical list because the cards are the app's existing artwork tiles; the
 * count is already bounded by the caller's cap, so the row is short by construction.
 */
private fun LazyListScope.cardSection(
    title: String,
    results: List<AutomotiveSearchResult>,
    shape: CarCardShape,
    onResultClick: (AutomotiveSearchResult) -> Unit,
) {
    if (results.isEmpty()) return
    item(key = "$title-header") { CarSectionHeader(title = title) }
    item(key = "$title-row") {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(RowSpacing),
        ) {
            results.forEach { result ->
                CarContentCard(
                    title = result.title,
                    onClick = { onResultClick(result) },
                    subtitle = result.subtitle,
                    artworkUrl = result.artworkUrl,
                    shape = shape,
                )
            }
        }
    }
}

/** Only a song can be the thing currently playing; the other card types are destinations. */
private fun AutomotiveSearchResult.isCurrent(currentlyPlayingMediaId: String?): Boolean =
    this is AutomotiveSearchResult.SongResult && song.mediaId == currentlyPlayingMediaId

/** The best result across every type, given more weight because it is the answer most taps want. */
@Composable
private fun TopResult(
    result: AutomotiveSearchResult,
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
            model = result.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(TopResultArtSize)
                .clip(RoundedCornerShape(CarCardCornerRadius)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Top result", color = NyasaGold, fontSize = LabelSize)
            Text(
                text = result.title,
                color = if (isPlaying) NyasaGold else Color.White,
                fontSize = TopResultTitleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.subtitle,
                color = CarTextSecondary,
                fontSize = BodySize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // A song plays from here; everything else opens a screen, and a "Play" button that
        // navigated instead would be lying about what the tap does.
        CarPillButton(
            label = when {
                result !is AutomotiveSearchResult.SongResult -> "Open"
                isPlaying -> "Playing"
                else -> "Play"
            },
            onClick = onClick,
        )
    }
}

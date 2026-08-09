package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.auto.ui.components.CarContentCard
import com.example.nyasaplayer.auto.ui.components.CarEmptyState
import com.example.nyasaplayer.auto.ui.components.CarSectionHeader
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarContentCardSize
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.core.common.models.Genre

private val GridSpacing = 24.dp
private val ListPadding = 24.dp
private const val BrowseGridColumns = 3
private val ScrollbarGap = 8.dp
private val ScrollbarWidth = 8.dp
private val ScrollbarTrackCornerRadius = 4.dp
private const val ScrollbarTrackAlpha = 0.15f
private const val ScrollbarThumbAlpha = 0.6f
private const val ScrollbarAnimationDurationMs = 150

/**
 * Browse.
 *
 * A grid of real genres. Tapping one shuffle-plays it — there is no `CarGenreScreen` among the
 * twenty, and playing on tap is what "Play/open category" means in its absence (D9).
 *
 * No filter chips: `Genre` is id, name, color, imageUrl, popularity and songIds, and nothing
 * backs "mood" or "category". Any chip set would be invented taxonomy (D11). No search field:
 * screen 4 lists none, screens 5 and 6 own it, and A6 relocates it (D10).
 */
@Composable
fun CarBrowseScreen(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    onLibraryClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    when {
        errorMessage != null && genres.isEmpty() -> CarEmptyState(
            title = "Something went wrong",
            body = errorMessage,
            modifier = modifier,
            actionLabel = "Try again",
            onAction = onRetry,
        )

        isLoading && genres.isEmpty() -> BrowseSkeleton(modifier = modifier)

        genres.isEmpty() -> CarEmptyState(
            title = "Nothing to browse yet",
            body = "Genres will appear here once your library has synced.",
            modifier = modifier,
            actionLabel = "Open Library",
            onAction = onLibraryClick,
        )

        else -> BrowseGrid(genres = genres, onGenreClick = onGenreClick, modifier = modifier)
    }
}

@Composable
private fun BrowseGrid(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = ScrollbarWidth + ScrollbarGap),
            contentPadding = PaddingValues(vertical = ListPadding),
            verticalArrangement = Arrangement.spacedBy(GridSpacing),
        ) {
            item { CarSectionHeader(title = "Browse by genre") }
            // Chunked rows rather than LazyVerticalGrid: the rest of this module lays out in
            // LazyColumn, and the scrollbar below reads LazyListState.
            items(genres.chunked(BrowseGridColumns)) { rowGenres ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                ) {
                    rowGenres.forEach { genre ->
                        CarContentCard(
                            title = genre.name,
                            onClick = { onGenreClick(genre) },
                            artworkUrl = genre.imageUrl,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Pads a short final row so its cards match the width of a full row above,
                    // rather than stretching to fill the row on their own.
                    repeat(BrowseGridColumns - rowGenres.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        VerticalScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 16.dp, horizontal = 8.dp),
        )
    }
}

/** Static placeholders, no shimmer — the ambient layer is the app's only decorative motion. */
@Composable
private fun BrowseSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(GridSpacing),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                repeat(BrowseGridColumns) {
                    Box(
                        modifier = Modifier
                            .size(CarContentCardSize)
                            .clip(RoundedCornerShape(CarCardCornerRadius))
                            .background(CarRaised),
                    )
                }
            }
        }
    }
}

private fun computeScrollbarInfo(listState: LazyListState): ScrollbarInfo? {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems == 0 || visibleItems.isEmpty()) return null

    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val totalContentHeight = visibleItems.sumOf { it.size } * totalItems.toFloat() / visibleItems.size
    val thumbRatio = (viewportHeight / totalContentHeight).coerceIn(0.1f, 1f)

    val firstVisibleIndex = visibleItems.first().index.toFloat()
    val firstVisibleOffset = visibleItems.first().offset.toFloat()
    val scrollFraction = if (totalItems <= 1) {
        0f
    } else {
        (firstVisibleIndex - firstVisibleOffset / viewportHeight) / (totalItems - 1).coerceAtLeast(1)
    }

    return ScrollbarInfo(thumbRatio, scrollFraction.coerceIn(0f, 1f))
}

@Composable
private fun VerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scrollbarInfo by remember { derivedStateOf { computeScrollbarInfo(listState) } }

    val animatedThumbRatio by animateFloatAsState(
        targetValue = scrollbarInfo?.thumbRatio ?: 1f,
        animationSpec = tween(ScrollbarAnimationDurationMs),
        label = "thumbRatio",
    )
    val animatedScrollFraction by animateFloatAsState(
        targetValue = scrollbarInfo?.scrollFraction ?: 0f,
        animationSpec = tween(ScrollbarAnimationDurationMs),
        label = "scrollFraction",
    )

    val trackColor = Color.White.copy(alpha = ScrollbarTrackAlpha)
    val thumbColor = Color.White.copy(alpha = ScrollbarThumbAlpha)
    val cornerRadiusPx = ScrollbarTrackCornerRadius.value

    Box(
        modifier = modifier
            .width(ScrollbarWidth)
            .drawWithContent {
                val trackHeight = size.height
                val scrollbarWidthPx = size.width

                // Track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset.Zero,
                    size = Size(scrollbarWidthPx, trackHeight),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )

                // Thumb
                val thumbHeight = (trackHeight * animatedThumbRatio).coerceAtLeast(scrollbarWidthPx * 2)
                val maxThumbOffset = trackHeight - thumbHeight
                val thumbOffset = maxThumbOffset * animatedScrollFraction

                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(0f, thumbOffset),
                    size = Size(scrollbarWidthPx, thumbHeight),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                )
            },
    )
}

private data class ScrollbarInfo(
    val thumbRatio: Float,
    val scrollFraction: Float,
)

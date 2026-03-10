package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarGradientBlue
import com.example.nyasaplayer.auto.ui.theme.CarGradientBlueCyan
import com.example.nyasaplayer.auto.ui.theme.CarGradientGreen
import com.example.nyasaplayer.auto.ui.theme.CarGradientGreenDark
import com.example.nyasaplayer.auto.ui.theme.CarGradientIndigo
import com.example.nyasaplayer.auto.ui.theme.CarGradientIndigoPurple
import com.example.nyasaplayer.auto.ui.theme.CarGradientOrange
import com.example.nyasaplayer.auto.ui.theme.CarGradientOrangeDark
import com.example.nyasaplayer.auto.ui.theme.CarGradientPink
import com.example.nyasaplayer.auto.ui.theme.CarGradientRose
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.ui.components.NowPlayingOverlay
import com.example.nyasaplayer.core.common.ui.icons.AlbumIcon
import com.example.nyasaplayer.core.common.ui.icons.MusicNoteIcon
import com.example.nyasaplayer.core.common.ui.icons.PlaylistAddIcon
import com.example.nyasaplayer.core.common.ui.icons.QueueMusicIcon
import com.example.nyasaplayer.core.common.ui.icons.RadioIcon
import com.example.nyasaplayer.core.common.ui.icons.SearchIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary

private val CategoryIconSize = 56.dp
private const val IconBgAlpha = 0.5f
private val PlaylistCardSize = 140.dp
private const val GradientOverlayStartY = 80f
private const val FeaturedPlaylistsMax = 10
private const val CategoryCardAspectRatio = 1.4f
private const val BrowseGridColumns = 3
private val ScrollbarWidth = 8.dp
private val ScrollbarTrackCornerRadius = 4.dp
private const val ScrollbarTrackAlpha = 0.15f
private const val ScrollbarThumbAlpha = 0.6f
private const val ScrollbarAnimationDurationMs = 150

private data class BrowseCategory(
    val name: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
)

private val browseCategories = listOf(
    BrowseCategory("Trending Now", MusicNoteIcon, listOf(CarGradientPink, CarGradientRose)),
    BrowseCategory("New Releases", AlbumIcon, listOf(CarGradientBlue, CarGradientBlueCyan)),
    BrowseCategory("Top Charts", QueueMusicIcon, listOf(CarGradientGreen, CarGradientGreenDark)),
    BrowseCategory("Playlists", PlaylistAddIcon, listOf(NyasaPrimary, NyasaPrimaryDark)),
    BrowseCategory("Genres", MusicNoteIcon, listOf(CarGradientOrange, CarGradientOrangeDark)),
    BrowseCategory("Podcasts", RadioIcon, listOf(CarGradientIndigoPurple, CarGradientIndigo)),
)

@Composable
fun CarBrowseScreen(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingAlbumId: String? = null,
    isPlaying: Boolean = false,
    onCategoryClick: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp + ScrollbarWidth + 8.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { BrowseSearchBar(onClick = onSearchClick) }
            item { BrowseAllGrid(onCategoryClick = onCategoryClick) }
            item {
                FeaturedPlaylistsSection(
                    albums = albums,
                    onAlbumClick = onAlbumClick,
                    currentlyPlayingAlbumId = currentlyPlayingAlbumId,
                    isPlaying = isPlaying,
                )
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

@Composable
private fun BrowseSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CarTouchTargetSize)
            .clip(RoundedCornerShape(CarCardCornerRadius))
            .background(NyasaSurface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = SearchIcon,
            contentDescription = "Search",
            tint = NyasaTextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = "Search songs, artists, albums...",
            color = NyasaTextSecondary,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun BrowseAllGrid(
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Browse All",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        browseCategories.chunked(BrowseGridColumns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { category ->
                    CategoryCard(
                        category = category,
                        onClick = { onCategoryClick(category.name) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: BrowseCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(CategoryCardAspectRatio)
            .clip(RoundedCornerShape(CarCardCornerRadius))
            .background(Brush.linearGradient(category.gradientColors))
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = IconBgAlpha),
            modifier = Modifier
                .size(CategoryIconSize)
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
        Text(
            text = category.name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        )
    }
}

@Composable
private fun FeaturedPlaylistsSection(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    currentlyPlayingAlbumId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Featured Playlists",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(albums.take(FeaturedPlaylistsMax), key = { it.id }) { album ->
                FeaturedPlaylistCard(
                    album = album,
                    isCurrentAlbum = album.id == currentlyPlayingAlbumId,
                    isPlaying = isPlaying,
                    onClick = { onAlbumClick(album) },
                )
            }
        }
    }
}

@Composable
private fun FeaturedPlaylistCard(
    album: Album,
    isCurrentAlbum: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(PlaylistCardSize)
            .clickable(onClick = onClick),
    ) {
        NowPlayingOverlay(
            isCurrentTrack = isCurrentAlbum,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(CarCardCornerRadius),
        ) {
            Box(
                modifier = Modifier
                    .size(PlaylistCardSize)
                    .clip(RoundedCornerShape(CarCardCornerRadius)),
            ) {
                AsyncImage(
                    model = album.imageUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                                startY = GradientOverlayStartY,
                            ),
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${album.songIds.size} songs",
            color = NyasaTextSecondary,
            fontSize = 12.sp,
        )
    }
}

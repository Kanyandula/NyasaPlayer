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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.nyasaplayer.auto.ui.components.CarCardShape
import com.example.nyasaplayer.auto.ui.components.CarContentCard
import com.example.nyasaplayer.auto.ui.components.CarEmptyState
import com.example.nyasaplayer.auto.ui.components.CarSectionHeader
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarContentCardSize
import com.example.nyasaplayer.auto.ui.theme.CarGlass
import com.example.nyasaplayer.auto.ui.theme.CarListArtSize
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.viewmodel.FavoriteArtist
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.NowPlayingOverlay

private val RowSpacing = 32.dp
private val CardSpacing = 24.dp
private val ListPadding = 24.dp

// 2 rows, matching BrowseSkeleton: 3 rows (604dp) clips the third inside Library's
// content slot (roughly 432dp), and this is a static placeholder, not scrollable content.
private const val SkeletonRowCount = 2
private const val SkeletonCardCount = 4

/**
 * Library.
 *
 * Six category rows, Recently played first. Rows put content one tap from a tab root, which is
 * what a moving vehicle needs — and it keeps album, playlist and artist detail at drill depth 1
 * rather than 2, where any real head unit's `maxContentDepth` would refuse them (D8).
 *
 * Favourites is one card showing the liked count, not a list: it is a shortcut to a rail
 * destination that already renders that list, and two surfaces rendering identical content is a
 * visible bug (A2 D2). Downloads is one card into screen 15, live since A9 — it rendered disabled
 * from A3 so Library would not change shape when it landed (D13). Account chrome is not here:
 * sign-out moved to Settings when
 * A7 built screen 14 (D68), closing D14.
 */
@Suppress("LongParameterList")
@Composable
fun CarLibraryScreen(
    recentlyPlayed: List<Song>,
    playlists: List<Playlist>,
    albums: List<Album>,
    favoriteArtists: List<FavoriteArtist>,
    likedSongCount: Int,
    onSongClick: (List<Song>, Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (FavoriteArtist) -> Unit,
    onFavouritesClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    val hasContent = recentlyPlayed.isNotEmpty() || playlists.isNotEmpty() ||
        albums.isNotEmpty() || favoriteArtists.isNotEmpty() || likedSongCount > 0

    Box(modifier = modifier.fillMaxSize()) {
        when {
            errorMessage != null && !hasContent -> CarEmptyState(
                title = "Something went wrong",
                body = errorMessage,
                actionLabel = "Try again",
                onAction = onRetry,
            )

            isLoading && !hasContent -> LibrarySkeleton()

            else -> LibraryRows(
                recentlyPlayed = recentlyPlayed,
                playlists = playlists,
                albums = albums,
                favoriteArtists = favoriteArtists,
                likedSongCount = likedSongCount,
                hasContent = hasContent,
                onSongClick = onSongClick,
                onPlaylistClick = onPlaylistClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onFavouritesClick = onFavouritesClick,
                onBrowseClick = onBrowseClick,
                onDownloadsClick = onDownloadsClick,
                currentlyPlayingMediaId = currentlyPlayingMediaId,
                isPlaying = isPlaying,
            )
        }
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun LibraryRows(
    recentlyPlayed: List<Song>,
    playlists: List<Playlist>,
    albums: List<Album>,
    favoriteArtists: List<FavoriteArtist>,
    likedSongCount: Int,
    hasContent: Boolean,
    onSongClick: (List<Song>, Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (FavoriteArtist) -> Unit,
    onFavouritesClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        item {
            LibraryHeader()
        }

        if (!hasContent) {
            item {
                CarEmptyState(
                    title = "Your library is empty",
                    body = "Play something and it will show up here.",
                    actionLabel = "Browse Music",
                    onAction = onBrowseClick,
                )
            }
        }

        // Every data row with no content is omitted rather than rendering an empty carousel.
        if (recentlyPlayed.isNotEmpty()) {
            item {
                LibraryRow(title = "Recently played") {
                    items(recentlyPlayed, key = { "recent_${it.mediaId}" }) { song ->
                        CarContentCard(
                            title = song.title,
                            onClick = { onSongClick(recentlyPlayed, song) },
                            subtitle = song.resolvedArtistName,
                            artworkUrl = song.resolvedCoverUrl,
                            isPlaying = isPlaying && song.mediaId == currentlyPlayingMediaId,
                        )
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item {
                LibraryRow(title = "Playlists") {
                    items(playlists, key = { "playlist_${it.id}" }) { playlist ->
                        CarContentCard(
                            title = playlist.name,
                            onClick = { onPlaylistClick(playlist) },
                            subtitle = "${playlist.songIds.size} songs",
                        )
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item {
                LibraryRow(title = "Albums") {
                    items(albums, key = { "album_${it.id}" }) { album ->
                        CarContentCard(
                            title = album.name,
                            onClick = { onAlbumClick(album) },
                            subtitle = album.artistName,
                            artworkUrl = album.imageUrl,
                        )
                    }
                }
            }
        }

        if (favoriteArtists.isNotEmpty()) {
            item {
                LibraryRow(title = "Artists") {
                    items(favoriteArtists, key = { "artist_${it.artistId}" }) { artist ->
                        CarContentCard(
                            title = artist.artistName,
                            onClick = { onArtistClick(artist) },
                            subtitle = "${artist.likedCount} liked",
                            artworkUrl = artist.coverUrl,
                            shape = CarCardShape.Circle,
                        )
                    }
                }
            }
        }

        if (likedSongCount > 0) {
            item {
                LibraryRow(title = "Favourites") {
                    item {
                        CarContentCard(
                            title = "Liked songs",
                            onClick = onFavouritesClick,
                            subtitle = "$likedSongCount songs",
                        )
                    }
                }
            }
        }

        // Never omitted, and no longer disabled: A9 gave it a screen (D13).
        item {
            LibraryRow(title = "Downloads") {
                item {
                    CarContentCard(
                        title = "Downloads",
                        onClick = onDownloadsClick,
                        subtitle = "Offline music",
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier) {
        CarSectionHeader(title = title, modifier = Modifier.padding(bottom = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            content = content,
        )
    }
}

/**
 * Row-shaped placeholders, so the headings stay put and the screen does not jump.
 *
 * Budget: the ~432dp content slot minus this composable's own `padding(vertical = ListPadding)`
 * (24dp top + 24dp bottom) leaves 384dp, exactly 2 * CarContentCardSize (180dp) + CardSpacing
 * (24dp). Using RowSpacing (32dp) here — the gap `LibraryRows` puts *between* rows — double-counts
 * padding the real layout doesn't have at this level, so the second row's box gets coerced down to
 * ~172dp. CardSpacing matches the 24dp gap BrowseSkeleton uses between its own two rows, for the
 * same reason.
 */
@Composable
private fun LibrarySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = ListPadding),
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        repeat(SkeletonRowCount) {
            Row(horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
                repeat(SkeletonCardCount) {
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

@Composable
private fun LibraryHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Your Library", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "All your music in one place",
            color = CarTextSecondary,
            fontSize = 18.sp,
        )
    }
}

@Composable
internal fun LikedSongItem(
    song: Song,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CarCardCornerRadius))
            .background(CarGlass)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NowPlayingOverlay(
            isCurrentTrack = isCurrentTrack,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(12.dp),
        ) {
            AsyncImage(
                model = song.resolvedCoverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(CarListArtSize)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.resolvedArtistName,
                color = CarTextSecondary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

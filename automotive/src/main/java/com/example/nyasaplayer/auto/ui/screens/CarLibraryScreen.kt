package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.auto.ui.components.CarCardShape
import com.example.nyasaplayer.auto.ui.components.CarContentCard
import com.example.nyasaplayer.auto.ui.components.CarEmptyState
import com.example.nyasaplayer.auto.ui.components.CarSectionHeader
import com.example.nyasaplayer.auto.ui.components.carConsumeTouches
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarContentCardSize
import com.example.nyasaplayer.auto.ui.theme.CarGlass
import com.example.nyasaplayer.auto.ui.theme.CarListArtSize
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarScrim
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.auto.viewmodel.FavoriteArtist
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.NowPlayingOverlay

private val SignOutRed = Color(0xFFEF5350)
private const val ModalWidthFraction = 0.5f

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
 * visible bug (A2 D2). Downloads renders visibly disabled rather than hidden, so Library does not
 * change shape when A8 lands (D13). Sign-out stays here until A7 owns screen 14 (D14).
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
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    userDisplayName: String = "",
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    var showSignOutConfirmation by remember { mutableStateOf(false) }
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
                userDisplayName = userDisplayName,
                onSignOutClick = { showSignOutConfirmation = true },
                currentlyPlayingMediaId = currentlyPlayingMediaId,
                isPlaying = isPlaying,
            )
        }

        if (showSignOutConfirmation) {
            SignOutConfirmationOverlay(
                onConfirm = onSignOut,
                onDismiss = { showSignOutConfirmation = false },
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
    userDisplayName: String,
    onSignOutClick: () -> Unit,
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
            LibraryHeader(userDisplayName = userDisplayName, onSignOutClick = onSignOutClick)
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

        // Never omitted: it carries no data by definition, and hiding it would change
        // Library's shape when A8 lands (D13).
        item {
            LibraryRow(title = "Downloads") {
                item {
                    CarContentCard(
                        title = "Downloads",
                        onClick = {},
                        subtitle = "Coming soon",
                        enabled = false,
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
private fun LibraryHeader(
    userDisplayName: String,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Your Library", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (userDisplayName.isNotEmpty()) {
                    "Signed in as $userDisplayName"
                } else {
                    "All your music in one place"
                },
                color = CarTextSecondary,
                fontSize = 18.sp,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .height(CarTouchTargetSize)
                .clip(RoundedCornerShape(16.dp))
                .background(SignOutRed.copy(alpha = 0.15f))
                .clickable(onClick = onSignOutClick)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Sign Out",
                    tint = SignOutRed,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Sign Out",
                    color = SignOutRed,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SignOutConfirmationOverlay(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarScrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        SignOutModalCard(onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
private fun SignOutModalCard(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(ModalWidthFraction)
            .clip(RoundedCornerShape(24.dp))
            .background(CarGlass)
            .carConsumeTouches()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sign Out?",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "You will need to sign in again to access your music library.",
            color = CarTextSecondary,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
        )
        Spacer(modifier = Modifier.height(32.dp))
        SignOutActions(onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
private fun SignOutActions(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(onClick = onDismiss)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cancel",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(SignOutRed)
                .clickable(onClick = onConfirm)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Sign Out",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
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

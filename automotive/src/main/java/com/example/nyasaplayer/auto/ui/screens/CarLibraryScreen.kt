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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarListArtSize
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.NowPlayingOverlay
import com.example.nyasaplayer.core.common.ui.components.ShufflePlayButton
import com.example.nyasaplayer.core.common.ui.icons.MusicNoteIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary

private val AlbumItemArtSize = 64.dp
private val SignOutRed = Color(0xFFEF5350)
private const val BackdropAlpha = 0.8f
private const val ModalWidthFraction = 0.5f

@Suppress("LongParameterList", "LongMethod")
@Composable
fun CarLibraryScreen(
    artists: List<Artist>,
    albums: List<Album>,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    likedSongs: List<Song> = emptyList(),
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
    onShuffleLikedSongs: () -> Unit = {},
    onLikedSongClick: (Song) -> Unit = {},
    onSignOut: () -> Unit = {},
    userDisplayName: String = "",
) {
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(NyasaBackground)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                LibraryHeader(
                    userDisplayName = userDisplayName,
                    onSignOutClick = { showSignOutConfirmation = true },
                )
            }

            if (likedSongs.isNotEmpty()) {
                item { LikedSongsHeader(count = likedSongs.size) }
                item {
                    ShufflePlayButton(
                        onClick = onShuffleLikedSongs,
                        height = CarTouchTargetSize,
                    )
                }
                items(likedSongs, key = { it.mediaId }) { song ->
                    LikedSongItem(
                        song = song,
                        isCurrentTrack = song.mediaId == currentlyPlayingMediaId,
                        isPlaying = isPlaying,
                        onClick = { onLikedSongClick(song) },
                    )
                }
            }

            if (artists.isNotEmpty()) {
                item {
                    FavoriteArtistsSection(
                        artists = artists,
                        onArtistClick = onArtistClick,
                    )
                }
            }

            if (albums.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Albums",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(albums, key = { it.id }) { album ->
                    AlbumListItem(album = album, onClick = { onAlbumClick(album) })
                }
            }
        }

        if (showSignOutConfirmation) {
            SignOutConfirmationOverlay(
                onConfirm = onSignOut,
                onDismiss = { showSignOutConfirmation = false },
            )
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
                color = NyasaTextSecondary,
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
            .background(Color.Black.copy(alpha = BackdropAlpha))
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
            .background(NyasaSurface2)
            .clickable(enabled = false, onClick = {})
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
            color = NyasaTextSecondary,
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
private fun LikedSongsHeader(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = NyasaPrimary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = "Liked Songs",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$count songs",
            color = NyasaTextSecondary,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun LikedSongItem(
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
            .background(NyasaSurface2)
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
                color = NyasaTextSecondary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FavoriteArtistsSection(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Favorite Artists",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(artists, key = { it.id }) { artist ->
                ArtistAvatar(artist = artist, onClick = { onArtistClick(artist) })
            }
        }
    }
}

@Composable
private fun ArtistAvatar(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(CarListArtSize)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SubcomposeAsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(CarListArtSize)
                .clip(CircleShape),
            loading = { ArtistPlaceholder() },
            error = { ArtistPlaceholder() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ArtistPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(NyasaPrimary, NyasaPrimaryDark))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MusicNoteIcon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun AlbumListItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CarCardCornerRadius))
            .background(NyasaSurface2)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = album.imageUrl,
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(AlbumItemArtSize)
                .clip(RoundedCornerShape(12.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = album.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artistName,
                color = NyasaTextSecondary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(CarTouchTargetSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Icon(Icons.Default.PlayArrow, "Play ${album.name}", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Artist
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary

private val ArtistAvatarSize = 80.dp
private val AlbumArtSize = 64.dp
private val AlbumPlayButtonSize = 76.dp
private const val MaxArtists = 8
private const val MaxAlbums = 6

@Composable
fun CarLibraryScreen(
    artists: List<Artist>,
    albums: List<Album>,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground)
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { LibraryHeader() }

        if (artists.isNotEmpty()) {
            item {
                FavoriteArtistsSection(
                    artists = artists.take(MaxArtists),
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
            items(albums.take(MaxAlbums), key = { it.id }) { album ->
                AlbumListItem(album = album, onClick = { onAlbumClick(album) })
            }
        }
    }
}

@Composable
private fun LibraryHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("Your Library", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("All your music in one place", color = NyasaTextSecondary, fontSize = 18.sp)
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
            .width(ArtistAvatarSize)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(ArtistAvatarSize)
                .clip(CircleShape),
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
private fun AlbumListItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
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
                .size(AlbumArtSize)
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
                .size(AlbumPlayButtonSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Icon(Icons.Default.PlayArrow, "Play ${album.name}", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

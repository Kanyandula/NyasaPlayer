package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Genre
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimaryDark
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary

private val AccentBarWidth = 4.dp
private val AccentBarHeight = 48.dp
private val GenrePlayButtonSize = 76.dp
private const val FeaturedPlaylistsMax = 6
private const val GradientOverlayStartY = 100f

@Composable
fun CarBrowseScreen(
    genres: List<Genre>,
    albums: List<Album>,
    onGenreClick: (Genre) -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground)
            .padding(24.dp),
    ) {
        BrowseHeader()
        Spacer(modifier = Modifier.height(20.dp))
        BrowseContent(
            genres = genres,
            albums = albums,
            onGenreClick = onGenreClick,
            onAlbumClick = onAlbumClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BrowseHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("Browse Music", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Explore playlists and genres", color = NyasaTextSecondary, fontSize = 18.sp)
    }
}

@Composable
private fun BrowseContent(
    genres: List<Genre>,
    albums: List<Album>,
    onGenreClick: (Genre) -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FeaturedPlaylistsColumn(
            albums = albums,
            onAlbumClick = onAlbumClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        GenresColumn(
            genres = genres,
            onGenreClick = onGenreClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun FeaturedPlaylistsColumn(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Featured Playlists",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(albums.take(FeaturedPlaylistsMax), key = { it.id }) { album ->
                FeaturedPlaylistCard(album = album, onClick = { onAlbumClick(album) })
            }
        }
    }
}

@Composable
private fun GenresColumn(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Browse by Genre",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(genres, key = { it.id }) { genre ->
                GenreItem(genre = genre, onClick = { onGenreClick(genre) })
            }
        }
    }
}

@Composable
private fun FeaturedPlaylistCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
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
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = GradientOverlayStartY,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = album.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${album.songIds.size} tracks",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun GenreItem(
    genre: Genre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NyasaSurface2)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(AccentBarWidth)
                    .height(AccentBarHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(NyasaPrimary, NyasaPrimaryDark))),
            )
            Column {
                Text(
                    text = genre.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${genre.songIds.size} songs",
                    color = NyasaTextSecondary,
                    fontSize = 16.sp,
                )
            }
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(GenrePlayButtonSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Icon(Icons.Default.PlayArrow, "Play ${genre.name}", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

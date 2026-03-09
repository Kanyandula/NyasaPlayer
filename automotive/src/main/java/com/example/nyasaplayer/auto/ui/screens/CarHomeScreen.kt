
package com.example.nyasaplayer.auto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.NowPlayingOverlay
import com.example.nyasaplayer.core.common.ui.icons.HeartIcon
import com.example.nyasaplayer.core.common.ui.icons.MusicNoteIcon
import com.example.nyasaplayer.core.common.ui.icons.RadioIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaBackground
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.util.greetingResource

private val AlbumArtSize = 80.dp

// Quick action gradient colors
private val MyMusicGradientStart = Color(0xFFA855F7)
private val MyMusicGradientEnd = Color(0xFF7C3AED)
private val RadioGradientStart = Color(0xFFEC4899)
private val RadioGradientEnd = Color(0xFFBE123C)
private val FavoritesGradientStart = Color(0xFFEF4444)
private val FavoritesGradientEnd = Color(0xFFB91C1C)
private val TrendingGradientStart = Color(0xFF3B82F6)
private val TrendingGradientEnd = Color(0xFF4338CA)

@Composable
fun CarHomeScreen(
    recentlyPlayed: List<Song>,
    onSongClick: (Song) -> Unit,
    onQuickActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentlyPlayingMediaId: String? = null,
    isPlaying: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NyasaBackground)
            .padding(24.dp),
    ) {
        HomeHeader()
        Spacer(modifier = Modifier.height(20.dp))
        HomeContent(
            recentlyPlayed = recentlyPlayed,
            onSongClick = onSongClick,
            onQuickActionClick = onQuickActionClick,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isPlaying = isPlaying,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = greetingResource(),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Ready for your drive?",
            color = NyasaTextSecondary,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun HomeContent(
    recentlyPlayed: List<Song>,
    onSongClick: (Song) -> Unit,
    onQuickActionClick: (String) -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        QuickActionsColumn(
            onQuickActionClick = onQuickActionClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        RecentlyPlayedColumn(
            songs = recentlyPlayed,
            onSongClick = onSongClick,
            currentlyPlayingMediaId = currentlyPlayingMediaId,
            isPlaying = isPlaying,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun QuickActionsColumn(
    onQuickActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Quick Access",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                QuickActionCard(
                    icon = MusicNoteIcon,
                    label = "My Music",
                    gradientColors = listOf(MyMusicGradientStart, MyMusicGradientEnd),
                    onClick = { onQuickActionClick("my_music") },
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    icon = RadioIcon,
                    label = "Radio",
                    gradientColors = listOf(RadioGradientStart, RadioGradientEnd),
                    onClick = { onQuickActionClick("radio") },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                QuickActionCard(
                    icon = HeartIcon,
                    label = "Favorites",
                    gradientColors = listOf(FavoritesGradientStart, FavoritesGradientEnd),
                    onClick = { onQuickActionClick("favorites") },
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    icon = Icons.Default.PlayArrow,
                    label = "Trending",
                    gradientColors = listOf(TrendingGradientStart, TrendingGradientEnd),
                    onClick = { onQuickActionClick("trending") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RecentlyPlayedColumn(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    currentlyPlayingMediaId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Recently Played",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(songs, key = { it.mediaId }) { song ->
                RecentlyPlayedItem(
                    song = song,
                    isCurrentTrack = song.mediaId == currentlyPlayingMediaId,
                    isPlaying = isPlaying,
                    onClick = { onSongClick(song) },
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradientColors))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(48.dp))
            Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RecentlyPlayedItem(
    song: Song,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
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
                    .size(AlbumArtSize)
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

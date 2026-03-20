package com.example.nyasaplayer.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.nyasaplayer.R
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.icons.MoreHorizIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary
import com.example.nyasaplayer.core.common.util.formatDuration

private val SongRowThumbnailSize = 48.dp
private val SongRowThumbnailRadius = 8.dp

@Composable
fun SongRow(
    song: Song,
    trackNumber: Int,
    isCurrentTrack: Boolean,
    isAnimating: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowBackground = if (isCurrentTrack) NyasaPrimary.copy(alpha = 0.15f) else Color.Transparent
    val titleColor = if (isCurrentTrack) NyasaPrimary else Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackNumberIndicator(
            trackNumber = trackNumber,
            isCurrentTrack = isCurrentTrack,
            isAnimating = isAnimating,
        )
        Spacer(modifier = Modifier.width(8.dp))
        AsyncImage(
            model = song.resolvedCoverUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(SongRowThumbnailSize)
                .clip(RoundedCornerShape(SongRowThumbnailRadius)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.resolvedArtistName,
                style = MaterialTheme.typography.bodySmall,
                color = NyasaTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (song.durationMs > 0) {
            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = NyasaTextTertiary,
            )
        }
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = MoreHorizIcon,
                contentDescription = stringResource(R.string.more_options),
                tint = NyasaTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

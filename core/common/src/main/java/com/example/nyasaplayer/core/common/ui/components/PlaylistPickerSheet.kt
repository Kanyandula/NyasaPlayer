package com.example.nyasaplayer.core.common.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nyasaplayer.core.common.R
import com.example.nyasaplayer.core.common.models.Playlist
import com.example.nyasaplayer.core.common.ui.icons.PlaylistAddIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaPrimary
import com.example.nyasaplayer.core.common.ui.theme.NyasaSurface2
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
    playlists: List<Playlist>,
    onSelectPlaylist: (Playlist) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onCreateNew: () -> Unit = {},
    title: String? = null,
    showCreateNew: Boolean = true,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NyasaSurface2,
        dragHandle = { BottomSheetDefaults.DragHandle(color = NyasaTextTertiary) },
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = title ?: stringResource(R.string.select_playlist),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (showCreateNew) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCreateNew)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = NyasaPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.create_new_playlist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NyasaPrimary,
                    )
                }
            }
            if (playlists.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_playlists_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NyasaTextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            } else {
                LazyColumn {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onSelectPlaylist(playlist) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PlaylistAddIcon,
            contentDescription = null,
            tint = NyasaTextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.songIds.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = NyasaTextSecondary,
            )
        }
    }
}

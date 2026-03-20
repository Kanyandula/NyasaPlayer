package com.example.nyasaplayer.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.SongDownloadState
import com.example.nyasaplayer.core.common.ui.components.SongOverflowSheet
import com.example.nyasaplayer.download.SongDownloadManager

@Composable
fun SongOverflowWithDownload(
    song: Song,
    downloadManager: SongDownloadManager?,
    onDismiss: () -> Unit,
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {},
    onSaveToPlaylist: () -> Unit = {},
    showRemoveFromPlaylist: Boolean = false,
    onRemoveFromPlaylist: () -> Unit = {},
) {
    val downloadState = remember(song.mediaId) {
        resolveDownloadState(song.mediaId, downloadManager)
    }
    SongOverflowSheet(
        song = song,
        onDismiss = onDismiss,
        isLiked = isLiked,
        onToggleLike = onToggleLike,
        downloadState = downloadState,
        onDownloadClick = { s ->
            downloadManager?.downloadSong(s.mediaId)
            onDismiss()
        },
        onRemoveDownloadClick = { s ->
            downloadManager?.removeDownload(s.mediaId)
            onDismiss()
        },
        onSaveToPlaylist = {
            onDismiss()
            onSaveToPlaylist()
        },
        showRemoveFromPlaylist = showRemoveFromPlaylist,
        onRemoveFromPlaylist = {
            onDismiss()
            onRemoveFromPlaylist()
        },
    )
}

fun resolveDownloadState(
    mediaId: String,
    downloadManager: SongDownloadManager?,
): SongDownloadState {
    if (downloadManager == null) return SongDownloadState.NotDownloaded
    val localUri = downloadManager.getLocalFileUri(mediaId)
    return when {
        localUri != null -> SongDownloadState.Downloaded
        downloadManager.activeDownloads.value.contains(mediaId) -> SongDownloadState.Downloading
        else -> SongDownloadState.NotDownloaded
    }
}

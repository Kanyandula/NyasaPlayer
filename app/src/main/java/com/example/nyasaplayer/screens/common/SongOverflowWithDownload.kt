package com.example.nyasaplayer.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.SongDownloadState
import com.example.nyasaplayer.core.common.ui.components.SongOverflowSheet
import com.example.nyasaplayer.core.data.download.SongDownloadManager
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import java.io.File

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
    val downloadState = rememberDownloadState(song.mediaId, downloadManager)
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

/**
 * The sheet's download state, observed from the row rather than read from the in-memory path
 * index, which is empty for a moment after process start (T32).
 *
 * The `exists` check is the one `localUriFor` does, keyed on the path so it runs when the row
 * changes rather than on every recomposition. One frame of NotDownloaded precedes the first row
 * emission — the trade for never being stale.
 */
@Composable
private fun rememberDownloadState(
    mediaId: String,
    downloadManager: SongDownloadManager?,
): SongDownloadState {
    if (downloadManager == null) return SongDownloadState.NotDownloaded
    val rows = remember(mediaId, downloadManager) { downloadManager.observeDownload(mediaId) }
    val download by rows.collectAsState(initial = null)
    val active by downloadManager.activeDownloads.collectAsState()
    val onDisk = remember(download?.filePath) {
        download?.filePath?.takeIf { it.isNotBlank() }?.let { File(it).exists() } == true
    }
    return downloadStateOf(download?.status, onDisk, isActive = mediaId in active)
}

/**
 * The sheet's three states from what is known about a song.
 *
 * [onDisk] is separate from a `Completed` [status] on purpose: the row outlives the file when
 * storage is cleared, and the player falls back to the stream then, so the sheet must offer
 * Download rather than Remove or the two disagree.
 */
internal fun downloadStateOf(
    status: DownloadStatus?,
    onDisk: Boolean,
    isActive: Boolean,
): SongDownloadState = when {
    status == DownloadStatus.Completed && onDisk -> SongDownloadState.Downloaded
    isActive || status == DownloadStatus.Downloading || status == DownloadStatus.Pending ->
        SongDownloadState.Downloading
    else -> SongDownloadState.NotDownloaded
}

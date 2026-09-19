package com.example.nyasaplayer.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.ui.components.SongDownloadState
import com.example.nyasaplayer.core.common.ui.components.SongOverflowSheet
import com.example.nyasaplayer.core.data.download.SongDownloadManager
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus

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
 * The sheet's download state, from the database rather than the in-memory path index.
 *
 * It used to read `getLocalFileUri` inside a `remember`, which was wrong twice over: the index
 * loads asynchronously at process start, so an early read said "not downloaded" about a song that
 * is (T32), and `remember` then kept that answer for the life of the composition. Observing the
 * row fixes both — and it also updates while a download runs, which the old read never did.
 */
@Composable
fun rememberDownloadState(
    mediaId: String,
    downloadManager: SongDownloadManager?,
): SongDownloadState {
    if (downloadManager == null) return SongDownloadState.NotDownloaded
    val download by downloadManager.observeDownload(mediaId).collectAsState(initial = null)
    val active by downloadManager.activeDownloads.collectAsState()
    return when {
        download?.status == DownloadStatus.Completed -> SongDownloadState.Downloaded
        mediaId in active -> SongDownloadState.Downloading
        download?.status == DownloadStatus.Downloading ||
            download?.status == DownloadStatus.Pending -> SongDownloadState.Downloading
        else -> SongDownloadState.NotDownloaded
    }
}

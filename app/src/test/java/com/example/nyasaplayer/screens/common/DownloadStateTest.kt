package com.example.nyasaplayer.screens.common

import com.example.nyasaplayer.core.common.ui.components.SongDownloadState
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the overflow sheet offers for a song (T32). */
class DownloadStateTest {

    @Test
    fun completedAndOnDisk_isDownloaded() {
        assertEquals(
            SongDownloadState.Downloaded,
            downloadStateOf(DownloadStatus.Completed, onDisk = true, isActive = false),
        )
    }

    /**
     * The row outlives the file when storage is cleared. The player falls back to the stream in
     * that case, so the sheet has to offer Download — otherwise the two disagree and the only way
     * back is to remove a download that is not there.
     */
    @Test
    fun completedButTheFileIsGone_isNotDownloaded() {
        assertEquals(
            SongDownloadState.NotDownloaded,
            downloadStateOf(DownloadStatus.Completed, onDisk = false, isActive = false),
        )
    }

    @Test
    fun noRowAtAll_isNotDownloaded() {
        assertEquals(
            SongDownloadState.NotDownloaded,
            downloadStateOf(status = null, onDisk = false, isActive = false),
        )
    }

    @Test
    fun activeDownload_isDownloading() {
        assertEquals(
            SongDownloadState.Downloading,
            downloadStateOf(status = null, onDisk = false, isActive = true),
        )
    }

    @Test
    fun pendingOrRunningRow_isDownloading() {
        assertEquals(
            SongDownloadState.Downloading,
            downloadStateOf(DownloadStatus.Pending, onDisk = false, isActive = false),
        )
        assertEquals(
            SongDownloadState.Downloading,
            downloadStateOf(DownloadStatus.Downloading, onDisk = false, isActive = false),
        )
    }

    /** A failed row is not a download in progress. */
    @Test
    fun failedRow_isNotDownloaded() {
        assertEquals(
            SongDownloadState.NotDownloaded,
            downloadStateOf(DownloadStatus.Failed, onDisk = false, isActive = false),
        )
    }
}

package com.example.nyasaplayer.core.data.download

import android.content.Context
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.NetworkMonitor
import com.example.nyasaplayer.core.data.fake.FakeDownloadDao
import com.example.nyasaplayer.core.data.fake.FakeSongRepository
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import com.example.nyasaplayer.core.data.offline.OfflineDownloadRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * A download refused before it starts leaves a trace.
 *
 * `markFailed` is an `UPDATE`, so until a row exists it writes nothing: an offline tap used to
 * vanish on both surfaces — no failed row for the car's Downloads screen to show, and nothing for
 * mobile to say (`docs/T29_VERIFICATION.md`, 2026-09-19).
 *
 * Against the real [OfflineDownloadRepository] rather than a fake of it, because the bug was in
 * how `addDownload` and `markFailed` meet. `NetworkMonitor` starts offline and only a connectivity
 * callback makes it online; Robolectric delivers none, so this manager is genuinely offline.
 */
@RunWith(RobolectricTestRunner::class)
class SongDownloadManagerRefusalTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun managerWith(dao: FakeDownloadDao): Pair<SongDownloadManager, OfflineDownloadRepository> {
        val downloads = OfflineDownloadRepository(dao)
        val songs = FakeSongRepository().apply {
            this.songs.value = listOf(Song(mediaId = MediaId, audioUrl = "https://cdn.example/a.mp3"))
        }
        return SongDownloadManager(context, downloads, songs, NetworkMonitor(context)) to downloads
    }

    @Test
    fun offline_recordsTheAttemptAsFailed() = runBlocking {
        val dao = FakeDownloadDao()
        val (manager, _) = managerWith(dao)

        manager.downloadSong(MediaId)

        val row = withTimeout(TimeoutMs) {
            dao.observeByMediaId(MediaId).first { it?.status == DownloadStatus.Failed }
        }
        assertEquals(DownloadStatus.Failed, requireNotNull(row).status)
    }

    @Test
    fun offline_saysWhyItRefused() = runBlocking {
        val dao = FakeDownloadDao()
        val (manager, _) = managerWith(dao)
        val refusal = withTimeout(TimeoutMs) {
            manager.refusals.onSubscription { manager.downloadSong(MediaId) }.first()
        }

        assertEquals(DownloadRefusal.Offline, refusal)
    }

    /**
     * A song already on disk is left alone. Without the guard the row survives — `addDownload` is
     * skipped for an existing one — but `markFailed` still flips `Completed` to `Failed`, and a
     * good download then reads as not downloaded everywhere.
     */
    @Test
    fun offline_songAlreadyOnDisk_isNotTouched() = runBlocking {
        val file = File(context.filesDir, "downloads/$MediaId.audio").apply {
            parentFile?.mkdirs()
            writeText("audio")
        }
        val dao = FakeDownloadDao(
            listOf(
                DownloadEntity(
                    mediaId = MediaId,
                    status = DownloadStatus.Completed,
                    filePath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    downloadedAt = 1L,
                ),
            ),
        )
        val (manager, downloads) = managerWith(dao)
        dao.releaseCompletedLoad()
        withTimeout(TimeoutMs) { downloads.awaitDownloadIndex() }

        manager.downloadSong(MediaId)
        delay(SettleMs)

        assertEquals(
            "a download that is present on disk was downgraded to Failed",
            DownloadStatus.Completed,
            dao.getByMediaId(MediaId)?.status,
        )
    }

    /**
     * The same song, with the path index **not** loaded — the window T32 is about.
     *
     * An earlier version of the guard asked `localUriFor`, which reads that index, so inside this
     * window it missed and the row was downgraded. The guard reads the row, so the gate below is
     * deliberately left closed.
     */
    @Test
    fun offline_songOnDiskButIndexNotLoaded_isStillNotTouched() = runBlocking {
        val file = File(context.filesDir, "downloads/$MediaId.audio").apply {
            parentFile?.mkdirs()
            writeText("audio")
        }
        val dao = FakeDownloadDao(
            listOf(
                DownloadEntity(
                    mediaId = MediaId,
                    status = DownloadStatus.Completed,
                    filePath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    downloadedAt = 1L,
                ),
            ),
        )
        val (manager, _) = managerWith(dao)

        manager.downloadSong(MediaId)
        delay(SettleMs)

        assertEquals(
            "the guard read the path index, which has not loaded, and downgraded a good download",
            DownloadStatus.Completed,
            dao.getByMediaId(MediaId)?.status,
        )
    }

    private companion object {
        const val MediaId = "a"
        const val TimeoutMs = 5_000L
        const val SettleMs = 150L
    }
}

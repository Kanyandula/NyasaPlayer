package com.example.nyasaplayer.core.data.download

import android.content.Context
import com.example.nyasaplayer.core.common.models.Song
import com.example.nyasaplayer.core.common.util.NetworkMonitor
import com.example.nyasaplayer.core.data.api.DownloadRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import com.example.nyasaplayer.core.data.local.entity.DownloadEntity
import com.example.nyasaplayer.core.data.local.entity.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val BufferSize = 8192
private const val PercentMultiplier = 100
private const val ConnectTimeoutMs = 15_000
private const val ReadTimeoutMs = 30_000

@Singleton
class SongDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val songRepository: SongRepository,
    private val networkMonitor: NetworkMonitor,
) : SongDownloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadsDir: File = File(context.filesDir, "downloads").apply { mkdirs() }

    private val _activeDownloads = MutableStateFlow<Set<String>>(emptySet())
    val activeDownloads: StateFlow<Set<String>> = _activeDownloads.asStateFlow()

    private val _refusals = MutableSharedFlow<DownloadRefusal>(extraBufferCapacity = 1)

    /**
     * Downloads that were asked for and refused before they began.
     *
     * The car shows a failed row on its Downloads screen, which is its own answer; mobile's
     * Downloads screen lists completed songs only and the person who tapped is somewhere else
     * entirely, so `NyasaPlayerApp` turns these into a snackbar.
     */
    val refusals: SharedFlow<DownloadRefusal> = _refusals.asSharedFlow()

    init {
        scope.launch { downloadRepository.resetStaleDownloads() }
    }

    override fun downloadSong(mediaId: String) {
        if (_activeDownloads.value.contains(mediaId)) return
        scope.launch {
            try {
                val existing = downloadRepository.getDownload(mediaId)
                if (existing.isOnDisk()) return@launch
                // Recorded before it can be refused: markFailed is an UPDATE, so until a row
                // exists it writes nothing and the refusal leaves no trace at all. addDownload
                // upserts a bare Pending row, so an existing one is left as it is.
                if (existing == null) downloadRepository.addDownload(mediaId)
                if (!networkMonitor.isOnline.value) {
                    refuse(mediaId, DownloadRefusal.Offline)
                    return@launch
                }
                val songs = songRepository.getSongsByIds(listOf(mediaId))
                val song = songs.firstOrNull() ?: run {
                    refuse(mediaId, DownloadRefusal.Unavailable)
                    return@launch
                }
                val audioUrl = song.resolvedAudioUrl
                if (audioUrl.isBlank()) {
                    refuse(mediaId, DownloadRefusal.Unavailable)
                    return@launch
                }
                _activeDownloads.update { it + mediaId }
                performDownload(mediaId, audioUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                downloadRepository.markFailed(mediaId)
            } finally {
                _activeDownloads.update { it - mediaId }
            }
        }
    }

    /**
     * Already downloaded and still there.
     *
     * Read from the row rather than `localUriFor`, which goes through the in-memory path index —
     * empty for a moment after process start (T32), and a miss here would let the code below mark
     * a good download Failed.
     */
    private fun DownloadEntity?.isOnDisk(): Boolean =
        this != null && status == DownloadStatus.Completed && filePath.isNotBlank() &&
            File(filePath).exists()

    private suspend fun refuse(mediaId: String, reason: DownloadRefusal) {
        downloadRepository.markFailed(mediaId)
        _refusals.emit(reason)
    }

    /** Not on [SongDownloads]: no screen offers it. Cancelling mid-flight is still phone-only. */
    fun cancelDownload(mediaId: String) {
        scope.launch {
            _activeDownloads.update { it - mediaId }
            val file = File(downloadsDir, "$mediaId.audio")
            if (file.exists()) file.delete()
            downloadRepository.removeDownload(mediaId)
        }
    }

    override fun removeDownload(mediaId: String) {
        scope.launch {
            val download = downloadRepository.getDownload(mediaId)
            if (download != null) {
                val file = File(download.filePath)
                if (file.exists()) file.delete()
            }
            downloadRepository.removeDownload(mediaId)
        }
    }

    override fun removeAllDownloads() {
        scope.launch {
            downloadsDir.listFiles()?.forEach { it.delete() }
            downloadRepository.removeAllDownloads()
        }
    }

    override fun retryDownload(mediaId: String) {
        downloadSong(mediaId)
    }

    /** The download row for [mediaId] as it changes, for screens that render its state. */
    fun observeDownload(mediaId: String): Flow<DownloadEntity?> =
        downloadRepository.observeDownload(mediaId)

    /** [DownloadRepository.resolveLocalUri], for callers that already hold the manager. */
    fun resolveLocalUri(song: Song): Song = downloadRepository.resolveLocalUri(song)

    @Suppress("NestedBlockDepth")
    private suspend fun performDownload(mediaId: String, audioUrl: String) {
        val outputFile = File(downloadsDir, "$mediaId.audio")
        val connection = URL(audioUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = ConnectTimeoutMs
            connection.readTimeout = ReadTimeoutMs
            connection.instanceFollowRedirects = true
            connection.connect()
            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L
            var lastReportedProgress = -1
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(BufferSize)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!_activeDownloads.value.contains(mediaId)) {
                            outputFile.delete()
                            return
                        }
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes * PercentMultiplier / totalBytes).toInt()
                            if (progress != lastReportedProgress) {
                                lastReportedProgress = progress
                                downloadRepository.updateProgress(mediaId, progress)
                            }
                        }
                    }
                }
            }
            val fileSize = outputFile.length()
            downloadRepository.markCompleted(mediaId, outputFile.absolutePath, fileSize)
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            outputFile.delete()
            downloadRepository.markFailed(mediaId)
        } finally {
            connection.disconnect()
        }
    }
}

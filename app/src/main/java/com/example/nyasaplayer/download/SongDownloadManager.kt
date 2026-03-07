package com.example.nyasaplayer.download

import android.content.Context
import com.example.nyasaplayer.core.common.util.NetworkMonitor
import com.example.nyasaplayer.core.data.api.DownloadRepository
import com.example.nyasaplayer.core.data.api.SongRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@Singleton
class SongDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val songRepository: SongRepository,
    private val networkMonitor: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadsDir: File = File(context.filesDir, "downloads").apply { mkdirs() }

    private val _activeDownloads = MutableStateFlow<Set<String>>(emptySet())
    val activeDownloads: StateFlow<Set<String>> = _activeDownloads.asStateFlow()

    fun downloadSong(mediaId: String) {
        if (_activeDownloads.value.contains(mediaId)) return
        scope.launch {
            try {
                if (!networkMonitor.isOnline.value) {
                    downloadRepository.markFailed(mediaId)
                    return@launch
                }
                val songs = songRepository.getSongsByIds(listOf(mediaId))
                val song = songs.firstOrNull() ?: run {
                    downloadRepository.markFailed(mediaId)
                    return@launch
                }
                val audioUrl = song.resolvedAudioUrl
                if (audioUrl.isBlank()) {
                    downloadRepository.markFailed(mediaId)
                    return@launch
                }
                downloadRepository.addDownload(mediaId)
                _activeDownloads.value = _activeDownloads.value + mediaId
                performDownload(mediaId, audioUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                downloadRepository.markFailed(mediaId)
            } finally {
                _activeDownloads.value = _activeDownloads.value - mediaId
            }
        }
    }

    fun cancelDownload(mediaId: String) {
        scope.launch {
            _activeDownloads.value = _activeDownloads.value - mediaId
            val file = File(downloadsDir, "$mediaId.audio")
            if (file.exists()) file.delete()
            downloadRepository.removeDownload(mediaId)
        }
    }

    fun removeDownload(mediaId: String) {
        scope.launch {
            val download = downloadRepository.getDownload(mediaId)
            if (download != null) {
                val file = File(download.filePath)
                if (file.exists()) file.delete()
            }
            downloadRepository.removeDownload(mediaId)
        }
    }

    fun removeAllDownloads() {
        scope.launch {
            downloadsDir.listFiles()?.forEach { it.delete() }
            downloadRepository.removeAllDownloads()
        }
    }

    fun retryDownload(mediaId: String) {
        downloadSong(mediaId)
    }

    fun getLocalFileUri(mediaId: String): String? {
        val path = downloadRepository.getLocalFilePath(mediaId) ?: return null
        val file = File(path)
        return if (file.exists()) file.toURI().toString() else null
    }

    @Suppress("NestedBlockDepth")
    private suspend fun performDownload(mediaId: String, audioUrl: String) {
        val outputFile = File(downloadsDir, "$mediaId.audio")
        val connection = URL(audioUrl).openConnection() as HttpURLConnection
        try {
            connection.connect()
            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L
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
                            downloadRepository.updateProgress(mediaId, progress)
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

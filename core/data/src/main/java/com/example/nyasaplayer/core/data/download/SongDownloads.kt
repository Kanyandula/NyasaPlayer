package com.example.nyasaplayer.core.data.download

/**
 * The download actions a screen can start.
 *
 * [SongDownloadManager] is the only implementation, and this interface exists for one reason: a
 * caller can depend on the four verbs without depending on a `Context`-bound singleton. The car's
 * content ViewModel is tested with plain JUnit fakes, and PRD §6.5 keeps platform types out of
 * those tests — injecting the manager itself would put them on Robolectric to build a `Context`.
 *
 * Reading download state is not here: that is [com.example.nyasaplayer.core.data.api.DownloadRepository],
 * which is already an interface and already faked.
 */
interface SongDownloads {
    fun downloadSong(mediaId: String)

    fun removeDownload(mediaId: String)

    fun removeAllDownloads()

    fun retryDownload(mediaId: String)
}

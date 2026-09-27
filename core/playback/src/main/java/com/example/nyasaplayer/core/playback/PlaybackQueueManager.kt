package com.example.nyasaplayer.core.playback

import androidx.media3.common.Player
import com.example.nyasaplayer.core.common.models.Song
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueManager @Inject constructor() {

    private val lock = Any()

    var queue: List<Song> = emptyList()
        private set

    var currentIndex: Int = -1
        set(value) = synchronized(lock) { field = value }

    private var originalQueue: List<Song> = emptyList()

    var isShuffled: Boolean = false
        private set

    fun setQueue(songs: List<Song>, startSong: Song): Song = synchronized(lock) {
        originalQueue = songs
        queue = songs
        isShuffled = false
        currentIndex = songs.indexOf(startSong).coerceAtLeast(0)
        queue[currentIndex]
    }

    fun restoreQueue(orderedSongs: List<Song>, index: Int): Unit = synchronized(lock) {
        originalQueue = orderedSongs
        queue = orderedSongs
        isShuffled = false
        currentIndex = index.coerceIn(0, orderedSongs.lastIndex)
    }

    fun setQueueShuffled(songs: List<Song>): Song? = synchronized(lock) {
        if (songs.isEmpty()) return null
        originalQueue = songs
        queue = songs.shuffled()
        isShuffled = true
        currentIndex = 0
        queue[currentIndex]
    }

    fun toggleShuffle(): Unit = synchronized(lock) {
        val current = queue.getOrNull(currentIndex) ?: return
        if (isShuffled) {
            queue = originalQueue
            currentIndex = queue.indexOf(current).coerceAtLeast(0)
            isShuffled = false
        } else {
            val remaining = queue.filterIndexed { i, _ -> i != currentIndex }.shuffled()
            queue = listOf(current) + remaining
            currentIndex = 0
            isShuffled = true
        }
    }

    /**
     * Adopts [player]'s playlist when it was set by something other than this manager — the media
     * template, Assistant or Bluetooth play through `onAddMediaItems`, which never touches the queue.
     * A playlist this manager already holds keeps its order and shuffle state.
     */
    fun syncWith(player: Player): Unit = synchronized(lock) {
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        if (items.map { it.mediaId } != queueSongIds()) {
            originalQueue = items.map { it.toSong() }
            queue = originalQueue
            isShuffled = false
        }
        currentIndex = if (queue.isEmpty()) -1 else player.currentMediaItemIndex
    }

    fun queueSongIds(): List<String> = synchronized(lock) {
        queue.map { it.mediaId }
    }
}

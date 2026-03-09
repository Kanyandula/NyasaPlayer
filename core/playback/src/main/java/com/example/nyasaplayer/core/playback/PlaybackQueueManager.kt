package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.Song
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueManager @Inject constructor() {

    private val lock = Any()

    var queue: List<Song> = emptyList()
        private set

    var currentIndex: Int = -1

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

    fun queueSongIds(): List<String> = synchronized(lock) {
        queue.map { it.mediaId }
    }
}

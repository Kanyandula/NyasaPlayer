package com.example.nyasaplayer.player

import com.example.nyasaplayer.core.common.models.Song
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueManager @Inject constructor() {

    var queue: List<Song> = emptyList()
        private set

    @Volatile
    var currentIndex: Int = -1

    private var originalQueue: List<Song> = emptyList()

    var isShuffled: Boolean = false
        private set

    fun setQueue(songs: List<Song>, startSong: Song): Song {
        originalQueue = songs
        queue = songs
        isShuffled = false
        currentIndex = songs.indexOf(startSong).coerceAtLeast(0)
        return queue[currentIndex]
    }

    fun restoreQueue(orderedSongs: List<Song>, index: Int) {
        originalQueue = orderedSongs
        queue = orderedSongs
        isShuffled = false
        currentIndex = index.coerceIn(0, orderedSongs.lastIndex)
    }

    fun setQueueShuffled(songs: List<Song>): Song? {
        if (songs.isEmpty()) return null
        originalQueue = songs
        queue = songs.shuffled()
        isShuffled = true
        currentIndex = 0
        return queue[currentIndex]
    }

    fun toggleShuffle() {
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

    fun skipNext(repeatMode: RepeatMode): Song? {
        if (currentIndex < queue.lastIndex) {
            currentIndex++
        } else if (repeatMode == RepeatMode.All && queue.size > 1) {
            currentIndex = 0
        } else {
            return null
        }
        return queue[currentIndex]
    }

    fun skipPrevious(): Song? {
        if (currentIndex > 0) {
            currentIndex--
            return queue[currentIndex]
        }
        return null
    }

    val hasPrevious: Boolean get() = currentIndex > 0

    fun hasNext(repeatMode: RepeatMode): Boolean {
        return currentIndex < queue.lastIndex ||
            (repeatMode == RepeatMode.All && queue.size > 1)
    }

    fun queueSongIds(): List<String> = queue.map { it.mediaId }
}

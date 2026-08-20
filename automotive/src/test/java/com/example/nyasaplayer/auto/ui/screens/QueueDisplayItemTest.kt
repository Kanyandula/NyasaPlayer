package com.example.nyasaplayer.auto.ui.screens

import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueDisplayItemTest {

    private fun queue(size: Int) = List(size) { Song(mediaId = "s$it", title = "Song $it") }

    @Test
    fun `parked returns every row with its original index`() {
        val items = queueDisplayItems(queue(30), currentIndex = 0, maxItems = 5, isDriving = false)

        assertEquals(30, items.size)
        assertEquals(List(30) { it }, items.map { it.queueIndex })
        assertEquals("s29", items.last().song.mediaId)
    }

    @Test
    fun `driving queue shorter than cap is returned whole`() {
        val items = queueDisplayItems(queue(3), currentIndex = 1, maxItems = 5, isDriving = true)

        assertEquals(listOf(0, 1, 2), items.map { it.queueIndex })
    }

    @Test
    fun `driving queue longer than cap is truncated to the first page`() {
        val items = queueDisplayItems(queue(30), currentIndex = 2, maxItems = 5, isDriving = true)

        assertEquals(listOf(0, 1, 2, 3, 4), items.map { it.queueIndex })
        assertEquals("s0", items.first().song.mediaId)
    }

    @Test
    fun `current item beyond the cap is included in the window`() {
        val items = queueDisplayItems(queue(30), currentIndex = 12, maxItems = 5, isDriving = true)

        assertEquals(listOf(12, 13, 14, 15, 16), items.map { it.queueIndex })
        assertEquals("s12", items.first().song.mediaId)
    }

    @Test
    fun `window near the end of the queue stays full and in range`() {
        val items = queueDisplayItems(queue(30), currentIndex = 29, maxItems = 5, isDriving = true)

        assertEquals(listOf(25, 26, 27, 28, 29), items.map { it.queueIndex })
    }

    @Test
    fun `invalid current index falls back to the first capped page`() {
        val negative = queueDisplayItems(queue(30), currentIndex = -1, maxItems = 5, isDriving = true)
        val tooLarge = queueDisplayItems(queue(30), currentIndex = 99, maxItems = 5, isDriving = true)

        assertEquals(listOf(0, 1, 2, 3, 4), negative.map { it.queueIndex })
        assertEquals(listOf(0, 1, 2, 3, 4), tooLarge.map { it.queueIndex })
    }

    @Test
    fun `zero or negative cap while driving renders nothing`() {
        assertTrue(queueDisplayItems(queue(30), currentIndex = 0, maxItems = 0, isDriving = true).isEmpty())
        assertTrue(queueDisplayItems(queue(30), currentIndex = 0, maxItems = -3, isDriving = true).isEmpty())
    }

    @Test
    fun `empty queue renders nothing in either state`() {
        assertTrue(queueDisplayItems(emptyList(), currentIndex = 0, maxItems = 5, isDriving = true).isEmpty())
        assertTrue(queueDisplayItems(emptyList(), currentIndex = 0, maxItems = 5, isDriving = false).isEmpty())
    }

    @Test
    fun `display rows map back to the songs at their queue index`() {
        val source = queue(30)
        val items = queueDisplayItems(source, currentIndex = 12, maxItems = 5, isDriving = true)

        items.forEach { assertEquals(source[it.queueIndex], it.song) }
    }

    @Test
    fun `clear is parked-only and needs more than one song`() {
        assertTrue(canClearQueue(queueSize = 2, isDriving = false))
        assertFalse(canClearQueue(queueSize = 1, isDriving = false))
        assertFalse(canClearQueue(queueSize = 0, isDriving = false))
        assertFalse(canClearQueue(queueSize = 30, isDriving = true))
    }
}

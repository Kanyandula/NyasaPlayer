package com.example.nyasaplayer.auto.ui.screens

import com.example.nyasaplayer.core.common.models.Song

/** A queue row to render, paired with its index in the real Media3 queue. */
internal data class QueueDisplayItem(
    val song: Song,
    val queueIndex: Int,
)

/**
 * Rows to render for [queue]. Parked shows everything; driving shows at most [maxItems] rows,
 * windowed so the current item stays visible. [QueueDisplayItem.queueIndex] is always the index
 * in [queue], so callbacks keep addressing the real queue.
 */
internal fun queueDisplayItems(
    queue: List<Song>,
    currentIndex: Int,
    maxItems: Int,
    isDriving: Boolean,
): List<QueueDisplayItem> {
    val items = queue.mapIndexed { index, song -> QueueDisplayItem(song, index) }
    if (!isDriving) return items
    val cap = maxItems.coerceAtLeast(0)
    if (cap == 0 || items.size <= cap) return items.take(cap)
    // Keep the first page unless the current item falls past it.
    val start = if (currentIndex in cap until items.size) minOf(currentIndex, items.size - cap) else 0
    return items.subList(start, start + cap)
}

/** Clearing the queue is a parked-only action, and pointless for a queue of one. */
internal fun canClearQueue(
    queueSize: Int,
    isDriving: Boolean,
): Boolean = !isDriving && queueSize > 1

package com.example.nyasaplayer.auto.ui.screens

import com.example.nyasaplayer.core.playback.QueueOrigin

internal data class SourceLabel(val heading: String, val name: String)

/**
 * The full player's "Playing from" label, or null when the queue has no collection to name — which
 * includes one whose name is blank, such as a detail screen tapped before its title loaded.
 */
internal fun QueueOrigin.sourceLabel(): SourceLabel? = when (this) {
    is QueueOrigin.Playlist -> SourceLabel("PLAYING FROM PLAYLIST", name)
    is QueueOrigin.Album -> SourceLabel("PLAYING FROM ALBUM", name)
    is QueueOrigin.Artist -> SourceLabel("PLAYING FROM ARTIST", name)
    is QueueOrigin.Genre -> SourceLabel("PLAYING FROM GENRE", name)
    is QueueOrigin.Search -> SourceLabel("PLAYING FROM SEARCH", "\u201C$query\u201D").takeIf { query.isNotBlank() }
    QueueOrigin.Favourites -> SourceLabel("PLAYING FROM", "Favourites")
    QueueOrigin.Downloads -> SourceLabel("PLAYING FROM", "Downloads")
    QueueOrigin.RecentlyPlayed -> SourceLabel("PLAYING FROM", "Recently Played")
    QueueOrigin.None -> null
}?.takeIf { it.name.isNotBlank() }

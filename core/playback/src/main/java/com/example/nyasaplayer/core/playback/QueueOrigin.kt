package com.example.nyasaplayer.core.playback

/**
 * Where the current queue came from, so the player can say "Playing from …" without guessing from
 * the current song. [None] means there is no collection to name — a single track, or a queue an
 * external controller (media template, Assistant, Bluetooth) set.
 */
sealed interface QueueOrigin {
    data class Playlist(val id: String, val name: String) : QueueOrigin
    data class Album(val id: String, val name: String) : QueueOrigin
    data class Artist(val id: String, val name: String) : QueueOrigin
    data class Genre(val name: String) : QueueOrigin
    data class Search(val query: String) : QueueOrigin
    data object Favourites : QueueOrigin
    data object Downloads : QueueOrigin
    data object RecentlyPlayed : QueueOrigin
    data object None : QueueOrigin
}

/**
 * The origin as three primitives — kind, id, name — so Bundles and Firestore never carry a class.
 * Kinds are fixed strings rather than class names, which R8 renames.
 */
fun QueueOrigin.toFields(): Triple<String, String, String> = when (this) {
    is QueueOrigin.Playlist -> Triple(KindPlaylist, id, name)
    is QueueOrigin.Album -> Triple(KindAlbum, id, name)
    is QueueOrigin.Artist -> Triple(KindArtist, id, name)
    is QueueOrigin.Genre -> Triple(KindGenre, "", name)
    is QueueOrigin.Search -> Triple(KindSearch, "", query)
    QueueOrigin.Favourites -> Triple(KindFavourites, "", "")
    QueueOrigin.Downloads -> Triple(KindDownloads, "", "")
    QueueOrigin.RecentlyPlayed -> Triple(KindRecentlyPlayed, "", "")
    QueueOrigin.None -> Triple("", "", "")
}

/**
 * Reverses [toFields]. Anything it cannot read back whole — an unknown or missing kind, or a blank
 * field the kind needs — is [QueueOrigin.None]: no label beats a wrong one, and states saved before
 * origins existed carry no kind at all.
 */
fun queueOriginOf(kind: String?, id: String?, name: String?): QueueOrigin {
    val i = id.orEmpty()
    val n = name.orEmpty()
    val named = n.isNotBlank()
    val identified = named && i.isNotBlank()
    return when (kind) {
        KindPlaylist -> QueueOrigin.Playlist(i, n).takeIf { identified }
        KindAlbum -> QueueOrigin.Album(i, n).takeIf { identified }
        KindArtist -> QueueOrigin.Artist(i, n).takeIf { identified }
        KindGenre -> QueueOrigin.Genre(n).takeIf { named }
        KindSearch -> QueueOrigin.Search(n).takeIf { named }
        KindFavourites -> QueueOrigin.Favourites
        KindDownloads -> QueueOrigin.Downloads
        KindRecentlyPlayed -> QueueOrigin.RecentlyPlayed
        else -> null
    } ?: QueueOrigin.None
}

private const val KindPlaylist = "playlist"
private const val KindAlbum = "album"
private const val KindArtist = "artist"
private const val KindGenre = "genre"
private const val KindSearch = "search"
private const val KindFavourites = "favourites"
private const val KindDownloads = "downloads"
private const val KindRecentlyPlayed = "recentlyPlayed"

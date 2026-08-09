package com.example.nyasaplayer.auto.ui.navigation

/**
 * A destination reached by drilling in from a tab root. All three sit at drill depth 1 (D8).
 *
 * Identifiers and display strings only, never domain objects — this is stored in
 * `rememberSaveable`. [Artist] carries `artistName` because the artist screen needs it and
 * resolving it from state would drop the destination during the gap after process death, before
 * Firestore's first emission (D16).
 *
 * `java.io.Serializable` rather than `@Parcelize`: the kotlin-parcelize plugin is not applied to
 * `:automotive`, and both `CarScreen` and `FavoriteArtist` already take this route.
 */
sealed interface CarDestination : java.io.Serializable {
    data class Artist(val artistId: String, val artistName: String) : CarDestination
    data class Album(val albumId: String) : CarDestination
    data class Playlist(val playlistId: String) : CarDestination
}

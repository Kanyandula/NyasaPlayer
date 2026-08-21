package com.example.nyasaplayer.auto.ui.navigation

/**
 * A destination reached by drilling in from a tab root. All of them sit at drill depth 1 (D8).
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

    /**
     * A catalogue artist, reached from a search result.
     *
     * Separate from [Artist], which is the driver's liked songs by that artist: it renders
     * hearts and drops rows live on unlike, which is not what an arbitrary catalogue artist is
     * (spec 3.4). No name is carried because the metadata read is Room-backed and answers
     * immediately, including after process death.
     */
    data class CatalogArtist(val artistId: String) : CarDestination
    data class Album(val albumId: String) : CarDestination
    data class Playlist(val playlistId: String) : CarDestination
}

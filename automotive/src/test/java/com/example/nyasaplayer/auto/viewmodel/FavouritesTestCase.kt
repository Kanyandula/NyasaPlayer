package com.example.nyasaplayer.auto.viewmodel

import com.example.nyasaplayer.auto.MainDispatcherRule
import com.example.nyasaplayer.auto.fake.FakeAlbumRepository
import com.example.nyasaplayer.auto.fake.FakeArtistRepository
import com.example.nyasaplayer.auto.fake.FakeAuthRepository
import com.example.nyasaplayer.auto.fake.FakeGenreRepository
import com.example.nyasaplayer.auto.fake.FakePlaylistRepository
import com.example.nyasaplayer.auto.fake.FakeSongRepository
import com.example.nyasaplayer.auto.fake.FakeUserRepository
import com.example.nyasaplayer.core.common.models.LikedSong
import com.example.nyasaplayer.core.common.models.Song
import org.junit.Rule

/**
 * The fixture the three Favourites suites share: the same six fakes wired into one ViewModel,
 * the two builders, and the one derivation of what screen 8 renders.
 *
 * One copy so the derivation cannot drift between suites — [rendered] in particular is a mirror
 * of a decision made in `AutomotiveApp`, and mirrors kept in triplicate are how a mutation
 * survives in one file while another still asserts the old shape.
 */
abstract class FavouritesTestCase {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    protected val songs = FakeSongRepository()
    protected val users = FakeUserRepository()
    protected val auth = FakeAuthRepository()
    protected val genres = FakeGenreRepository()

    protected fun viewModel() = AutomotiveContentViewModel(
        songRepository = songs,
        genreRepository = genres,
        albumRepository = FakeAlbumRepository(),
        artistRepository = FakeArtistRepository(),
        playlistRepository = FakePlaylistRepository(),
        userRepository = users,
        authRepository = auth,
    )

    protected fun song(id: String) = Song(mediaId = id, title = "Title $id", artistName = "Artist $id")

    protected fun likedSong(id: String) = LikedSong(mediaId = id, likedAt = 1L)

    /**
     * Exactly what screen 8 draws, mirroring AutomotiveApp.kt:465. Asserting on `likedSongs`
     * alone would prove nothing about the screen; asserting on `favourites` alone would skip
     * the case where no freeze was ever taken.
     */
    protected fun AutomotiveContentState.rendered(): List<String> =
        (favourites ?: likedSongs).map { it.mediaId }
}

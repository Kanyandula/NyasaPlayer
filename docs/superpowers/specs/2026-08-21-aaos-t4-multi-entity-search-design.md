# AAOS T4 - Multi-Entity Search Results

> **Status:** Draft for implementation · **Date:** 2026-08-21 · **Depends on:** A6 search,
> D33 result-scope decision, preferably T1 Compose test tooling
> **Ticket:** `docs/tickets/T4-automotive-multi-entity-search.md`

## 1. Context

A6 made the automotive search sheet real, but deliberately shipped song-only submitted results.
That kept the first search slice honest: `SongRepository.searchSongs()` and
`MediaBrowseTree.search()` already had a song contract, while albums, artists and playlists did
not have search APIs or valid custom-launcher result destinations.

T4 adds multi-entity result cards to the `oem` custom launcher without changing the A6 text-entry
contract. Search still runs on explicit submit, recent queries stay session-only, and system voice
search stays routed through `PlaybackService.onSearch` / `onGetSearchResult`.

## 2. Outcome

Submitted search results are typed and sectioned:

1. Featured result
2. Songs
3. Albums
4. Artists
5. Playlists

Every visible card either starts playback from the visible song results or opens a valid detail
destination at the current automotive navigation depth. No result is produced by filtering whatever
`AutomotiveContentViewModel` has already collected for the visible tabs.

## 3. Decisions

### 3.1 Data contract

T4 introduces an automotive-facing typed result model:

```kotlin
data class AutomotiveSearchResults(
    val query: String = "",
    val featured: AutomotiveSearchResult? = null,
    val songs: List<AutomotiveSearchResult.SongResult> = emptyList(),
    val albums: List<AutomotiveSearchResult.AlbumResult> = emptyList(),
    val artists: List<AutomotiveSearchResult.ArtistResult> = emptyList(),
    val playlists: List<AutomotiveSearchResult.PlaylistResult> = emptyList(),
)

sealed interface AutomotiveSearchResult {
    val stableId: String
    val title: String
    val subtitle: String
    val artworkUrl: String
    val rank: SearchRank

    data class SongResult(val song: Song, override val rank: SearchRank) : AutomotiveSearchResult
    data class AlbumResult(val album: Album, override val rank: SearchRank) : AutomotiveSearchResult
    data class ArtistResult(val artist: Artist, override val rank: SearchRank) : AutomotiveSearchResult
    data class PlaylistResult(val playlist: Playlist, override val rank: SearchRank) : AutomotiveSearchResult
}

data class SearchRank(
    val matchQuality: Int,
    val typePriority: Int,
    val popularity: Int,
    val sortTitle: String,
)
```

The exact Kotlin names may change during implementation, but the semantics should not:

- The result type is preserved all the way from search service to UI tap handling.
- `stableId` includes the type prefix, for example `song:<mediaId>` and `album:<id>`.
- `featured` is the best ranked visible result and is removed from its own section to avoid a
  duplicate card.
- Sections stay in fixed order so the screen does not reorder itself as different result types
  appear.
- Empty sections are omitted.

### 3.2 Ordering

Repositories return each type in deterministic match order. The coordinator then chooses the
featured card from the union.

Match quality:

1. Exact primary name/title match.
2. Primary name/title prefix match.
3. Primary name/title substring match.
4. Secondary-field match.

Secondary fields:

- Songs: artist name and album name.
- Albums: artist name.
- Artists: genre names, only if the stored list can be matched without brittle JSON string hacks.
- Playlists: none for T4; playlist search is by playlist name.

Tie-breakers:

1. Type priority: song, album, artist, playlist.
2. Popularity descending when the model has popularity.
3. Title/name ascending, case-insensitive.
4. Stable id ascending.

The type-priority tie-breaker does not mean songs always win. An exact album match outranks a song
whose album name merely contains the query.

### 3.3 API shape

T4 should use repository-level search primitives plus one automotive-facing coordinator.

Add repository APIs:

```kotlin
interface AlbumRepository {
    suspend fun searchAlbums(query: String, limit: Int): List<Album>
}

interface ArtistRepository {
    suspend fun searchArtists(query: String, limit: Int): List<Artist>
}

interface PlaylistRepository {
    suspend fun searchPlaylists(userId: String, query: String, limit: Int): List<Playlist>
}
```

Keep `SongRepository.searchSongs(query, limit)` as the song primitive.

Then add a small automotive search coordinator that injects `SongRepository`, `AlbumRepository`,
`ArtistRepository`, `PlaylistRepository` and `AuthRepository`, runs the four searches for a
committed query, ranks the typed results, and returns `AutomotiveSearchResults`.

This is preferred over a single new catalogue repository because the existing repositories already
own the source-specific details:

- Albums and artists are Room-backed and can search with DAO queries.
- Playlists are user-scoped Firestore data and need the signed-in user id.
- Songs already define the media-session search primitive.

It is also preferred over filtering `AutomotiveContentState` because the result should not depend
on whether a tab collector has emitted yet.

### 3.4 Artist navigation

T4 adds a real general artist detail destination for catalogue artists.

Do not reuse `CarArtistLikedSongsScreen` for arbitrary search results. That screen is scoped to
the driver's liked songs, renders favourite toggles, and intentionally removes rows live after an
unlike. It is not a valid representation of every catalogue artist.

The implementation should add a distinct artist destination, for example
`CarDestination.CatalogArtist(artistId: String)`, and load:

- artist metadata from `ArtistRepository.getArtistById(artistId)`
- tracks from `SongRepository.getSongsByArtist(artistId)`

The artist detail may reuse the visual structure of the album/playlist detail screen, but the
route and state must be distinct from the liked-songs artist route.

### 3.5 Media-session parity

`PlaybackService.onSearch` / `onGetSearchResult` remain song-result surfaces for T4.

That is a documented parity boundary, not an accidental divergence. Host-rendered Android Auto
search is a voice/media-session path where playable song results are the stable contract today.
The custom launcher can show non-playable album, artist and playlist cards because it owns the
tap handling and detail destinations.

The parity requirement for T4 is:

- The song section in the custom launcher and `MediaBrowseTree.search()` use the same
  `SongRepository.searchSongs()` semantics.
- Query trimming, limits and ordering for songs do not fork between the two paths.
- Non-song result cards are custom-launcher enrichment and must be documented as absent from the
  media-session result list.

If implementation changes song ranking, `MediaBrowseTreeTest` must be updated to prove the
media-session path follows the same song ordering.

### 3.6 Driving caps and tap behavior

The driving cap is cumulative across the visible result screen, not per raw result type.

When `UxRestrictionState.isDistractionOptimized` is false, the result screen may show each section
up to its repository limit. When it is true, flatten the visible result stream in rendered order:

```text
featured, songs, albums, artists, playlists
```

Apply `UxRestrictionState.cap()` to that flattened list, then re-section the surviving items for
rendering. This keeps `maxCumulativeContentItems` honest and prevents lower sections from silently
adding more cards than the platform allowed.

Tap behavior:

- Song result: play from the visible song-result queue only, including the featured card if it is
  a song. The tapped song is the start item.
- Album result: close search and open album detail at drill depth 1.
- Artist result: close search and open the new catalogue artist detail at drill depth 1.
- Playlist result: close search and open playlist detail at drill depth 1.

Detail screens continue to apply their own track-list caps. A result card tap must never use an
uncapped or stale backing list that contains items the driver could not see.

## 4. Scope

- Typed automotive search results for songs, albums, artists and playlists.
- Repository search APIs for albums, artists and playlists.
- A coordinator that returns one ranked, typed result set for a committed query.
- `AutomotiveSearchViewModel` updates to hold typed results while preserving A6 submit/retry/error
  behavior.
- A general catalogue artist detail route.
- Multi-entity result UI with top result and fixed-order sections.
- Unit and Compose tests for ranking, typing, caps and tap behavior.
- Documentation updates once cards ship.

## 5. Out of scope

- Reopening A6 text entry, IME, voice prompt or recent-query behavior.
- Custom voice capture or `RECORD_AUDIO`.
- Persistent search history.
- Full-text indexing, typo tolerance, stemming or remote search services.
- Mobile `:app` search redesign.
- Returning album, artist or playlist cards from `PlaybackService` in T4.
- Playlist artwork beyond the existing first-track fallback used by playlist detail.

## 6. Requirements

- **R1:** A committed query returns a typed result set that can contain songs, albums, artists and
  playlists at the same time.
- **R2:** The UI renders result sections in fixed order and preserves the result type for tap
  handling.
- **R3:** Album, artist and playlist results are produced by repository/search APIs, not by
  filtering currently collected UI state.
- **R4:** Artist result taps open a general catalogue artist detail destination, not the
  liked-songs artist screen.
- **R5:** Song result taps play from the visible song-result queue only.
- **R6:** Driving restrictions apply one cumulative cap to the rendered result stream.
- **R7:** Media-session search and launcher song search use the same song-search semantics.
- **R8:** Blank, failed, stale and retried searches keep the A6 behavior.

## 7. Acceptance criteria

- [ ] Given a query matches a song title and an album name, then the result model contains both a
      `SongResult` and an `AlbumResult` and the UI renders them in their intended sections.
- [ ] Given a query exactly matches an album name and only secondarily matches a song album field,
      then the album can become the featured result.
- [ ] Given a query matches an artist, then tapping the artist card opens a catalogue artist
      detail screen with that artist's songs.
- [ ] Given a playlist name matches a query, then the playlist result is produced without waiting
      for `AutomotiveContentViewModel.playlists` to emit.
- [ ] Given the vehicle is moving, then the total count of featured plus section cards does not
      exceed `maxCumulativeContentItems`.
- [ ] Given a visible song result is tapped while moving, then playback starts from the visible
      song-result queue, not from uncapped hidden results.
- [ ] Given `PlaybackService.onGetSearchResult` serves the same query, then its song ordering
      matches the launcher song section, while non-song cards are documented launcher-only
      enrichment.
- [ ] Given any repository search fails, then the search screen shows the existing recoverable
      search error instead of partial stale rows.

## 8. Verification targets

The implementation owner should run:

```bash
./gradlew :automotive:testOemDebugUnitTest :core:data:testDebugUnitTest
```

Expected focused coverage:

- DAO/repository search for album and artist primary and secondary fields.
- Playlist repository search that does not depend on an existing UI collector.
- Coordinator ranking, sectioning and featured-result de-duplication.
- `AutomotiveSearchViewModel` stale-result, retry and failure behavior after typed-result changes.
- UI rendering for multi-entity sections, preferably with the T1 Compose harness.
- Tap routing for song, album, artist and playlist cards.
- `MediaBrowseTree.search()` parity for song ordering.

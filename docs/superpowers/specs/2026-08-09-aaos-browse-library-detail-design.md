# AAOS Slice A3 — Browse, Library and the Detail Screens

> **Status:** Draft for review · **Date** 2026-08-09 · **Depends on:** A2 (merged, PR #16)
> **Design source:** `docs/aaos-DESIGN.md` §Layout, §Components, §Driving restrictions
> **Scope source:** `docs/AAOS_PRD.md` §9 (phase A3), `docs/AAOS_SCREEN_CONTRACT.md` screens 4, 7, 10, 11

## 1. Context

A2 settled the shell: `CarSystemBar`, `CarNavRail`, `CarMiniPlayer` and the ambient layer are
now identical on every screen, and `CarHomeScreen` was rebuilt inside it. The remaining rail
destinations were left alone — Browse and Library still carry the layout they had before the
chrome contract existed, and two of the four screens this slice owns do not exist at all.

A3 is the first slice where the user goes *somewhere*. Home, Browse, Library and Favourites
are all tab roots; Album and Playlist are the first destinations reached by drilling in from a
root, and the restriction layer has a depth cap written specifically for them. The one
drill-down that exists today — Library → artist — was built as a single nullable in
`AutomotiveApp`, which does not survive two more destinations.

### 1.1 What A3 is

1. **`CarBrowseScreen` rebuilt** against real genre data, with search removed.
2. **`CarLibraryScreen` rebuilt** as the contract's category rows.
3. **`CarAlbumScreen`** — new.
4. **`CarPlaylistScreen`** — new, with `PlaylistRepository` wired into the car for the first time.
5. **A drill-down model** that scales past one destination.

### 1.2 What A3 is not

- **Not search.** A6 owns `CarSearchScreen` and `CarSearchResultsScreen`. A3 *removes* the
  search field Browse carries today; see D10.
- **Not the designed Favourites.** A4 owns screens 8, 9 and 17. A3 does not touch
  `CarFavouriteMusicScreen` or `CarArtistLikedSongsScreen` beyond the call-site change §2.2
  forces.
- **Not downloads.** A8 owns screen 15. Library's Downloads row ships visibly disabled (D13).
- **Not settings or sign-out.** A7 owns screen 14. Sign-out stays where it is (D14).
- **Not a genre detail screen.** There is no such screen in the 20; see D9.

## 2. The drill-down problem

### 2.1 What exists today

`AutomotiveApp.kt` holds navigation in five `rememberSaveable` values, one of which is the
only drill-down:

```kotlin
var selectedArtist by rememberSaveable { mutableStateOf<FavoriteArtist?>(null) }
```

and `carUiLocation()` derives depth from it:

```kotlin
drillDepth = if (selectedArtist != null) 1 else 0,
```

Adding Album and Playlist the same way means three mutually-exclusive nullables that every
tab switch and every eviction has to reset in step. `AuthenticatedApp` already resets
`selectedArtist` in three separate places (the gate's `Denied` branch, `onSelectTab`, and
`onBackFromArtist`); tripling that is three chances to leave a stale destination behind and
render two screens' state at once.

### 2.2 `CarDestination`

New file, `automotive/.../auto/ui/navigation/CarDestination.kt`:

```kotlin
sealed interface CarDestination : java.io.Serializable {
    data class Artist(val artistId: String, val artistName: String) : CarDestination
    data class Album(val albumId: String) : CarDestination
    data class Playlist(val playlistId: String) : CarDestination
}
```

**Identifiers and display strings, never domain objects.** `AAOS_SCREEN_CONTRACT.md` §Compose
Rules forbids storing domain objects in `rememberSaveable` unless they are already safely
serializable. Album and Playlist carry an id alone because §3.2 loads their content anyway.
`Artist` also carries `artistName`, which is the only thing `CarArtistLikedSongsScreen` needs
beyond the track list — see D16 for why it is not resolved from state.

**`java.io.Serializable`, not `@Parcelize`.** The kotlin-parcelize plugin is not applied to
`:automotive` (verified: no `parcelize` in `automotive/build.gradle.kts`), and both
`CarScreen` and `FavoriteArtist` already take this route. Adding a Gradle plugin to store
three strings is not a trade this slice should make.

`AuthenticatedApp` replaces `selectedArtist` with:

```kotlin
var drillDown by rememberSaveable { mutableStateOf<CarDestination?>(null) }
```

`carUiLocation()` takes `drillDown: CarDestination?` in place of `selectedArtist` and derives
`drillDepth = if (drillDown != null) 1 else 0`. The gate's `Denied` branch, `onSelectTab` and
every back action clear one value.

`CarArtistLikedSongsScreen`'s signature does not change. Its call site passes
`destination.artistName` straight through and filters `likedSongs` by `destination.artistId`,
with **no lookup against `favoriteArtists` and no rule that clears `drillDown` when the artist
is missing**. Both would be process-death bugs: `rememberSaveable` restores `drillDown`
synchronously while `likedSongs` is still empty, so any "resolve or clear" rule would discard
the destination during the gap before Firestore's first emission. The list renders empty for
that moment and fills in, which is what the loading state is for. See D16.

### 2.3 Depth and the gate

`gate()` is not modified. It already reads:

```kotlin
location.drillDepth > state.maxContentDepth -> ReasonDepth
```

All three destinations sit at depth 1. Library's rows are carousels, not category screens, so
an album is one step from a tab root, not two (D8). While driving with `maxContentDepth == 0`,
entering any of the three is refused and an in-progress one is evicted to the tab root — the
existing `LaunchedEffect(playerState.restrictions, location)` handles both, unchanged.

## 3. Data layer

### 3.1 Playlists

`AutomotiveContentViewModel` injects `PlaylistRepository` (`:core:data`, verified interface):

```kotlin
fun getPlaylists(userId: String): Flow<List<Playlist>>
```

and gains `observePlaylists()`, modelled on the existing `observeLikedSongs()`: a `playlistsJob`
cancelled in `cancelContentJobs()`, started from `loadContent()`, and restarted by
`reloadUserContent()` on user switch. `AutomotiveContentState` gains
`playlists: List<Playlist> = emptyList()`.

`Playlist` (`core.common.models`, in `UserData.kt`) is `id`, `name`, `songIds`, `createdAt`,
`updatedAt` — **no cover image field**. Playlist artwork is the first resolved track's
`resolvedCoverUrl`, the same derivation `deriveFavoriteArtists()` already uses for artist
avatars, falling back to the existing placeholder when the playlist is empty.

Write operations are not wired. `createPlaylist`, `addSongToPlaylist`,
`removeSongFromPlaylist` and `deletePlaylist` exist on the interface and stay unused —
playlist editing in a vehicle is not in any phase of the PRD.

### 3.2 Detail loading

`AutomotiveContentState` gains `detail: CarDetailState?`:

```kotlin
data class CarDetailState(
    val destination: CarDestination,
    val title: String = "",
    val subtitle: String = "",
    val artworkUrl: String = "",
    val tracks: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
```

with `openDetail(destination: CarDestination)` and `closeDetail()` on the ViewModel, backed by
a `detailJob` cancelled on every call so a slow album load cannot land after the user has moved
on. Cancellation alone is not sufficient — a coroutine suspended in `getSongsByIds` resumes
and can reach its `_contentState.update` before the cancellation is observed — so the write is
guarded by re-checking that the destination it was launched for is still the current one, and
`CancellationException` is rethrown rather than swallowed, as the existing
`@Suppress("TooGenericExceptionCaught")` blocks in this ViewModel already do. **The guard goes
inside the atomic `_contentState.update { … }` block**, not in a pre-read of
`_contentState.value` — a check-then-write pair is a race in exactly the scenario it exists to
prevent. The shell drives it from one place:

```kotlin
LaunchedEffect(drillDown) {
    drillDown?.let(contentViewModel::openDetail) ?: contentViewModel.closeDetail()
}
```

`openDetail` sets `detail = null` for `CarDestination.Artist` and returns. Artist tracks are a
filter over `likedSongs`, which is a live Firestore-backed flow; snapshotting it into
`CarDetailState` would freeze the screen against unlikes performed on it. One entry point with
one documented early return costs less than a second call site that exists to skip a branch.

This replaces the two suspend one-shots the shell calls today. `getSongsByGenre(genreId)` stays,
because Browse plays a genre without opening anything (D9). `getSongsByAlbum(albumId)` is
**deleted, not reused**: it fetches the `Album` and then discards it, and the hero needs the
album's name, artist and artwork. `openDetail` calls `albumRepository.getAlbumById(albumId)`
itself and maps the result into `CarDetailState` in one pass.

#### How each destination resolves

`LaunchedEffect(drillDown)` fires **once per destination** and never re-runs when data lands
later. Anything `openDetail` reads must therefore be available on the first call, including the
call that follows process death — the same restore gap D16 is written for. The two loaded
destinations resolve differently and neither reads `contentState`:

| Destination | Header + track ids from | Available at restore? |
|---|---|---|
| `Album` | `albumRepository.getAlbumById(albumId)` — a `suspend` one-shot | Yes. A direct repository read, independent of `observeAlbums()` |
| `Playlist` | `playlistRepository.getPlaylists(userId).first()` | Yes. Suspends until Firestore's first emission, then resolves |

`PlaylistRepository` has **no `getPlaylistById`** — `getPlaylists(userId): Flow<List<Playlist>>`
is the whole read surface. Resolving the playlist out of `contentState.playlists` would be the
bug D16 describes: `rememberSaveable` restores `drillDown` synchronously while `observePlaylists()`
has not emitted, the one-shot `openDetail` finds nothing, and the screen stays empty forever
because the effect is keyed on `drillDown` alone. Taking `first()` from the repository flow
instead makes `openDetail` wait for the emission rather than race it, and costs one momentary
snapshot listener. It also needs no new method on a `:core:data` interface shared with `:app`.

```kotlin
val userId = authRepository.currentUserId ?: return      // same guard as observeLikedSongs()
val playlist = playlistRepository.getPlaylists(userId).first()
    .firstOrNull { it.id == destination.playlistId }
```

`currentUserId` is a `String?` added to `AuthRepository` alongside the existing
`currentUser: FirebaseUser?`. It exists because `FirebaseUser` is an abstract SDK class with no
constructible form, and no mocking library is on the catalog — so without it no unit test can
produce a signed-in user, and §7.1 could not be written at all.

A null playlist here sets `errorMessage` with `isLoading = false`.

**What this costs offline, stated plainly.** An earlier draft of this section claimed `first()`
"terminates in every case, so no path leaves a permanent spinner". That is true about hanging and
wrong about what the user sees. Firestore's `addSnapshotListener` serves an initial event from
cache almost always — including an **empty** snapshot when nothing is cached. So the realistic bad
path is not a spinner, it is a *wrong error*: cold start, offline, empty cache → `first()` returns
`[]` → the playlist is reported missing → **"This playlist is no longer available"** for a playlist
that exists. Because `LaunchedEffect(drillDown)` never re-runs, that message sticks until the user
backs out and re-enters.

A3 ships this knowingly rather than adding a bounded wait. Distinguishing "not synced yet" from
"genuinely deleted" needs either a timeout constant this module has nowhere else, or a
`first { it.any { p -> p.id == playlistId } }` that reintroduces the hang for a genuinely deleted
id. Offline behaviour is revisited in A6 and A8; §7.1 case 8 pins the current behaviour so the
change is a deliberate one when it comes.

### 3.3 Ordering

Track order matters more here than anywhere else in the app — a shuffled album tracklist is an
obvious bug where a shuffled liked-songs list is not. It needs no work.

`OfflineSongRepository.getSongsByIds` already returns songs in request order
(`core/data/.../offline/OfflineSongRepository.kt:21-28`): the DAO's `IN` query is unordered, so
the repository re-associates by `mediaId` and maps back over `ids`. `OfflineSongRepositoryTest`
locks that in. **A3 relies on the repository contract and adds no ordering code.**

Two observations, neither in scope:

- `AutomotiveContentViewModel` re-orders the result again in `loadRecentlyPlayed()` and
  `observeLikedSongs()`. Those lines are redundant, not load-bearing. Deleting them is a
  separate cleanup; A3 leaves them alone rather than touching working code it does not own.
- `FakeSongRepository` (`core/data/src/test/.../fake/`) does **not** preserve request order,
  so it diverges from every real implementation and from the behaviour tests assert. That is a
  `:core:data` test-fidelity bug worth its own fix, and it is why the fake would not have
  caught an ordering regression.

## 4. The screens

### 4.1 `CarBrowseScreen` (screen 4)

Contract: *filter chips, genre/mood/category cards, Play/open category.*

Ships as a grid of genre cards built from `contentState.genres`. Tapping one shuffle-plays that
genre and expands the full player, which is what the current screen does and what "Play
category" means in the absence of a genre screen (D9).

Deleted: `CarSearchBar`, `SearchResultItem`, `SearchEmptyState`, `FeaturedPlaylistsSection`,
`FeaturedPlaylistCard`, the `browseCategories` constant list, and the `searchQuery`,
`searchResults`, `onSearchQueryChange`, `onClearSearch`, `onSearchResultClick`,
`isSearchDisabled`, `albums`, `onAlbumClick`, `currentlyPlayingAlbumId` parameters.
`VerticalScrollbar` and `computeScrollbarInfo` are kept.

New signature:

```kotlin
@Composable
fun CarBrowseScreen(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
)
```

The `Genre` model has no field backing "mood" or "category", so **no filter chips ship**; see
D11. States: loading, empty (`CarEmptyState`), error with retry.

### 4.2 `CarLibraryScreen` (screen 7)

Contract: *category rows for Playlists, Albums, Artists, Favourites, Downloads; recently
played.*

Six horizontal rows, each a `LazyRow` of `CarContentCard` (§5), in contract order with
Recently played first:

| Row | Source | Tap |
|---|---|---|
| Recently played | `recentlyPlayed` | plays the song in the row's list |
| Playlists | `playlists` | `drillDown = Playlist(id)` |
| Albums | `albums` | `drillDown = Album(id)` |
| Artists | `favoriteArtists` | `drillDown = Artist(id)` |
| Favourites | `likedSongs` | selects the Favourites **tab** |
| Downloads | — | disabled |

Favourites is **one card showing the liked count**, not a list of liked songs. It is a shortcut
to a rail destination that already renders that list; A2's D2 established that two surfaces
rendering identical content is a visible bug, and this keeps it a shortcut rather than a copy.

Every *data* row with no content is omitted rather than rendering an empty carousel. The
Downloads row is **never** omitted — it carries no data by definition, and hiding it would
change Library's shape when A8 lands (D13).

States, per the contract's required set for screen 7:

```kotlin
@Composable
fun CarLibraryScreen(
    // … row data and callbacks …
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
)
```

Loading shows row-shaped placeholders rather than a full-screen spinner, so the six headings
stay put and the screen does not jump when data lands. Error shows the shared error treatment
with retry, wired to `AutomotiveContentViewModel.retryLoad()` — the same call Home uses, and
the reason `retryLoad()` exists separately from `reloadUserContent()`. Empty — loaded, no
error, every data row empty — shows `CarEmptyState` with a Browse action, with the disabled
Downloads row still visible beneath it.

Deleted: `LikedSongsHeader`, the liked-songs `LazyColumn`, `AlbumListItem`. Kept:
`LibraryHeader`, `SignOutConfirmationOverlay`, `SignOutModalCard`, `SignOutActions` (D14),
`ArtistAvatar`/`ArtistPlaceholder` folded into `CarContentCard`'s circular variant.

### 4.3 `CarAlbumScreen` (screen 11)

Contract: *album hero, Play, Download, track rows.*

Hero — artwork, album name, artist name, track count — then Play and Shuffle as 76dp CTAs,
then `CarTrackRow` list. Back returns to Library.

**No Download button, and this is a knowing contract deviation.** Screen 11 lists Download and
a download-progress state. A download subsystem exists, but the half that performs a download —
`SongDownloadManager` — is `@Singleton` in `:app`, which `:automotive` cannot depend on.
`DownloadRepository` is in `:core:data` and *is* reachable, but it only records download state
in Room; wiring it alone would produce a button that marks a track "downloading" forever.
Recorded as D12 with a blocker, per §Phase Acceptance Additions.

States: loading (hero skeleton, list placeholder), error with retry, empty (an album whose
`songIds` resolve to nothing).

### 4.4 `CarPlaylistScreen` (screen 10)

Contract: *playlist hero, Play, Shuffle, track rows, save/offline if supported.*

Structurally identical to §4.3 with the playlist's derived artwork and name. **Not supported**
resolves the conditional: `PlaylistRepository` has no offline or save concept, so neither
control ships.

Both detail screens read the same `CarDetailState`, so they are two thin composables over one
shared body rather than two independent screens.

## 5. `CarContentCard`

`AAOS_SCREEN_CONTRACT.md` §Shared Component Inventory requires one `CarContentCard` for
"album, playlist, mix, genre, recommendation" and prefers "one shared component with variants
over local copies". Today there are four private copies — `CategoryCard` and
`FeaturedPlaylistCard` in Browse, `AlbumListItem` and `ArtistAvatar` in Library — and A3 would
otherwise add two more.

New `auto/ui/components/CarContentCard.kt`, one composable with a shape variant:

```kotlin
enum class CarCardShape { Square, Circle }
```

Square for albums, playlists and genres; circle for artists. Required states per the inventory:
normal, focused, playing, unavailable. `CarTrackRow` and `CarEmptyState` are reused unchanged.

## 6. Two contract violations A3 removes

Both are in code A3 rewrites anyway, so neither costs extra work — but both need to be named,
because "the rewrite happened to fix it" is how a regression comes back.

**Hardcoded categories matched by name.** `browseCategories` is a static list of names and
gradients. `AutomotiveApp`'s `onCategoryClick` matches it against Firestore genres by string:

```kotlin
val genre = contentState.genres.firstOrNull {
    it.name.equals(categoryName, ignoreCase = true)
} ?: return@launch
```

A category whose name is not in Firestore is a card that does nothing when tapped, silently.
That is two violations at once: §Phase Acceptance Additions forbids static placeholder data in
production screens, and §CTA Rules forbids silent no-ops. Driving Browse from `genres` and
passing `Genre` instead of `String` removes the lookup and the failure with it.

**"Featured Playlists" renders albums.** `FeaturedPlaylistsSection(albums: List<Album>, …)`
sits under a hardcoded "Featured Playlists" heading. The section is deleted; real playlists
appear in Library's Playlists row.

## 7. Verification

### 7.1 Unit tests

`automotive/src/test/java/.../viewmodel/DetailLoadingTest.kt` — the detail load is the only
logic in A3 that can be silently wrong rather than visibly wrong. Cases:

1. `openDetail(Album)` populates `tracks` in the album's `songIds` order.
2. `openDetail(Playlist)` does the same for a playlist's `songIds`.
3. A second `openDetail` for a different destination while the first is in flight leaves
   `detail` holding the second destination's tracks, never the first's.
4. `closeDetail()` clears `detail`, and an in-flight load that resumes afterwards does not
   repopulate it.
5. `openDetail(Artist)` leaves `detail` null.
6. An album whose `songIds` resolve to nothing yields `isLoading = false` with empty tracks —
   the empty state — not a permanent spinner.
7. `openDetail(Playlist)` called **before the playlists flow has emitted** resolves once the
   emission arrives, rather than settling on empty. This is the restore path in §3.2; a fake
   whose flow emits on demand is what makes it testable.
8. `openDetail(Playlist)` for an id absent from an emission that *has* arrived yields
   `errorMessage` with `isLoading = false`, not a spinner.
9. `openDetail(Album)` where `getAlbumById` returns null does the same.

Cases 3 and 4 are the ones that justify the test; they are the guard described in §3.2, and a
regression there shows the wrong album's tracks under the right album's title. Case 7 is the
one the review found missing, and it is the only one of the nine that a passing implementation
can fail silently forever.

**Fakes.** `AutomotiveContentViewModel`'s constructor takes five repositories today and six
after §3.1, so `DetailLoadingTest` needs a fake for **all** of them in `automotive/src/test`:
`SongRepository`, `GenreRepository`, `AlbumRepository`, `UserRepository`, `AuthRepository` and
`PlaylistRepository`. `:core:data`'s fakes are not visible from `:automotive` — and
`FakeSongRepository` would be the wrong model to copy anyway, per §3.3. Only the song, album and
playlist fakes carry behaviour; the other three return empty flows and a fixed uid, but they
still have to exist or the ViewModel cannot be constructed.

**Test infrastructure.** This is `:automotive`'s **first ViewModel test** — `UxFlagsTest`,
`CarRestrictionGateTest` and `DecorativeMotionTest` are all pure-function tests over a module
that currently declares `testImplementation(libs.junit)` alone. So it needs both:

- `testImplementation(libs.kotlinx.coroutines.test)` in `automotive/build.gradle.kts`. The
  catalog already has the alias, so no version decision.
- A `MainDispatcherRule` in `automotive/src/test`, because `openDetail` runs in `viewModelScope`
  and there is no `Dispatchers.setMain` rule in this module to inherit. `init { loadContent() }`
  means every construction of the ViewModel touches the main dispatcher, so the rule is a
  prerequisite for the test existing at all, not a detail of one case.

`CarRestrictionGateTest` already covers depth-1 refusal and eviction to `tabRoot()`. A3 adds no
gate cases, because it adds no gate rules.

### 7.2 Manual checklist

On the emulator, per `docs/AAOS_DRIVING_STATE_TESTING.md`:

1. Parked: Browse renders real genres; every card plays something.
2. Parked: Library renders six rows; Downloads is visibly disabled; Favourites card opens the
   Favourites tab.
3. Parked: album and playlist detail open, play, shuffle and return.
4. Driving **with `maxContentDepth == 0`**: entering album or playlist detail is refused with
   `ReasonDepth`. The rule is `drillDepth > maxContentDepth`, so depth 1 is *allowed* at any cap
   of 1 or more — and the userdebug AVD reports 3. Refusal must therefore be checked against an
   injected cap of 0, not by driving the emulator and expecting a denial. `CarRestrictionGateTest`
   already asserts depth 1 is permitted at cap 1; that is correct behaviour, not a gap.
5. Drive transition *while inside* a detail screen evicts to Library with the dialog shown.
6. Driving: track lists truncate at `maxCumulativeContentItems`.
7. Sign out and back in: playlists reload for the new user, and do not leak across the switch.
8. **Process death inside a detail screen** — background the app, `adb shell am kill`, resume.
   Run it three times, once per destination:
   - *Artist*: comes back on the artist, not on Library. The check D16 exists for.
   - *Playlist*: comes back on the playlist **with its tracks**, not empty and not errored. The
     check D17 exists for, and the one the unit tests model but cannot prove against real
     Firestore latency.
   - *Album*: same, via `getAlbumById`.
9. Library with no data loaded yet shows placeholders, not an empty screen; forced offline
   shows the error state with a working retry.
10. Screenshot all four screens parked and driving.

### 7.3 Gates

- `oem` and `playstore` both build, test and lint green (NFR-7).
- Detekt `maxIssues: 0`.
- Touch targets: cards and CTAs at least 76dp on the smallest side.
- Contrast: no new colour pairs. Reuses tokens already measured in `aaos-DESIGN.md` §Contrast.

## 8. Decisions

Taken 2026-08-09. Recorded so implementation does not re-litigate them. Numbering continues
A2's D1–D7.

| # | Decision | Rationale |
|---|---|---|
| D8 | Library's categories are **horizontal carousels**, not tiles that open list screens. Album, playlist and artist detail therefore sit at **drill depth 1**. | The contract says "category rows". Rows put content one tap from a tab root, which is what a moving vehicle needs. Tiles would add four list screens the contract never specifies, and would push every detail screen to depth 2 — refused under any `maxContentDepth` a real head unit reports while driving, making the feature unreachable in the vehicle it was designed for. |
| D9 | **No genre detail screen.** Tapping a Browse card shuffle-plays the genre. | There is no `CarGenreScreen` among the 20. Playing on tap is the current behaviour and matches "Play/open category" in the contract's CTA column. Inventing a screen mid-slice would put an unspecced destination at depth 1 next to two specced ones. |
| D10 | Browse's search field is **deleted in A3**, not carried until A6. | It is off-contract — screen 4 lists no search, screens 5 and 6 own it. Rebuilding Browse around a field that A6 relocates means laying out the screen twice. Search is unreachable between A3 and A6; the system-bar search control already renders disabled from A2's D3, so the app is honest about it in the meantime rather than half-offering the feature. |
| D11 | **No Browse filter chips**, despite the contract listing them. Recorded as a data blocker in `aaos-DESIGN.md`. | `Genre` is `id`, `name`, `color`, `imageUrl`, `popularity`, `songIds` — nothing backs "mood" or "category". Any chip set would be invented taxonomy, and §Phase Acceptance Additions requires missing data be recorded as a blocker rather than filled with placeholders. The grid is unchanged by chips arriving later. |
| D12 | **No Download button on `CarAlbumScreen`**, despite the contract listing one, and no download-progress state. Recorded as a **module blocker** in `aaos-DESIGN.md`: *downloads are unreachable from `:automotive` until `SongDownloadManager` leaves `:app`.* | `DownloadRepository` (`:core:data`) is reachable but is Room bookkeeping only — `addDownload`, `updateProgress`, `markCompleted`. The code that actually fetches and writes the file is `SongDownloadManager`, `@Singleton` in `:app`, and `:automotive` does not and should not depend on `:app`. Wiring the repository alone ships a button that permanently claims a download is in progress, which is worse than no button. Extracting the manager into a shared module is a real piece of work touching seven `:app` files — `NyasaPlayerNavigation`, `PlayerViewModel`, `SongOverflowWithDownload`, `DownloadsViewModel`, `LibraryScreen`, `PlaylistDetailScreen`, `SearchScreen` — and it belongs to A8, which owns downloads and needs it anyway. |
| D13 | Library's **Downloads row renders visibly disabled** rather than being hidden. | Same reasoning as A2's D3: a disabled control is honest state, and hiding it would change Library's shape when A8 lands, which is the churn the chrome contract exists to prevent. |
| D14 | **Sign-out stays on `CarLibraryScreen`** with its confirmation overlay, marked for deletion in A7. | It belongs on screen 14. Removing it in A3 leaves no way to sign out of the vehicle at all, since the system bar's avatar is disabled until A7. Keeping ~50 lines for two slices beats shipping an app a user cannot sign out of. |
| D15 | `CarPlaylistScreen` wires **read-only** playlist access. The four `PlaylistRepository` write methods stay unused. | Creating and editing playlists is not in any PRD phase, and playlist mutation is a parked-only, keyboard-bound interaction the restriction layer would refuse anyway. |
| D16 | `CarDestination.Artist` carries **`artistName` alongside `artistId`**, and the artist screen never resolves against `favoriteArtists` nor clears itself when the artist is absent. | `rememberSaveable` restores `drillDown` synchronously after process death, while `likedSongs` — and therefore `favoriteArtists`, which is derived from it — is still empty pending Firestore's first emission. Any "resolve the artist or clear the destination" rule fires during that gap and drops the user back to Library on every restore. Today's code sidesteps this by saving the whole `FavoriteArtist`; carrying one display string preserves that property without storing a domain object. The track list is a live filter over `likedSongs` and is correctly empty until it loads. |
| D17 | `openDetail` resolves a playlist with `playlistRepository.getPlaylists(userId).first()`, **not** by looking it up in `contentState.playlists`, and no `getPlaylistById` is added to `PlaylistRepository`. | `LaunchedEffect(drillDown)` fires once and never re-runs when data arrives, so anything `openDetail` reads must be available on the first call — including the call after process death. `Album` already satisfies that through the one-shot `getAlbumById`; `PlaylistRepository` has no equivalent, and reading `contentState.playlists` would resolve against an empty list during exactly the gap D16 describes — leaving the screen permanently empty rather than briefly so, because nothing re-triggers the load. Taking `first()` from the flow suspends until the emission instead of racing it. Its cost is a wrong error offline rather than a hang — see §3.2, which states that plainly; A3 accepts it and A6/A8 revisit offline behaviour. Adding a read method to a `:core:data` interface shared with `:app`, to solve a problem the existing method already solves, is the larger change rather than the smaller one. |

## 9. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| `selectedArtist` → `drillDown` touches the gate, eviction, tab switching and the artist screen's call site | A restriction regression that only appears on a moving vehicle | Land the navigation change alone, before any screen work, and re-run the §7.2 driving checks on the artist screen — the one drill-down that already worked — before adding new destinations |
| `PlaylistRepository` is entering the car module for the first time | A user-switch leak: playlists from the previous account surviving sign-out | `observePlaylists()` follows `observeLikedSongs()` exactly, including `cancelContentJobs()` and the `reloadUserContent()` restart. Check 7 in the manual list exists for this |
| Browse and Library rebuilds are large deletions in 548- and 525-line files | Losing behaviour that was not written down, e.g. the scrollbar | Enumerate deletions per screen in the plan (§4.1, §4.2 list them) and screenshot before and after |
| Replacing a saved domain object with a saved id changes what survives process death | A drill-down that silently drops the user back to a tab root on every restore | D16 removes the resolution step that would cause it; §7.2 check 8 is the only way to see it |
| An empty Firestore `genres` collection makes Browse blank | Browse looks broken rather than empty | Explicit empty state with a Library action, not a bare `LazyColumn` |
| `openDetail` is one-shot but two of its three destinations need data that may not have arrived | A detail screen that is empty or errored forever, only after process death — invisible in normal use | D17 resolves both loaded destinations from a repository read rather than from `contentState`, so neither races the restore. §7.1 case 7 is the regression test and §7.2 check 8 extends to playlist detail |
| Playlist artwork derives from the first track | A playlist whose first track has no cover shows a placeholder while later tracks have art | Accepted. The alternative is scanning tracks for the first non-blank cover, which is more code for a case a real catalogue rarely hits |

## 10. Definition of done

1. `CarDestination` exists; `AutomotiveApp` holds exactly one drill-down value; `carUiLocation()`
   derives depth from it; the gate and eviction are unmodified.
2. `CarBrowseScreen` renders real genres, has no search, and no hardcoded category list.
3. `CarLibraryScreen` renders the six contract rows; Downloads is visibly disabled; Favourites
   is a shortcut card, not a second copy of the liked list.
4. `CarAlbumScreen` and `CarPlaylistScreen` exist, are reachable from Library, and play and
   shuffle their tracks.
5. `PlaylistRepository` is wired into `AutomotiveContentViewModel` with a job that is cancelled
   and restarted on user switch.
6. `CarLibraryScreen` and `CarBrowseScreen` each have loading, empty and error-with-retry
   states.
7. `CarContentCard` exists and replaces the four private card composables.
8. All three detail destinations are refused at depth while driving, and an in-progress one is
   evicted on the drive transition.
9. All three detail destinations survive process death (§7.2 check 8) — the artist screen keeps
   its artist, and album and playlist detail come back with their tracks.
10. Detail loading is tested, including the two out-of-order cases and the
    playlist-before-first-emission case in §7.1. `:automotive` has a `MainDispatcherRule` and
    `kotlinx.coroutines.test` on its test classpath.
11. D11, D12 and D14 recorded in `docs/aaos-DESIGN.md` beside A2's D6 and D7, with D12
    carrying the module blocker.
12. Both flavors green; Detekt zero; the §7.2 checklist executed and its outcome recorded.

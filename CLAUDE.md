# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew clean                  # Clean build artifacts

# Static analysis — run these yourself; see the hook caveat below
./gradlew detekt                 # Run Detekt — maxIssues: 0, any issue fails
./gradlew :app:lintDebug :core:common:lintDebug :core:data:lintDebug \
          :core:playback:lintDebug :automotive:lintOemDebug \
          :automotive:lintPlaystoreDebug                               # Android Lint, all modules

# Reports
open build/reports/detekt/detekt.html
open app/build/reports/lint-results-debug.html   # <module>/build/reports/lint-results-<variant>.html

# Baselines
./gradlew detektBaseline         # Regenerates detekt-baseline.xml — absorbs every current
                                 # finding; never run it to turn a red build green

# Git hooks — NOT ACTIVE in this checkout
./scripts/install-hooks.sh       # or: ./gradlew installGitHooks
```

**The pre-commit hook does not run here.** `core.hooksPath` is set globally to
`~/.claude/git-hooks`, so git ignores `.git/hooks/` — which is exactly where
`install-hooks.sh` writes. Treat Detekt and Lint as manual gates and run them in every
verification command; do not report "the hook passed".

Unit tests span all five modules; `:automotive` holds by far the largest suite. Run all with
`./gradlew test`, or the car's alone with `./gradlew :automotive:testOemDebugUnitTest`.

## Architecture

**MVVM + Repository pattern** with Hilt DI, multi-module Gradle project.

### Module structure

```
                                      +-- :app (mobile)
:core:common <-- :core:data <-- :core:playback --+
                                      +-- :automotive (AAOS)
```

- **`:core:common`** (`com.example.nyasaplayer.core.common`) — domain models, theme, UI components, utilities
- **`:core:data`** (`com.example.nyasaplayer.core.data`) — repository interfaces & implementations, Room DB, Firebase sync, DTOs, DI modules
- **`:core:playback`** (`com.example.nyasaplayer.core.playback`) — `PlaybackService` (MediaLibraryService), `PlaybackQueueManager`, `PlaybackStatePersistence`, `BasePlayerStateCollector`, `MediaBrowseTree`, shared playback state models
- **`:app`** — screens, ViewModels, navigation, `AppModule`
- **`:automotive`** (`com.example.nyasaplayer.auto`) — AAOS. Ships **both** a custom Compose launcher and, via `:core:playback`'s `PlaybackService`, the media source the OEM template browses. See "AAOS rendering" below.

```
Firestore / Firebase Auth
        ↓
   FirebaseSyncManager              — live Firestore → Room sync (:core:data)
        ↓
   Room Database (core/data/local/) — single source of truth for songs, artists, genres, albums
        ↓
   Offline Repositories             — read from Room DAOs, expose Flow/suspend (:core:data)
        ↓
   ViewModels (screens/*/)          — @HiltViewModel, StateFlow for UI state (:app)
        ↓
   Composable Screens               — collectAsState(), callbacks passed up (:app)
```

### Navigation

Two nested `NavHost` layers:
- **RootNavHost** (`navigation/RootNavigation.kt`): auth flow — Login/SignUp → MainApp; the system
  splash (`installSplashScreen()` in `MainActivity`) covers the start-destination decision
- **NyasaPlayerNavHost** (`navigation/NyasaPlayerNavigation.kt`): bottom tabs — Home, Search, Library, Profile

### Player subsystem (`:core:playback` + `:app`)

Shared in `:core:playback`:
- `PlaybackService` — `MediaLibraryService` (single playback owner for mobile + AAOS), foreground service
- `PlaybackQueueManager` — queue state, shuffle (keeps current song at index 0), repeat modes (Off/All/One),
  and the queue's `QueueOrigin` (what it plays from), published to controllers as session extras
- `PlaybackStatePersistence` — Firestore-backed save/restore of queue, position, repeat mode, queue origin
- `BasePlayerStateCollector` — shared `MediaController` event listener + position polling base class
- `PlayerTransport` / `ControllerConnection` — the one transport API both surfaces call, and the
  reference-counted, rebuildable controller behind it (`docs/aaos-DESIGN.md` D62–D65)
- `MediaBrowseTree` — browse tree for AAOS system media center + assistant search
- `SongMediaItemMapper` — `Song` ↔ `MediaItem` conversion
- `PlaybackCommands` — custom `SessionCommand` constants for IPC

Mobile-specific in `:app`:
- `PlayerViewModel` — wraps a `BasePlayerStateCollector` (250ms polling), exposes `PlayerUiState`
- `GlobalPlayerLayer` — hosts MiniPlayer + ExpandedPlayer overlay above bottom nav

AAOS rendering — two surfaces, both live:
- **OEM media template** — browse/search/playback driven by `PlaybackService`'s
  `MediaLibrarySession` callbacks (`onGetLibraryRoot` / `onGetChildren` / `onGetItem` /
  `onSearch` / `onGetSearchResult` / `onAddMediaItems`) and `MediaBrowseTree`.
  Discovery needs both service actions (`MediaLibraryService` + legacy
  `MediaBrowserService`) and `<meta-data android:name="androidx.car.app.launchable"
  android:value="true"/>` **on the service** — that last one is the opt-in the car
  media app checks; without it the service is skipped as a "non media template app".
  The `com.android.automotive` application descriptor does not affect this
  (verified on emulator: the GAS car media app never reads it).
- **Custom launcher app** — `AutomotiveActivity` → `AutomotiveApp` hosts 17 screen
  composables in `auto/ui/screens/` (the PRD counts a 20-screen design; see
  `docs/AAOS_SHIP_RECORD.md` for what shipped against it):
  - `CarAuthScreen` — gate shown until signed in
  - `CarHomeScreen` / `CarBrowseScreen` / `CarLibraryScreen` / `CarFavouriteMusicScreen` —
    the four `CarScreen` enum rail tabs
  - `CarAlbumScreen` / `CarPlaylistScreen` / `CarArtistScreen` (all in `CarDetailScreen.kt`),
    `CarArtistLikedSongsScreen`, `CarDownloadsScreen` — drill-downs via `CarDestination`
  - `CarSearchScreen` / `CarSearchResultsScreen` — search, gated by `NO_KEYBOARD`
  - `CarSettingsScreen` / `CarProfileSwitcherScreen` — parked-only sheets (`CarSheet`)
  - `CarFullPlayerScreen`, `CarQueueScreen`, `CarEmptyFavouritesScreen` — conditional
    overlays and states, not nav destinations
- Car-side ViewModels: `AutomotiveAuthViewModel`, `AutomotiveContentViewModel`,
  `AutomotiveSearchViewModel`, and `AutomotivePlayerViewModel` (wraps
  `BasePlayerStateCollector`). `CarUxRestrictionsHandler` (`@Singleton`) drives
  parked-vs-driving gating.
- Sources of truth for the car: `docs/AAOS_PRD.md` (scope; §3.3 is the custom-launcher decision),
  `docs/AAOS_SCREEN_CONTRACT.md` (screens and shared components), `docs/AAOS_COMPLIANCE.md`
  (gates and Play policy), `docs/aaos-DESIGN.md` (visual design and the numbered decisions, Dn).
  The superseded 2026-04-23 "template only" decision is `docs/AAOS_ARCHITECTURE.md` §6.
- `Dn`, `T24 Dn` and `Tn` in comments and docs, and where removed tickets and records went:
  `docs/CODING_GUIDELINES.md` → References in comments and docs.
- **A change under `:core:playback` is a change to both surfaces.** Verify mobile and car, or say
  which one was left out and why.

### Data layer

Offline-first for catalog data (songs, artists, genres, albums): `FirebaseSyncManager` syncs Firestore → Room; UI reads from Room via `Offline*Repository`. User-specific data (likes, recently played, profile, playback state) and home feed still read directly from Firebase.

| Repository | Backend | Key collections/paths |
|---|---|---|
| `AuthRepository` | Firebase Auth | — |
| `OfflineSongRepository` | Room (`SongDao`) | synced from Firestore `songs` |
| `OfflineGenreRepository` | Room (`GenreDao`) | synced from Firestore `genres` |
| `OfflineArtistRepository` | Room (`ArtistDao`) | synced from Firestore `artists` |
| `OfflineAlbumRepository` | Room (`AlbumDao`) | synced from Firestore `albums` |
| `UserRepository` | Firestore | `users/{uid}` (the profile document), `users/{uid}/likedSongs`, `users/{uid}/recentlyPlayed`, `users/{uid}/playbackState/current` |
| `HomeFeedRepository` | Firestore | `homeFeed/default` (home feed sections) |
| `PlaylistRepository` | Firestore | `users/{uid}/playlists` |

Room entities live in `core/data/.../local/entity/`, DAOs in `core/data/.../local/dao/`, and the database class in `core/data/.../local/NyasaDatabase.kt`. `FirebaseSyncManager` (`core/data/.../sync/`) keeps snapshot listeners on `songs`, `artists`, `genres` and `albums`, syncing each into Room with retry; the phone starts it in `Application.onCreate`, the car only once signed in (`CatalogSync`, bound in `SyncModule`).

### DI modules

- `AppModule` (`:app` `di/`) — provides FirebaseFirestore, FirebaseAuth, and ApplicationContext
  (ExoPlayer is built inside `PlaybackService`, not injected)
- `DatabaseModule` (`:core:data` `di/`) — provides Room `NyasaDatabase` and DAOs
- `RepositoryModule` (`:core:data` `di/`) — binds `Offline*Repository` implementations to repository interfaces
- `SyncModule` (`:core:data` `di/`) — binds `FirebaseSyncManager` as `CatalogSync`
- `PlaybackModule` (`:core:playback` `di/`) — provides the `SessionToken` that `ControllerConnection` uses
- `AutoAppModule` (`:automotive` `di/`) — provides Firebase, ApplicationContext and the `ArtworkThemeExtractor` for the automotive process

## Code Style & Static Analysis

- **Max line length**: 120 characters
- **Trailing commas**: required on call and declaration sites
- **No wildcard imports**
- **Composables**: PascalCase, must accept `modifier: Modifier = Modifier` if emitting UI
- **Detekt config**: `config/detekt/detekt.yml` with compose-rules plugin (`io.nlopez.compose.rules:detekt`);
  scans main sources only, not tests
- **Lint config**: `app/lint.xml`, applied to `:app` only — `ContentDescription` is error severity
  there; the other modules run Lint defaults

Common Detekt and Lint fixes, the import order and the test naming convention: `docs/CODING_GUIDELINES.md`.

## Design System

Dark theme only. Key colors: `NyasaBackground` (#0D0D0D), `NyasaPrimary` (#A855F7), `NyasaPrimaryDark` (#7C3AED). Five surface levels with increasing lightness. Gradient buttons via `Brush.horizontalGradient`. Custom `ImageVector` icons in `core/common/.../ui/icons/NyasaIcons.kt`.

### The car surface

Separate constraints, and the ones that drift are the ones nothing measures. Before changing any
screen under `automotive/src/main/java/.../ui/`:

- **Sizes come from `auto/ui/theme/AutomotiveDimens.kt`**, not from literals. `CarTouchTargetSize`
  is 76dp and is the floor for every interactive element.
- **The reference head unit is 1440x800dp** (the `Automotive_Distant_Display_with_Google_Play`
  AVD, 1080x600px at 120dpi), of which the app window gets **628dp** once the OS status and climate
  bars are taken. It is not the 1024x768 `AAOS_AOSP_33_userdebug` AVD used for driving state, and
  it is not the 1920x1080 CSS-px canvas `docs/aaos-DESIGN.md` is authored on — that document's
  §Units gives the conversion rule, and anything taken from it needs converting first.
- **Size from the window, not from a constant** — the full player's art and column are the pattern
  to copy (`docs/aaos-DESIGN.md` §Units).
- **Three measurement suites are the contract**, all fed by `CarUiCases.kt`'s cases and run by
  `./gradlew :automotive:testOemDebugUnitTest`: `CarTouchTargetMeasurementTest` (>=76dp),
  `CarTextSizeMeasurementTest` (>=14sp, >=18sp for a control's own label) and
  `CarTextContrastMeasurementTest` (>=7:1). A property with no suite behind it is a property that
  drifts — see T37.
- **Truncation is not one of them.** Robolectric's fallback font metrics make `hasVisualOverflow`
  flag nearly every text node, so an overflow suite was tried and removed (2026-09-28). A cut-off
  title passes all three suites; check text length on the emulator with a real-length case.
- **Grep `auto/ui/components/` before writing a component.** The inventory, built and not, is
  `docs/AAOS_SCREEN_CONTRACT.md` §Shared Component Inventory. Emulator traps that have produced
  false findings are listed in `docs/AAOS_DRIVING_STATE_TESTING.md`.

## Firebase Setup

Requires `app/google-services.json` and `automotive/google-services.json` (both gitignored), and
`GOOGLE_WEB_CLIENT_ID` in `local.properties`, which both apps read through `BuildConfig`. Steps are
in README → Setup.

## Crash Reporting

`CrashReporter` (`:core:data` `crash/`) wraps Firebase Crashlytics: one custom key, `surface`
(`car` / `mobile`), one non-fatal, no `setUserId`, no `log`, and collection off in every debug
variant. `docs/CRASH_REPORTING.md` inventories what is sent and records the decisions, including
why Crashlytics is pinned to 19.4.4 until the KTX migration. Anything that adds to what is sent
adds its row there in the same change.

## Known Gaps

- Tests: `:core:data` covers entities, converters, offline repos and sync backoff; `:automotive` holds the largest suite, including the `CarTouchTargetMeasurementTest` / `CarTextContrastMeasurementTest` / `CarTextSizeMeasurementTest` compliance measurements
- README "Not Yet Implemented" section tracks planned features (queue management, artist/album detail screens, etc.)

### Error handling that IS in place

- **`CoroutineExceptionHandler`** — 12 of the 14 `@HiltViewModel` classes have a `private val exceptionHandler` CEH as a safety net for uncaught exceptions in `viewModelScope.launch`; maps errors to the ViewModel's error state (existing try/catch and `.catch {}` remain as primary handling). `AutomotivePlayerViewModel` and `AutomotiveSearchViewModel` do not have one
- **`NetworkMonitor`** (`:core:common` `util/`) — singleton on `registerDefaultNetworkCallback` exposing `isOnline: StateFlow<Boolean>`: the default network has `INTERNET` and is not a `CAPTIVE_PORTAL`, decided from the callbacks' own arguments by `DefaultNetworkState` (D72); used by `PlayerViewModel`, `ProfileViewModel`, `SongDownloadManager` and the car's `AutomotivePlayerViewModel`
- **Offline banner** — persistent `OfflineBanner` composable shown at top of all screens when offline; driven by `PlayerUiState.isOffline` which observes `NetworkMonitor`
- **Fail-fast offline playback** — `PlayerViewModel` checks `isOnline` before streaming; shows error instead of infinite buffering spinner. The car does the same through the shared `Song.isPlayableNow` / `isStreamStalledOffline` rules in `AutomotivePlayerViewModel`.
- **`ErrorMessages.kt`** — `isNetworkError()` extension distinguishes `FirebaseNetworkException`/`UnknownHostException` from other errors
- **All main screens have error UI**: HomeScreen, LibraryScreen, SearchScreen show full-screen `NyasaErrorScreen` with retry; ProfileScreen shows `ErrorBanner` with retry while still displaying cached data; auth screens show inline error text
- **Player error UI**: `PlayerError` data class (title, message, isPlaybackError) routes errors — playback errors show `ErrorBanner` in `ExpandedPlayer`; non-playback errors (sync, restore) always show via `Snackbar` in `NyasaPlayerApp`; `MiniPlayer` progress bar turns red on any error; `toggleLike()` shows a Snackbar on failure alongside optimistic rollback; `restorePlaybackState()` shows a Snackbar on failure
- **Intentionally silent failures**: profile creation during auth, recently-played logging, and like-state observation fail silently (non-critical, Firestore offline cache retries profile creation)
- **Repository error handling**: `FirebaseUserRepository.getPlaybackState` catches and returns null; the Room-backed reads (`getSongsByIds`, `getArtistById`, …) do not catch; write operations throw and are caught by their callers (PlayerViewModel, PlaybackStatePersistence, AuthViewModel, SignUpViewModel all have try/catch)

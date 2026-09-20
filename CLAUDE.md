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
          :core:playback:lintDebug :automotive:lintOemDebug            # Android Lint, all modules

# Reports
open build/reports/detekt/detekt.html
open app/build/reports/lint-results-debug.html

# Baselines
./gradlew detektBaseline         # Regenerate detekt-baseline.xml

# Git hooks — NOT ACTIVE in this checkout
./scripts/install-hooks.sh       # or: ./gradlew installGitHooks
```

**The pre-commit hook does not run here.** `core.hooksPath` is set globally to
`~/.claude/git-hooks`, so git ignores `.git/hooks/` — which is exactly where
`install-hooks.sh` writes. Treat Detekt and Lint as manual gates and run them in every
verification command; do not report "the hook passed".

Unit tests span five modules — `:automotive` (43 files) is the largest, then `:core:data` (21), `:core:playback` (12), `:app` (2), `:core:common` (2). Run all with `./gradlew test`.

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
- **`:automotive`** (`com.example.nyasaplayer.auto`) — AAOS. Ships **both** a launcher app with 17 custom Compose screens (`ui/screens/`) and, via `:core:playback`'s `PlaybackService`, the media source the OEM template browses. See "AAOS rendering" below.

```
Firestore / Realtime DB / Firebase Auth
        ↓
   FirebaseSyncManager              — syncs Firestore → Room on app start (:core:data)
        ↓
   Room Database (core/data/local/) — single source of truth for songs, artists, genres
        ↓
   Offline Repositories             — read from Room DAOs, expose Flow/suspend (:core:data)
        ↓
   ViewModels (screens/*/)          — @HiltViewModel, StateFlow for UI state (:app)
        ↓
   Composable Screens               — collectAsState(), callbacks passed up (:app)
```

### Navigation

Two nested `NavHost` layers:
- **RootNavHost** (`navigation/RootNavigation.kt`): auth flow — Splash → Login/SignUp → MainApp
- **NyasaPlayerNavHost** (`navigation/NyasaPlayerNavigation.kt`): bottom tabs — Home, Search, Library, Profile

### Player subsystem (`:core:playback` + `:app`)

Shared in `:core:playback`:
- `PlaybackService` — `MediaLibraryService` (single playback owner for mobile + AAOS), foreground service
- `PlaybackQueueManager` — queue state, shuffle (keeps current song at index 0), repeat modes (Off/All/One)
- `PlaybackStatePersistence` — Firestore-backed save/restore of queue, position, repeat mode
- `BasePlayerStateCollector` — shared `MediaController` event listener + position polling base class
- `MediaBrowseTree` — browse tree for AAOS system media center + assistant search
- `SongMediaItemMapper` — `Song` ↔ `MediaItem` conversion
- `PlaybackCommands` — custom `SessionCommand` constants for IPC

Mobile-specific in `:app`:
- `PlayerViewModel` — extends `BasePlayerStateCollector` (250ms polling), exposes `PlayerUiState`
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
- `docs/AAOS_UI_REDESIGN_PLAN.md` holds the superseded 2026-04-23 "template only" decision.
  It carries a SUPERSEDED banner and is kept for its §1.1 two-surface inventory and §2 Play
  policy reasoning. Current sources of truth: `docs/AAOS_PRD.md` §3.3,
  `docs/AAOS_SCREEN_CONTRACT.md`, `docs/AAOS_COMPLIANCE.md`.

### Data layer

Offline-first for catalog data (songs, artists, genres): `FirebaseSyncManager` syncs Firestore → Room on app start; UI reads from Room via `Offline*Repository`. User-specific data (likes, recently played, profile) and home feed still read directly from Firebase.

| Repository | Backend | Key collections/paths |
|---|---|---|
| `AuthRepository` | Firebase Auth | — |
| `OfflineSongRepository` | Room (`SongDao`) | synced from Firestore `songs` |
| `OfflineGenreRepository` | Room (`GenreDao`) | synced from Firestore `genres` |
| `OfflineArtistRepository` | Room (`ArtistDao`) | synced from Firestore `artists` |
| `OfflineAlbumRepository` | Room (`AlbumDao`) | synced from Firestore `albums` |
| `UserRepository` | Firestore | `users/{uid}/likedSongs`, `users/{uid}/recentlyPlayed`, `users/{uid}/profile` |
| `HomeFeedRepository` | Realtime Database | home feed sections |
| `PlaylistRepository` | Firestore | `users/{uid}/playlists` |

Room entities live in `core/data/.../local/entity/`, DAOs in `core/data/.../local/dao/`, and the database class in `core/data/.../local/NyasaDatabase.kt`. `FirebaseSyncManager` (`core/data/.../sync/`) handles one-shot Firestore → Room sync on startup.

### DI modules

- `AppModule` (`:app` `di/`) — provides FirebaseFirestore, FirebaseAuth, and ApplicationContext
  (ExoPlayer is built inside `PlaybackService`, not injected)
- `DatabaseModule` (`:core:data` `di/`) — provides Room `NyasaDatabase` and DAOs
- `RepositoryModule` (`:core:data` `di/`) — binds `Offline*Repository` implementations to repository interfaces
- `PlaybackModule` (`:core:playback` `di/`) — provides the `SessionToken` that `ControllerConnection` uses
- `AutoAppModule` (`:automotive` `di/`) — provides Firebase and ApplicationContext for the automotive process

## Code Style & Static Analysis

- **Max line length**: 120 characters
- **Trailing commas**: required on call and declaration sites
- **No wildcard imports**
- **Composables**: PascalCase, must accept `modifier: Modifier = Modifier` if emitting UI
- **Detekt config**: `config/detekt/detekt.yml` with compose-rules plugin (`io.nlopez.compose.rules:detekt`)
- **Lint config**: `app/lint.xml` — `ContentDescription` is error severity (accessibility)

Common Detekt fixes: `MagicNumber` → extract to `const val`; `ModifierMissing` → add `modifier` param; `RememberMissing` → wrap in `remember {}`; `ViewModelInjection` → use `hiltViewModel()` at top-level composable only.

## Design System

Dark theme only. Key colors: `NyasaBackground` (#0D0D0D), `NyasaPrimary` (#A855F7), `NyasaPrimaryDark` (#7C3AED). Five surface levels with increasing lightness. Gradient buttons via `Brush.horizontalGradient`. Custom `ImageVector` icons in `core/common/.../ui/icons/NyasaIcons.kt`.

## Firebase Setup

Requires `app/google-services.json`. Firebase console must have:
- Auth: Email/Password + Google provider (Web Client ID configured in `LoginScreen.kt`)
- Firestore collections: `songs`, `genres`, `artists`, `albums`
- Realtime Database for home feed sections

## Crash Reporting

`CrashReporter` (`:core:data` `crash/`) wraps Firebase Crashlytics. It sets one custom key,
`surface` (`mobile` / `automotive`), and reports non-fatals; it never calls `setUserId` or
`log`. Collection is off in every debug variant, via
`core/data/src/debug/AndroidManifest.xml`. What the SDK sends, and what it never sends, is
inventoried in `docs/CRASH_REPORTING.md`. Crashlytics is pinned to 19.4.4 pending a KTX
migration.

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
- **Repository error handling**: Read suspend functions (`getSongsByIds`, `getArtistById`, `getPlaybackState`) catch exceptions and return safe defaults; write operations throw and are caught by their callers (PlayerViewModel, PlaybackStatePersistence, AuthViewModel, SignUpViewModel all have try/catch)

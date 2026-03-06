# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew clean                  # Clean build artifacts

# Static analysis (both enforced by pre-commit hook)
./gradlew detekt                 # Run Detekt — maxIssues: 0, any issue fails
./gradlew :app:lintDebug :core:common:lintDebug :core:data:lintDebug  # Run Android Lint

# Reports
open build/reports/detekt/detekt.html
open app/build/reports/lint-results-debug.html

# Baselines
./gradlew detektBaseline         # Regenerate detekt-baseline.xml

# Git hooks (runs Detekt + Lint before each commit)
./scripts/install-hooks.sh       # or: ./gradlew installGitHooks
```

Unit tests live in `core/data/src/test/`. Run with `./gradlew test`.

## Architecture

**MVVM + Repository pattern** with Hilt DI, multi-module Gradle project.

### Module structure

```
:core:common  ←──  :core:data  ←──  :app
(models,          (repos, Room,    (screens, player,
 theme, utils)     Firebase, sync)  navigation, DI roots)
```

- **`:core:common`** (`com.example.nyasaplayer.core.common`) — domain models, theme, UI components, utilities
- **`:core:data`** (`com.example.nyasaplayer.core.data`) — repository interfaces & implementations, Room DB, Firebase sync, DTOs, DI modules
- **`:app`** — screens, ViewModels, player subsystem, navigation, `AppModule`/`PlayerModule`

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

### Player subsystem (`player/`)

- `PlayerManager` — wraps Media3 ExoPlayer, converts `Song` → `MediaItem`
- `PlaybackQueueManager` — queue state, shuffle (keeps current song at index 0), repeat modes (Off/All/One)
- `PlaybackService` — foreground service for background playback
- `PlaybackStatePersistence` — disk-based save/restore of queue, position, repeat mode
- `PlayerViewModel` — exposes `PlayerUiState` StateFlow, bridges UI actions to PlayerManager
- `GlobalPlayerLayer` — hosts MiniPlayer + ExpandedPlayer overlay above bottom nav

### Data layer

Offline-first for catalog data (songs, artists, genres): `FirebaseSyncManager` syncs Firestore → Room on app start; UI reads from Room via `Offline*Repository`. User-specific data (likes, recently played, profile) and home feed still read directly from Firebase.

| Repository | Backend | Key collections/paths |
|---|---|---|
| `AuthRepository` | Firebase Auth | — |
| `OfflineSongRepository` | Room (`SongDao`) | synced from Firestore `songs` |
| `OfflineGenreRepository` | Room (`GenreDao`) | synced from Firestore `genres` |
| `OfflineArtistRepository` | Room (`ArtistDao`) | synced from Firestore `artists` |
| `UserRepository` | Firestore | `users/{uid}/likedSongs`, `users/{uid}/recentlyPlayed`, `users/{uid}/profile` |
| `HomeFeedRepository` | Realtime Database | home feed sections |

Room entities live in `core/data/.../local/entity/`, DAOs in `core/data/.../local/dao/`, and the database class in `core/data/.../local/NyasaDatabase.kt`. `FirebaseSyncManager` (`core/data/.../sync/`) handles one-shot Firestore → Room sync on startup.

### DI modules

- `AppModule` (`:app` `di/`) — provides FirebaseFirestore, FirebaseDatabase, FirebaseAuth
- `DatabaseModule` (`:core:data` `di/`) — provides Room `NyasaDatabase` and DAOs
- `RepositoryModule` (`:core:data` `di/`) — binds `Offline*Repository` implementations to repository interfaces
- `PlayerModule` (`:app` `di/`) — provides ExoPlayer instance

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

## Known Gaps

- Unit tests live in `:core:data` (`core/data/src/test/`) covering entities, converters, offline repos, and sync backoff
- README "Not Yet Implemented" section tracks planned features (playlists, downloads, queue management, artist/album detail screens, etc.)

### Error handling that IS in place

- **`CoroutineExceptionHandler`** — all 7 ViewModels have a `private val exceptionHandler` CEH as a safety net for uncaught exceptions in `viewModelScope.launch`; maps errors to the ViewModel's error state (existing try/catch and `.catch {}` remain as primary handling)
- **`NetworkMonitor`** (`:core:common` `util/`) — singleton using `ConnectivityManager.registerDefaultNetworkCallback` exposing `isOnline: StateFlow<Boolean>`; used by `PlayerViewModel` (fail-fast offline playback, offline banner) and `ProfileViewModel`
- **Offline banner** — persistent `OfflineBanner` composable shown at top of all screens when offline; driven by `PlayerUiState.isOffline` which observes `NetworkMonitor`
- **Fail-fast offline playback** — `PlayerViewModel` checks `isOnline` before streaming; shows error instead of infinite buffering spinner
- **`ErrorMessages.kt`** — `isNetworkError()` extension distinguishes `FirebaseNetworkException`/`UnknownHostException` from other errors
- **All main screens have error UI**: ForYouScreen, LibraryScreen, SearchScreen show full-screen `NyasaErrorScreen` with retry; ProfileScreen shows `ErrorBanner` with retry while still displaying cached data; auth screens show inline error text
- **Player error UI**: `PlayerError` data class (title, message, isPlaybackError) routes errors — playback errors show `ErrorBanner` in `ExpandedPlayer`; non-playback errors (sync, restore) always show via `Snackbar` in `NyasaPlayerApp`; `MiniPlayer` progress bar turns red on any error; `toggleLike()` shows a Snackbar on failure alongside optimistic rollback; `restorePlaybackState()` shows a Snackbar on failure
- **Intentionally silent failures**: profile creation during auth, recently-played logging, and like-state observation fail silently (non-critical, Firestore offline cache retries profile creation)
- **Repository error handling**: Read suspend functions (`getSongsByIds`, `getArtistById`, `getPlaybackState`) catch exceptions and return safe defaults; write operations throw and are caught by their callers (PlayerViewModel, PlaybackStatePersistence, AuthViewModel, SignUpViewModel all have try/catch)

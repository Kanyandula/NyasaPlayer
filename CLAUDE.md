# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew clean                  # Clean build artifacts

# Static analysis (both enforced by pre-commit hook)
./gradlew detekt                 # Run Detekt — maxIssues: 0, any issue fails
./gradlew :app:lintDebug         # Run Android Lint

# Reports
open build/reports/detekt/detekt.html
open app/build/reports/lint-results-debug.html

# Baselines
./gradlew detektBaseline         # Regenerate detekt-baseline.xml

# Git hooks (runs Detekt + Lint before each commit)
./scripts/install-hooks.sh       # or: ./gradlew installGitHooks
```

There are no custom test suites yet — only example stubs exist under `app/src/test/` and `app/src/androidTest/`.

## Architecture

**MVVM + Repository pattern** with Hilt DI, single `app` module.

```
Firestore / Realtime DB / Firebase Auth
        ↓
   Repositories (data/)        — singleton @Provides, suspend funs + callbackFlow
        ↓
   ViewModels (screens/*/)     — @HiltViewModel, StateFlow for UI state
        ↓
   Composable Screens          — collectAsState(), callbacks passed up
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

All data comes from Firebase. Repositories use `callbackFlow` with `addSnapshotListener` for reactive updates and `.await()` for one-shot reads.

| Repository | Backend | Key collections/paths |
|---|---|---|
| `AuthRepository` | Firebase Auth | — |
| `SongRepository` | Firestore | `songs` |
| `GenreRepository` | Firestore | `genres` |
| `ArtistRepository` | Firestore | `artists` |
| `UserRepository` | Firestore | `users/{uid}/likedSongs`, `users/{uid}/recentlyPlayed`, `users/{uid}/profile` |
| `HomeFeedRepository` | Realtime Database | home feed sections |

### DI modules (`di/`)

- `AppModule` — provides FirebaseFirestore, FirebaseDatabase, FirebaseAuth, all repositories
- `PlayerModule` — provides ExoPlayer instance

## Code Style & Static Analysis

- **Max line length**: 120 characters
- **Trailing commas**: required on call and declaration sites
- **No wildcard imports**
- **Composables**: PascalCase, must accept `modifier: Modifier = Modifier` if emitting UI
- **Detekt config**: `config/detekt/detekt.yml` with compose-rules plugin (`io.nlopez.compose.rules:detekt`)
- **Lint config**: `app/lint.xml` — `ContentDescription` is error severity (accessibility)

Common Detekt fixes: `MagicNumber` → extract to `const val`; `ModifierMissing` → add `modifier` param; `RememberMissing` → wrap in `remember {}`; `ViewModelInjection` → use `hiltViewModel()` at top-level composable only.

## Design System

Dark theme only. Key colors: `NyasaBackground` (#0D0D0D), `NyasaPrimary` (#A855F7), `NyasaPrimaryDark` (#7C3AED). Five surface levels with increasing lightness. Gradient buttons via `Brush.horizontalGradient`. Custom `ImageVector` icons in `ui/icons/NyasaIcons.kt`.

## Firebase Setup

Requires `app/google-services.json`. Firebase console must have:
- Auth: Email/Password + Google provider (Web Client ID configured in `LoginScreen.kt`)
- Firestore collections: `songs`, `genres`, `artists`, `albums`
- Realtime Database for home feed sections

## Known Gaps

- **No `CoroutineExceptionHandler`** in ViewModels — all error handling relies on local try/catch and flow `.catch {}` operators
- **Player error display is basic** — `ExpandedPlayer` shows an `ErrorBanner` on playback errors and `MiniPlayer` tints its progress bar red; `restorePlaybackState()` failures are silently swallowed (best-effort)
- **Silent failures** — profile creation, recently-played logging, and like-state observation fail silently with no user feedback
- No Room/local database — all data from Firebase with implicit Firestore offline cache only
- Tests are stub-only — no real unit or integration tests yet
- README "Not Yet Implemented" section tracks planned features (playlists, downloads, queue management, artist/album detail screens, etc.)

### Error handling that IS in place

- **`NetworkMonitor`** (`util/`) — singleton using `ConnectivityManager` exposing `isOnline: StateFlow<Boolean>`; used by ForYou, Library, Profile, and Search ViewModels
- **`ErrorMessages.kt`** — `isNetworkError()` extension distinguishes `FirebaseNetworkException`/`UnknownHostException` from other errors
- **All main screens have error UI**: ForYouScreen, LibraryScreen, SearchScreen show full-screen `NyasaErrorScreen` with retry; ProfileScreen shows `ErrorBanner` with retry while still displaying cached data; auth screens show inline error text
- **Player error UI**: `ExpandedPlayer` shows `ErrorBanner` below toolbar with dismiss/retry; `MiniPlayer` progress bar turns red (`NyasaError`); when player is not expanded, errors show via `Snackbar` in `NyasaPlayerApp`; `PlayerViewModel.restorePlaybackState()` is wrapped in try/catch for best-effort recovery
- **Repository error handling**: Read suspend functions (`getSongsByIds`, `getArtistById`, `getPlaybackState`) catch exceptions and return safe defaults; write operations throw and are caught by their callers (PlayerViewModel, PlaybackStatePersistence, AuthViewModel, SignUpViewModel all have try/catch)

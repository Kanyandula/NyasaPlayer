# NyasaPlayer

A modern Android music streaming application built with Jetpack Compose, Firebase, and ExoPlayer (Media3).
## UI
https://preview--nyasa-harmony-suite.lovable.app/screens

## Tech Stack

| Technology | Purpose |
|-----------|---------|
| Jetpack Compose + Material3 | UI framework |
| Kotlin | Language |
| Hilt | Dependency injection |
| Firebase Firestore | Song/genre/artist/album data, playlists, user state |
| Firebase Realtime Database | Home feed sections |
| Firebase Auth | Email/password + Google Sign-In |
| Room | Local database (offline-first catalog) |
| ExoPlayer (Media3 1.5.1) | Audio playback |
| Coil | Image loading (with offline disk cache) |
| Compose Navigation | Screen navigation |
| Firebase Crashlytics | Crash and non-fatal reporting (see `docs/CRASH_REPORTING.md`) |

## Architecture

- **Pattern**: MVVM with Repository layer
- **DI**: Hilt (`@HiltViewModel`, `@AndroidEntryPoint`, `@Module`)
- **State**: `StateFlow` + `collectAsState()` for reactive UI
- **Navigation**: Nested `NavHost` — `RootNavHost` (auth flow) and `NyasaPlayerNavHost` (bottom tabs)

## Project Structure

Multi-module Gradle project:

```
:core:common  ←  :core:data  ←  :core:playback  →  :app (mobile)
                                                  →  :automotive (AAOS)
```

```
:core:common  (com.example.nyasaplayer.core.common)
├── models/                         # Song, Artist, Album, Genre, HomeFeed, UserData
├── ui/
│   ├── theme/                      # Color, Theme, Type
│   ├── icons/NyasaIcons.kt        # Custom ImageVector icons
│   └── components/                 # ErrorBanner, NyasaErrorScreen, OfflineBanner,
│                                   #   SongOverflowSheet, PlaylistPickerSheet, ...
└── util/                           # FormatDuration, Greeting, NetworkMonitor, DefaultNetworkState

:core:data  (com.example.nyasaplayer.core.data)
├── api/                            # Repository interfaces (SongRepository, AuthRepository, etc.)
├── dto/                            # Firestore DTOs
├── local/                          # Room database, DAOs, entities, migrations
├── offline/                        # Offline-first repository implementations
├── download/SongDownloadManager.kt # Download-for-offline, local URI resolution
├── crash/CrashReporter.kt          # Crashlytics wrapper (surface key, non-fatals)
├── sync/                           # FirebaseSyncManager, CatalogSync — Firestore -> Room
├── Firebase*Repository.kt          # Firebase implementations
└── di/                             # DatabaseModule, RepositoryModule, SyncModule

:core:playback  (com.example.nyasaplayer.core.playback)
├── PlaybackService.kt              # MediaLibraryService — single playback owner, mobile + AAOS
├── MediaBrowseTree.kt              # Browse tree for the OEM media template and Assistant search
├── SongMediaItemMapper.kt          # Song <-> MediaItem conversion
├── PlaybackCommands.kt             # Custom SessionCommand constants
├── PlaybackQueueManager.kt         # Queue state, shuffle, repeat modes
├── PlaybackStatePersistence.kt     # Firestore-backed save/restore of queue, position, repeat
├── BasePlayerStateCollector.kt     # Shared MediaController event/polling base class
├── ControllerConnection.kt         # MediaController lifecycle and reconnection
├── PlayerUiState.kt / PlaybackSnapshot.kt / PlayerTransport.kt
├── OfflinePlayback.kt              # Shared isPlayableNow / isStreamStalledOffline rules
└── di/PlaybackModule.kt            # Provides SessionToken and shared playback dependencies

:app  (com.example.nyasaplayer)
├── MainActivity.kt
├── di/AppModule.kt                 # Firestore, FirebaseAuth, ApplicationContext
├── navigation/                     # RootNavigation, NyasaPlayerNavigation, NyasaBottomNavBar
├── player/PlayerViewModel.kt       # Extends BasePlayerStateCollector (250ms polling)
├── screens/
│   ├── NyasaPlayerApp.kt          # Main app shell with bottom nav
│   ├── auth/                       # LoginScreen, SignUpScreen, AuthViewModel, SignUpViewModel
│   ├── home/ search/ library/ profile/
│   ├── downloads/                  # DownloadsScreen, DownloadsViewModel
│   ├── playlist/                   # PlaylistDetailScreen, PlaylistViewModel
│   └── player/                     # MiniPlayer, ExpandedPlayer, GlobalPlayerLayer
├── util/ErrorMessages.kt           # Firebase error classification
└── ui/preview/PreviewData.kt       # Preview/mock data

:automotive  (com.example.nyasaplayer.auto)
# The custom Compose launcher IS the AAOS product (oem flavor). The OEM media
# template remains live in parallel, rendered from :core:playback's PlaybackService.
├── AutomotiveActivity.kt           # oem launcher entry point, distractionOptimized=true
├── ui/
│   ├── AutomotiveApp.kt            # Nav root: auth gate → rail tabs → overlays and sheets
│   ├── screens/                    # 17 screens — Home, Browse, Library, Favourites,
│   │                               #   Search(+Results), FullPlayer, Queue, Downloads,
│   │                               #   Album/Playlist/Artist detail, Settings, ProfileSwitcher,
│   │                               #   ArtistLikedSongs, EmptyFavourites, Auth
│   ├── components/                 # CarMiniPlayer, CarNavRail, CarSystemBar, CarModal,
│   │                               #   CarTrackRow, CarSignOutConfirmation, ... (21 files)
│   ├── navigation/                 # CarScreen (4 rail tabs), CarDestination, CarUiLocation, gate()
│   └── theme/                      # AutomotiveColors, AutomotiveDimens
├── viewmodel/                      # AutomotiveAuthViewModel, AutomotiveContentViewModel,
│                                   #   AutomotivePlayerViewModel, AutomotiveSearchViewModel,
│                                   #   CarUxRestrictionsHandler
└── di/AutoAppModule.kt             # Firebase + ApplicationContext for the automotive process
```

Build variants: `:automotive` ships `oem` (custom launcher, the product) and `playstore`
(no launcher activity, host-rendered only — a preserved Play path, not currently submitted).
See `docs/AAOS_PRD.md` §3.3.

## Implemented Features

### Splash Screen
- System splash (AndroidX SplashScreen API) with dark background
- Routes to Login (unauthenticated) or MainApp (authenticated)

### Authentication (Firebase Auth)
- **Login Screen**: Email/password fields, "Forgot password?" link, gradient "Sign In" button, Google Sign-In via Credential Manager API, "OR" divider, navigation to Sign Up
- **Sign Up Screen**: Registration with email/password, navigation back to Login
- **AuthRepository**: `signInWithEmail()`, `signUpWithEmail()`, `signInWithCredential()`, `sendPasswordResetEmail()`, `signOut()`, `authStateFlow()`, `isAuthenticated`
- **Navigation**: Proper `popUpTo(inclusive = true)` prevents back-navigating to splash/login after auth

### Home Screen (HomeScreen)
- Header with profile avatar placeholder, greeting ("Welcome back"), notification bell icon
- Dynamic sections loaded from Firebase Realtime Database
- Three layout types: `horizontal_scroll` (LazyRow cards), `grid` (2x2 Quick Picks), `list` (song rows)
- Section headers with "See All" + chevron

### Search Screen
- Search bar with filter-as-you-type
- "Browse All" genre grid (colored cards from Firestore)
- Genre drill-down: tap a genre to see its songs
- Back navigation with BackHandler
- Search results display matching songs

### Library Screen
- "Liked Songs" header with heart icon and song count
- Sort/Filter chips
- "Shuffle Play" gradient button
- Song list with artwork, title, artist, duration, overflow menu

### Profile Screen
- Profile header with avatar placeholder, display name, email
- Menu items: Liked Songs, Audio Quality, Settings, About
- Sign Out functionality

### Music Player
- **MiniPlayer**: Collapsed bar at bottom showing current song, play/pause/skip controls, artwork; swipe-to-dismiss; progress bar (turns red on error)
- **ExpandedPlayer**: Full-screen with large artwork (animated scale on play/pause), song info, like button, play/pause/skip/shuffle/repeat controls, styled progress slider with time labels; drag-down-to-collapse gesture; error banner for playback errors
- **Playback engine**: ExoPlayer (Media3), built and owned by `PlaybackService` (`:core:playback`); UI talks to it through a `MediaController`, never directly
- **Queue management**: `PlaybackQueueManager` handles queue state, skip next/previous, shuffle (keeps current song at index 0), repeat modes (Off/All/One)
- **Like/unlike**: Optimistic UI toggle with Firestore persistence; real-time like-state observation via snapshot listener
- **Background playback**: `PlaybackService` foreground service keeps audio playing when app is backgrounded
- **State persistence**: `PlaybackStatePersistence` saves/restores queue, position and repeat mode through `UserRepository` (Firestore), so playback resumes across app restarts and across devices
- **Recently played**: Logged per song play, displayed in Home screen
- **Error handling**: `PlayerError` model routes playback errors to ErrorBanner in ExpandedPlayer and non-playback errors (sync, restore) to Snackbar; `CoroutineExceptionHandler` safety net in all ViewModels
- **Offline UX**: Persistent offline banner across all screens; fail-fast playback (shows error instead of infinite buffering); `NetworkMonitor` singleton detects connectivity changes in real-time

### Offline-First Data (Room)
- **Room database**: Songs, artists, genres cached locally via `NyasaDatabase`
- **Firebase sync**: `FirebaseSyncManager` syncs Firestore collections → Room on app startup
- **Offline repositories**: `OfflineSongRepository`, `OfflineArtistRepository`, `OfflineGenreRepository` read from Room DAOs
- **Image caching**: Coil configured with `respectCacheHeaders(false)` and 10% disk cache for offline artwork

### Downloads & Offline Playback
- **Download songs**: Tap overflow menu on any song to download for offline playback
- **Downloads screen**: Accessible from Library, shows all downloaded songs with storage summary
- **Offline playback**: Downloaded songs play from local file when offline; non-downloaded songs show error
- **Download management**: Remove individual downloads or all at once (with confirmation dialog)
- **Progress tracking**: Room `DownloadEntity` tracks status (Pending/Downloading/Completed/Failed)
- **Stale download recovery**: Pending/Downloading states reset to Failed on app restart

### Android Automotive OS (AAOS)

Two surfaces ship, both live. The custom launcher is the product; the media template is
kept first-class for Assistant and voice search. The 2026-04-23 "template only" decision
was reversed on 2026-08-02 — see `docs/AAOS_PRD.md` §3.3.

- **Custom launcher** (`:automotive`, `oem` flavor): 18 of 20 designed screens, in a
  champagne-gold identity — Home, Browse, Library, Favourites, Search, Full Player, Queue,
  Downloads, Album/Playlist/Artist detail, Settings, Profile Switcher, Auth. Screen 2
  (PIN opt-in, T18) and phone/email sign-in (T19) are deferred past ship.
- **OEM media template**: Declared `<uses name="media" />`; root → [Recently Played, Genres,
  Artists, All Songs, Liked Songs] browse tree from `MediaBrowseTree`, search via
  `onSearch` / `onGetSearchResult`, queue driving `Player.setMediaItems`. Discovery needs
  both service actions plus `androidx.car.app.launchable` metadata **on the service**.
- **Driving restrictions**: `CarUxRestrictionsHandler` reads `CarUxRestrictionsManager` —
  `NO_KEYBOARD`, `NO_SETUP`, content depth and item caps. Settings, profile switching, typed
  search, deep drill-down, queue mutation and download deletion are refused while driving;
  transport, seek, queue skip-to and tab switching stay available. Starting to drive inside
  a restricted screen **evicts** you to a permitted one with an explanation (FR-2.5).
- **Measured compliance**: 695 interactive nodes ≥ 76dp, 1103 text nodes ≥ 7:1 contrast,
  both by automated Compose measurement tests. Evidence: `docs/AAOS_SHIP_RECORD.md`.

### Design System
- Dark theme throughout: `NyasaBackground` (#0D0D0D), `NyasaPrimary` (#A855F7), `NyasaPrimaryDark` (#7C3AED)
- Surface hierarchy: Surface1-5 with increasing lightness
- Text hierarchy: White → TextSecondary (70% white) → TextTertiary (50% white)
- Custom `ImageVector` icons: MusicNote, Email, Lock, ChevronRight, Notification, Heart, MoreVert, Settings, Home, Search, Library, Profile, Play, Pause, SkipNext, SkipPrevious
- Gradient buttons using `Brush.horizontalGradient`
- Glow effects via `shadow()` with colored `ambientColor`/`spotColor`

### Bottom Navigation
- Four tabs: Home, Search, Library, Profile
- Custom icons, NyasaPrimary selected indicator

## Not Yet Implemented

### Screens & Navigation

| Feature | Status | Notes |
|---------|--------|-------|
| Settings screen | Stub | Profile menu item present (`ProfileScreen.kt`), no screen |
| Audio quality settings | Stub | Profile menu item present, no screen |
| About screen | Stub | Profile menu item present, no screen |
| Artist detail screen | Not started | No route or screen |
| Album detail screen | Not started | No route or screen |
| "See All" section expansion | Stub | Home headers have chevrons but no navigation callback |
| Queue management UI (mobile) | Not started | Queue managed internally; no phone screen to view/reorder. The car has `CarQueueScreen` |
| Playlist creation / management | Implemented | `PlaylistDetailScreen`, `PlaylistViewModel`, `FirebasePlaylistRepository`; create, add/remove songs, album art grid covers |
| User profile editing | Not started | No edit screen or update flow |
| Onboarding / genre selection | Not started | No onboarding flow after sign-up |

### Player Features

| Feature | Status | Notes |
|---------|--------|-------|
| Share song | Stub | Empty `onClick` in `ExpandedPlayer.kt` toolbar |
| Volume control | Stub | Empty `onClick` in `ExpandedPlayer.kt` bottom bar |
| Lyrics display | Stub | Visual placeholder only in `ExpandedPlayer.kt` |
| Queue viewer (in player) | Stub | Visual placeholder only in `ExpandedPlayer.kt` |

### Search & Library

| Feature | Status | Notes |
|---------|--------|-------|
| Search history | Not started | No persistence of past queries |
| Sort / Filter in Library | Stub | Chips displayed (`LibraryScreen.kt`) with empty click handlers |

### Other

| Feature | Status | Notes |
|---------|--------|-------|
| Notifications (push) | Not started | Bell icon stub in `HomeScreen.kt` |

### Architectural Debt

| Item | Status | Notes |
|------|--------|-------|
| Phase 3: Domain model separation | Deferred | Firebase Timestamp in UserProfile/LikedSong/PlaybackState; TODO comments in `AuthRepository.kt`, `AuthResult.kt` |

## Setup

1. Clone the repository
2. Open in Android Studio
3. Add your `google-services.json` to `app/` (and `automotive/` for AAOS builds)
4. Add your Google Web Client ID to `local.properties`:
   ```properties
   GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
   ```
   (Find this in Firebase Console → Authentication → Sign-in method → Google provider → Web Client ID)
5. In Firebase Console:
   - Enable **Authentication** → Email/Password provider
   - Enable **Authentication** → Google provider
   - Set up **Firestore** collections: `songs`, `genres`, `artists`, `albums`
   - Set up **Realtime Database** for home feed sections
6. Build and run: `./gradlew assembleDebug`

## Build

```bash
./gradlew assembleDebug    # Debug build
./gradlew assembleRelease  # Release build
```

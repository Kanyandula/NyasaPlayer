# NyasaPlayer — Android Automotive OS (AAOS) Architecture

> Reference document for building the AAOS variant of NyasaPlayer.
> Covers architecture, Figma design specs, Firebase compatibility, and implementation roadmap.

---

## Table of Contents

1. [Overview](#overview)
2. [Module Boundaries](#module-boundaries)
3. [MediaSession Ownership](#mediasession-ownership)
4. [State Synchronization](#state-synchronization)
5. [Automotive Abstraction Layers](#automotive-abstraction-layers)
6. [Driver Distraction Compliance](#driver-distraction-compliance)
7. [AAOS Figma Design Specs](#aaos-figma-design-specs)
8. [Firebase Backend Compatibility](#firebase-backend-compatibility)
9. [Required Backend Changes](#required-backend-changes)
10. [Tradeoff Analysis](#tradeoff-analysis)
11. [Implementation Roadmap](#implementation-roadmap)

---

## Overview

NyasaPlayer AAOS shares the domain and data layers with the mobile app but has a completely
different UI layer optimized for in-car use. The key architectural principle is that
`PlaybackService` (MediaSession owner) is the shared boundary — both mobile and automotive
are different `MediaController` clients talking to the same service.

**Target Display:** 1280x720 landscape (16:9), automotive head unit
**Min SDK:** 28 (AAOS minimum)
**Figma Source:** `figma.com/make/HCqiewiK6cfBLpuZ7g1XPV/NyasaPlayer-UI-Design-System`

---

## Module Boundaries

### Current vs Proposed

```
CURRENT:
:core:common  <--  :core:data  <--  :app

PROPOSED:
                                      +-- :app (mobile)
:core:common <-- :core:data <-- :core:playback --+
                                      +-- :automotive
```

### New Modules

| Module | Package | Responsibility |
|---|---|---|
| `:core:playback` | `core.playback` | Player engine, MediaSession, queue management, state persistence — extracted from `app/.../player/` |
| `:automotive` | `com.example.nyasaplayer.auto` | AAOS Activity, car-optimized Compose UI, automotive DI, Car API integration |

### What Moves Where

**`app/player/` --> `:core:playback`** (extract entirely):
- `PlaybackService.kt` — the `MediaSessionService` (single playback owner for both targets)
- `PlaybackQueueManager.kt` — queue state holder
- `PlaybackStatePersistence.kt` — save/restore logic
- `PlaybackCommands.kt` — custom session commands
- `SongMediaItemMapper.kt` — Song <-> MediaItem conversion
- `PlayerUiState.kt` — shared UI state contract

**Stays in `:app`** (mobile-specific):
- `PlayerViewModel.kt` — mobile UI bindings (position polling, expanded/mini mode, like toggling)
- `GlobalPlayerLayer.kt`, `MiniPlayer.kt`, `ExpandedPlayer.kt` — mobile Compose UI
- All screen ViewModels, navigation, auth flow

**New in `:automotive`**:
- `AutomotivePlayerViewModel` — car-optimized state (no expanded/mini modes, distraction-aware)
- `AutomotiveActivity` — single `ComponentActivity` for car display
- `AutomotiveNavigation` — simplified nav (no bottom tabs, no auth on head unit)
- Car-specific Compose screens (see [Figma Design Specs](#aaos-figma-design-specs))

### Dependency Graph

```
:core:common (models, theme, utils, NetworkMonitor)
     ^
     |
:core:data (repositories, Room, Firebase, sync)
     ^
     |
:core:playback (PlaybackService, QueueManager, StatePersistence, MediaSession)
     ^                    ^
     |                    |
:app (mobile)        :automotive (AAOS)
```

### settings.gradle.kts Additions

```kotlin
include(":core:playback")
include(":automotive")
```

### :automotive build.gradle.kts

```kotlin
android {
    namespace = "com.example.nyasaplayer.auto"
    defaultConfig {
        applicationId = "com.example.nyasaplayer"
        minSdk = 28  // AAOS minimum
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:playback"))
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-automotive:1.4.0")
}
```

---

## MediaSession Ownership

### Single Owner in `:core:playback`

```
+------------------------------------------+
|            :core:playback                |
|                                          |
|  PlaybackService (MediaLibraryService)   |
|  +----------+  +----------------+        |
|  | ExoPlayer|  | MediaSession   |        |
|  +----+-----+  +-------+--------+        |
|       |                |                 |
|  +----+----------------+--------+        |
|  | PlaybackQueueManager         |        |
|  | PlaybackStatePersistence     |        |
|  +------------------------------+        |
|                                          |
|  SessionToken + MediaController.Builder  |
+------------------------------------------+
         ^                        ^
    :app (mobile)           :automotive
    MediaController         MediaController
```

### Key Change: MediaLibraryService

The current `PlaybackService` extends `MediaSessionService`. For AAOS, upgrade to
`MediaLibraryService` — a superset that adds browse tree support for the AAOS media center:

| | `MediaSessionService` (current) | `MediaLibraryService` (proposed) |
|---|---|---|
| Playback control | Yes | Yes |
| Browse tree for AAOS | No | Yes |
| Google Assistant | Basic | Full ("play X on NyasaPlayer") |
| Mobile impact | N/A | None (browse tree optional for mobile clients) |

### Browse Tree Structure

```
ROOT
 +-- "Recently Played"   (user-specific, from Firestore)
 +-- "Genres"             (from Room GenreDao)
 |    +-- "Electronic"    (songs filtered by genre)
 |    +-- "Hip Hop"
 |    +-- ...
 +-- "Artists"            (from Room ArtistDao)
 |    +-- "Jimi Hendrix"  (songs filtered by artist)
 |    +-- ...
 +-- "All Songs"          (from Room SongDao, sorted by popularity)
```

**Browse tree depth: Root -> Category -> Items = 3 taps to play** (within AAOS 6-tap limit).

---

## State Synchronization

### Architecture

```
                PlaybackService
                (MediaLibrarySession)
                      |
        +-------------+----------------+
        v                              v
   MediaController                MediaController
   (mobile process)               (automotive process)
        v                              v
+-------------------+        +------------------------+
| PlayerViewModel   |        | AutoPlayerViewModel    |
|                   |        |                        |
| - Mini/Expanded   |        | - Always visible       |
| - Like toggling   |        | - Like toggling        |
| - 250ms polling   |        | - 500ms polling        |
| - Full gestures   |        | - Large touch targets  |
+-------------------+        +------------------------+
```

### Shared State Collector (`:core:playback`)

Both ViewModels extend a shared base that handles MediaController event listening:

```kotlin
// :core:playback
abstract class BasePlayerStateCollector(
    private val mediaControllerFuture: ListenableFuture<MediaController>,
) {
    protected val _playbackState = MutableStateFlow(PlaybackSnapshot())
    val playbackState: StateFlow<PlaybackSnapshot> = _playbackState.asStateFlow()

    protected abstract val positionPollIntervalMs: Long  // 250ms mobile, 500ms auto
}

data class PlaybackSnapshot(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val isShuffled: Boolean = false,
    val queueSize: Int = 0,
)
```

### Why Two Separate ViewModels

| Concern | Mobile | Automotive |
|---|---|---|
| Player modes | Hidden/Mini/Expanded | Always visible (no modes) |
| Like toggling | Yes (optimistic + rollback) | No (distraction risk) |
| Position polling | 250ms (smooth scrubbing) | 500ms (progress bar only) |
| Error display | Snackbar + banner + red progress | Modal overlay + retry |
| Gestures | Swipe expand/collapse | None (touch targets only) |

---

## Automotive Abstraction Layers

### Layer Diagram

```
+--------------------------------------------------+
|                 :automotive                       |
|                                                  |
|  Automotive UI Layer                             |
|    AutomotiveActivity                            |
|    AutomotiveNavHost                             |
|    CarHomeScreen / CarNowPlayingScreen           |
|    CarBrowseScreen / CarErrorOverlay             |
|                  |                               |
|  Automotive Abstraction Layer                    |
|    AutomotivePlayerViewModel                     |
|    CarUxRestrictionsHandler                      |
|                  |                               |
|  Automotive DI (AutoPlayerModule)                |
|    Provides: SessionToken, MediaController       |
|    Provides: CarUxRestrictionsManager            |
+--------------------------------------------------+
         |                |               |
    :core:playback   :core:data     :core:common
```

### CarUxRestrictionsHandler

Wraps the Car API's distraction rules as a reactive StateFlow:

```kotlin
@Singleton
class CarUxRestrictionsHandler @Inject constructor(
    private val carUxRestrictionsManager: CarUxRestrictionsManager,
) {
    private val _restrictions = MutableStateFlow(UxRestrictionState())
    val restrictions: StateFlow<UxRestrictionState> = _restrictions.asStateFlow()
}

data class UxRestrictionState(
    val noTextEntry: Boolean = false,
    val limitedContent: Int = Int.MAX_VALUE,
    val noVideo: Boolean = false,
    val noFiltering: Boolean = false,
)
```

### AutomotiveActivity

Single entry point — no auth flow on head unit (assumes phone-paired account):

```kotlin
@AndroidEntryPoint
class AutomotiveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NyasaTheme {
                AutomotiveApp()  // No auth — uses synced Firebase account
            }
        }
    }
}
```

---

## Driver Distraction Compliance

### Google AAOS Requirements (CTS-enforced)

| Rule | Our Implementation |
|---|---|
| Max 6 taps to reach content | Browse tree: Root -> Category -> Song = 3 taps |
| No scrolling lists > N items | Enforce `CarUxRestrictions.maxCumulativeContentItems` (typically 12) |
| No text input while driving | Observe `UX_RESTRICTIONS_NO_TEXT_ENTRY`, disable keyboard, show voice prompt |
| Min touch target 76dp | All car Compose components: `Modifier.sizeIn(minWidth = 76.dp, minHeight = 76.dp)` |
| No animations > 2 seconds | No spring/tween animations in automotive UI |
| Glanceable content | Max 2 lines of text per list item, high contrast |

### Restriction Flow

```
CarUxRestrictionsManager (system)
    --> CarUxRestrictionsHandler (observes, emits StateFlow)
        --> AutomotivePlayerViewModel (combines with playback state)
            --> Compose UI (conditionally renders based on restrictions)
```

---

## AAOS Figma Design Specs

**Source:** `figma.com/make/HCqiewiK6cfBLpuZ7g1XPV` — `src/app/components/aaos/`

### Design Tokens (Shared with Mobile)

| Token | Value | CSS Variable |
|---|---|---|
| Background | `#0D0D0D` | `--background` |
| Surface | `#1A1A1A` | `--surface` |
| Surface Variant | `#242424` | `--surface-variant` |
| Surface Bright | `#2D2D2D` | `--surface-bright` |
| Primary Start | `#A855F7` | `--primary-start` |
| Primary End | `#7C3AED` | `--primary-end` |
| Text Primary | `rgba(255,255,255,1)` | `--text-primary` |
| Text Secondary | `rgba(255,255,255,0.7)` | `--text-secondary` |
| Text Tertiary | `rgba(255,255,255,0.4)` | `--text-tertiary` |
| Error | `#EF4444` | `--error` |
| Warning | `#F59E0B` | `--warning` |

**Spacing:** 8px grid system
**Border radius:** `16px` (cards/panels), `9999px` (circular buttons)
**Typography scale:** 12/14/16/18/20/24/32/48px

### Screen 1: AAOS Home Screen

**File:** `AAOSHomeScreen.tsx`
**Layout:** Full-height flex column, 2-column grid content + persistent mini player

```
+-----------------------------------------------------------+
|  "Good Evening"                                           |
|  "Ready for your drive?"                                  |
|                                                           |
|  +--- Quick Access ---+  +--- Recently Played ----------+|
|  | [My Music] [Radio] |  | [80x80 art] Title            ||
|  |                     |  |             Artist            ||
|  | [Favorites][Trend.] |  | [80x80 art] Title            ||
|  |                     |  |             Artist            ||
|  +---------------------+  | [80x80 art] Title            ||
|                            |             Artist            ||
|                            | [80x80 art] Title            ||
|                            +------------------------------+|
|                                                           |
|  [Mini Player Bar - 112px height]                         |
+-----------------------------------------------------------+
```

**Quick Actions:** 4 gradient square buttons, 48px icons
- My Music: `from-purple-500 to-purple-700`
- Radio: `from-pink-500 to-rose-700`
- Favorites: `from-red-500 to-red-700`
- Trending: `from-blue-500 to-indigo-700`

**Recently Played:** Vertical list, each item: 80x80px album art + title (xl) + artist (base), play overlay on hover

**Compose mapping:**
- Quick Actions -> 4 large `Card` composables with `Brush.linearGradient`
- Recently Played -> `LazyColumn` with `76.dp` min-height items
- Grid -> `Row` with two equal-weight `Column`s

### Screen 2: AAOS Full Player

**File:** `AAOSFullPlayer.tsx`
**Layout:** Horizontal — 400x400px album art (left) + controls (right)

```
+-----------------------------------------------------------+
| [v]        PLAYING FROM PLAYLIST        [...]             |
|                Road Trip Mix                               |
|                                                           |
|  +----------------+   Purple Haze                         |
|  |                |   Jimi Hendrix - Are You Experienced  |
|  |   400x400px    |                                       |
|  |   Album Art    |   [====gradient====--------] 2:34/5:42|
|  |   rounded-3xl  |                                       |
|  |                |   (shuf) (<) [  PLAY  ] (>) (rpt)     |
|  +----------------+            112px btn                   |
|                                                           |
|                        [heart]                            |
+-----------------------------------------------------------+
```

**Control button sizes (Compose dp mapping):**
- Shuffle: `64.dp` circle, `28px` icon
- Skip Back/Forward: `80.dp` circle, `36px` icon
- Play/Pause: `112.dp` circle, gradient `#A855F7 -> #7C3AED`, `48px` icon
- Repeat: `64.dp` circle, `28px` icon
- Like: `56.dp` circle (secondary action)
- Collapse/More: `56.dp` circle (top bar)

**Progress bar:** Full-width seekable slider, purple gradient fill, `h-3` track, `w-6 h-6` thumb
**Track info:** Title `4xl` (36sp), Artist `2xl` (24sp)

### Screen 3: AAOS Browse Screen

**File:** `AAOSBrowseScreen.tsx`
**Layout:** 2-column — playlist grid (left) + genre list (right) + persistent mini player

```
+-----------------------------------------------------------+
|  "Browse Music"                                           |
|  "Explore playlists and genres"                           |
|                                                           |
|  +-- Featured Playlists --+  +-- Browse by Genre --------+|
|  | [img][img]              |  | [|] Electronic  1,243 [>]||
|  | [img][img]              |  | [|] Hip Hop       987 [>]||
|  | [img][img]              |  | [|] Jazz           654 [>]||
|  +-------------------------+  | [|] Rock         2,112 [>]||
|                               | [|] Pop          1,876 [>]||
|                               | [|] Classical      432 [>]||
|                               +---------------------------+|
|                                                           |
|  [Mini Player Bar - 112px height]                         |
+-----------------------------------------------------------+
```

**Playlist cards:** Square aspect ratio, image + gradient overlay + text overlay (bottom)
**Genre items:** Full-width rows, `p-6`, purple accent bar (`w-2 h-12`), genre name (xl), count, play button (48px circle)

### Screen 4: AAOS Error States

**File:** `AAOSErrorStates.tsx`
**Three variants:**

| Error | Icon | Gradient | Actions |
|---|---|---|---|
| No Internet | `WifiOff` | `from-orange-500 to-red-500` | Dismiss + Retry |
| Server Error (503) | `ServerCrash` | `from-red-500 to-rose-700` | Dismiss + Retry |
| Connection Lost | `AlertTriangle` | `from-yellow-500 to-orange-500` | Auto-reconnect spinner |

**Modal layout:**
- Backdrop: `black/80` + blur
- Card: `max-w-2xl`, `bg-[#1A1A1A]`, `rounded-3xl`, `p-12`, `border-white/10`
- Icon: `128px` gradient circle
- Title: `3xl` (30sp), Description: `xl` (20sp)
- Buttons: `py-5`, `xl` text, full-width row
- Retry button: purple gradient with `RefreshCw` icon
- Reconnecting: animated spinning `RefreshCw` icon

### Screen 5: AAOS Mini Player

**File:** `AAOSMiniPlayer.tsx`
**Persistent bar, 112px height, docked at bottom of Home and Browse screens**

```
+-----------------------------------------------------------+
| [80x80 art] Title      | (<) [PLAY 80px] (>) | prog [<3] |
|             Artist      |                      |           |
+-----------------------------------------------------------+
```

**Three sections:**
1. **Left — Now Playing:** 80x80px album art (rounded-xl) + title (xl) + artist (base), truncated
2. **Center — Controls:** Skip Back (56px), Play/Pause (80px gradient), Skip Forward (56px)
3. **Right — Progress + Like:** Progress bar (gradient fill) with timestamps + Like button (56px)

**Surface:** `#1A1A1A`, top border `white/10`

---

## Firebase Backend Compatibility

### Assessment: SAME FIREBASE PROJECT — FULLY COMPATIBLE

The existing Firebase setup can serve both mobile and AAOS with minor extensions.

### Current Data Architecture

```
Firestore
 +-- songs (collection)        --> synced to Room SongEntity
 +-- genres (collection)       --> synced to Room GenreEntity
 +-- artists (collection)      --> synced to Room ArtistEntity
 +-- albums (collection)       --> NOT synced yet (no Room entity)
 +-- users/{uid}/
      +-- profile              --> read directly from Firestore
      +-- likedSongs/{mediaId} --> read directly from Firestore
      +-- recentlyPlayed/      --> read directly from Firestore
      +-- playbackState/current --> save/restore for cross-device resume

Realtime Database
 +-- homeFeed/default          --> home feed sections
```

### What Works Out of the Box

| Capability | Status | Details |
|---|---|---|
| Catalog sync (songs, genres, artists) | READY | `FirebaseSyncManager` syncs to Room on startup; AAOS reads from Room |
| User authentication | READY | Same Firebase Auth project; one UID across phone + AAOS |
| User-scoped data | READY | All user data under `users/{uid}/`; AAOS reads same data |
| Playback state sync | READY | `playbackState/current` enables cross-device resume |
| Offline-first browsing | READY | Room DB is source of truth; works without network |
| Like/unlike songs | READY | Firestore `likedSongs` sub-collection, user-scoped |
| Recently played | READY | Firestore `recentlyPlayed` sub-collection, user-scoped |

### What Needs Changes

| Issue | Severity | Details |
|---|---|---|
| No Album entity in Room | HIGH | `Song` has `albumId`/`albumName` but no `albums` table; can't browse by album |
| Missing DAO queries for browse tree | HIGH | No `getByPopularity()`, `getByGenre()` on ArtistDao; no `getByPopularity()` on GenreDao/SongDao |
| Genre query uses LIKE search | MEDIUM | `SongDao.getByGenreId()` does `LIKE '%genreId%'` (full table scan); needs optimization for large catalogs |
| No Firestore security rules in repo | MEDIUM | Must configure rules to allow AAOS access to catalog collections |
| Download data is device-local | LOW | `DownloadEntity` not in Firestore; AAOS can't see phone downloads |
| Queue size IPC limit | LOW | `SongMediaItemMapper` warns ~100-200 songs max per Bundle transaction |

### Authentication Strategy for AAOS

**Phone-paired approach (recommended):**
1. User authenticates on phone (existing Email/Password or Google Sign-In)
2. AAOS app checks `AuthRepository.isAuthenticated` on launch
3. If authenticated: show home screen, read user data from same `users/{uid}/`
4. If not authenticated: show "Pair your phone" screen with instructions
5. No keyboard auth on head unit (violates distraction rules)

**Technical detail:** Both apps share the same `google-services.json` Firebase project.
Firebase Auth persists login state per-device, so AAOS needs its own sign-in event.
Options: QR code pairing, Google Sign-In via vehicle's Google account, or
companion phone deep link.

---

## Required Backend Changes

### Phase 1: Room DAO Extensions (No Firebase Changes)

Add queries needed for the AAOS browse tree:

```kotlin
// SongDao — add:
@Query("SELECT * FROM songs ORDER BY popularity DESC LIMIT :limit")
fun getByPopularity(limit: Int): Flow<List<SongEntity>>

@Query("SELECT * FROM songs WHERE album_id = :albumId")
fun getByAlbumId(albumId: String): Flow<List<SongEntity>>

// ArtistDao — add:
@Query("SELECT * FROM artists ORDER BY popularity DESC LIMIT :limit")
fun getByPopularity(limit: Int): Flow<List<ArtistEntity>>

// GenreDao — add:
@Query("SELECT * FROM genres ORDER BY popularity DESC LIMIT :limit")
fun getByPopularity(limit: Int): Flow<List<GenreEntity>>
```

### Phase 2: Album Entity + Sync

```kotlin
// New: AlbumEntity
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "artist_id") val artistId: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "song_count") val songCount: Int,
)

// New: AlbumDao
@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums")
    fun getAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE artist_id = :artistId")
    fun getByArtistId(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getById(albumId: String): AlbumEntity?

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Transaction
    suspend fun sync(albums: List<AlbumEntity>) {
        upsertAll(albums)
        deleteNotIn(albums.map { it.id })
    }
}
```

Update `FirebaseSyncManager` to sync `albums` collection alongside songs/genres/artists.
Bump Room database version from 3 to 4 with migration.

### Phase 3: Firestore Security Rules (Firebase Console)

```javascript
// Recommended Firestore security rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Catalog — read-only for authenticated users
    match /songs/{songId} {
      allow read: if request.auth != null;
      allow write: if false; // admin SDK only
    }
    match /genres/{genreId} {
      allow read: if request.auth != null;
      allow write: if false;
    }
    match /artists/{artistId} {
      allow read: if request.auth != null;
      allow write: if false;
    }
    match /albums/{albumId} {
      allow read: if request.auth != null;
      allow write: if false;
    }

    // User data — scoped to authenticated user
    match /users/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /users/{uid}/{subcollection}/{docId} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

### Phase 4: Firestore Indexes (Only If Catalog > 10K Songs)

Create composite indexes via Firebase Console or `firestore.indexes.json`:

```json
{
  "indexes": [
    {
      "collectionGroup": "songs",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "artistId", "order": "ASCENDING" },
        { "fieldPath": "popularity", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "songs",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "albumId", "order": "ASCENDING" },
        { "fieldPath": "popularity", "order": "DESCENDING" }
      ]
    }
  ]
}
```

These are only needed at scale. Room handles queries locally, so Firestore indexes
only matter for sync performance, not browse-time performance.

### No Backend Changes Needed For

- Playback state sync (already stored at `users/{uid}/playbackState/current`)
- Like/unlike (already user-scoped)
- Recently played (already user-scoped)
- Home feed (Realtime Database, read-only)
- Catalog sync (one-shot Firestore -> Room, already works)

---

## Tradeoff Analysis

### Decision 1: Separate `:automotive` Module vs Build Flavors in `:app`

| | Separate Module (chosen) | Build Flavor |
|---|---|---|
| Code isolation | Full — different source sets | Partial — `if (isAuto)` branches |
| Build independence | Each builds alone | Coupled builds |
| APK size | Minimal — only auto deps in auto APK | Mobile ships car deps |
| Team scalability | Different teams can own each | Single team owns both |

**Choice: Separate module.** The UI layers are fundamentally different paradigms.

### Decision 2: MediaLibraryService vs MediaSessionService + Separate CarAppService

| | `MediaLibraryService` (chosen) | Dual Services |
|---|---|---|
| Single source of truth | Yes | Risk of state divergence |
| AAOS media center integration | Automatic | Manual |
| Google Assistant | Full | Limited |
| Complexity | +browse tree callbacks | +IPC between services |

**Choice: MediaLibraryService.** Superset API, better AAOS integration.

### Decision 3: Shared ViewModel vs Separate ViewModels

| | Shared ViewModel | Separate + Shared Collector (chosen) |
|---|---|---|
| Code reuse | Maximum | Moderate |
| Platform-specific behavior | `when(platform)` branches | Clean separation |
| Testing | One complex test suite | Two focused test suites |

**Choice: Separate ViewModels.** UI contracts are too different to share cleanly.

### Decision 4: Auth on Head Unit vs Phone-Paired

| | Auth on Head Unit | Phone-Paired (chosen) |
|---|---|---|
| UX | Typing password on car = terrible | Seamless |
| Distraction compliance | Very difficult | Not applicable |
| Implementation | Full auth flow for car | Check `isAuthenticated`, show pairing prompt |

**Choice: Phone-paired.** Distraction rules effectively prohibit on-screen auth.

### Decision 5: Same Firebase Project vs Separate

| | Same Project (chosen) | Separate Project |
|---|---|---|
| Data sharing | Automatic (same UID) | Requires sync service |
| Configuration | One `google-services.json` | Two configurations |
| Cost | Single billing | Double billing |
| Isolation | Shared quotas | Independent scaling |

**Choice: Same project.** User data is already UID-scoped; no reason to duplicate.

---

## Implementation Roadmap

### Phase 1: Extract `:core:playback` (No Behavior Change)

- [x] Create `:core:playback` module with build.gradle.kts
- [x] Move player files from `app/player/` to `core/playback/`
- [x] Update `:app` to depend on `:core:playback`
- [x] Verify all existing tests pass, mobile app unchanged
- [x] Extract `BasePlayerStateCollector` from `PlayerViewModel`

### Phase 2: Upgrade to `MediaLibraryService`

- [x] Change `PlaybackService` base class to `MediaLibraryService`
- [x] Implement `MediaBrowseTreeBuilder` with catalog queries from repositories
- [x] Add `onGetLibraryRoot`, `onGetChildren`, `onGetItem`, `onSearch` callbacks
- [x] Extend Room DAOs with `getByPopularity()` queries
- [x] Mobile continues using `MediaController` unchanged

### Phase 3: Room Schema Updates

- [x] Add `AlbumEntity`, `AlbumDao` to Room database
- [x] Bump database version 3 -> 4 with migration
- [x] Update `FirebaseSyncManager` to sync `albums` collection
- [x] Add `AlbumRepository` interface + `OfflineAlbumRepository` implementation
- [x] Update `RepositoryModule` bindings

### Phase 4: Create `:automotive` Module

- [x] Scaffold module: `build.gradle.kts`, `AndroidManifest.xml`
- [x] Create `AutomotiveActivity` (single entry point, no auth)
- [x] Create `AutoPlayerModule` (Hilt DI for car)
- [x] Implement `AutomotivePlayerViewModel` extending shared state collector
- [x] Implement `CarUxRestrictionsHandler`

### Phase 5: Implement AAOS Screens (from Figma)

- [x] `CarHomeScreen` — 2-column: quick actions + recently played + mini player
- [x] `CarNowPlayingScreen` — horizontal: album art + controls
- [x] `CarBrowseScreen` — 2-column: playlists + genres + mini player
- [x] `CarMiniPlayer` — 112px persistent bar
- [x] `CarErrorOverlay` — modal with 3 error variants
- [x] `AutomotiveNavHost` — simplified navigation between screens

### Phase 6: Wire End-to-End Playback

- [x] `AutomotiveApplication` — inject and start `FirebaseSyncManager`
- [x] `AutomotivePlayerViewModel` — add `playSong()`, `shufflePlay()`, queue commands
- [x] `AutomotiveContentViewModel` — add `getSongsByGenre()`, `getSongsByAlbum()`
- [x] `AutomotiveApp` — wire all TODO click lambdas to ViewModel play actions

### Phase 7: Firebase Configuration & AAOS Authentication

- [x] Configure Firestore security rules (`firestore.rules` — catalog read-only, user data scoped)
- [x] Add `firebase.json` project configuration
- [x] `CarAuthScreen` — Google Sign-In for AAOS (one-tap, no keyboard, CTS-compliant)
- [x] `AutomotiveAuthViewModel` — handles Google credential flow + profile creation
- [x] Auth gate in `AutomotiveApp` — show `CarAuthScreen` when unauthenticated
- [x] Delay `FirebaseSyncManager.start()` until after authentication
- [x] Add Credential Manager dependencies to `:automotive` build
- [x] Review Realtime Database rules for home feed access
- [ ] Test cross-device playback state sync (phone -> AAOS)
- [ ] Add Firestore composite indexes if catalog > 10K items

### Phase 8: Polish and CTS Compliance

- [x] Fix CarTopBar tab touch targets to meet 76dp CTS minimum
- [x] Wire Quick Action clicks (My Music/Favorites → Library, Trending/Radio → Browse)
- [x] Wire Category Card clicks (play genre songs via shuffle)
- [x] Wire Artist clicks (play artist songs)
- [x] Wire Search bar click (navigate to Browse)
- [x] Add Realtime Database rules (`database.rules.json`)
- [ ] Test on AAOS emulator (Automotive system image)
- [ ] Test on DHU (Desktop Head Unit)
- [ ] Validate all distraction rules (touch targets, list limits, text input)
- [ ] Run CTS media tests

---

## Key File References

| Component | Current Path |
|---|---|
| PlaybackService | `app/src/main/java/.../player/PlaybackService.kt` |
| PlayerViewModel | `app/src/main/java/.../player/PlayerViewModel.kt` |
| PlaybackQueueManager | `app/src/main/java/.../player/PlaybackQueueManager.kt` |
| PlaybackStatePersistence | `app/src/main/java/.../player/PlaybackStatePersistence.kt` |
| PlaybackCommands | `app/src/main/java/.../player/PlaybackCommands.kt` |
| SongMediaItemMapper | `app/src/main/java/.../player/SongMediaItemMapper.kt` |
| PlayerUiState | `app/src/main/java/.../player/PlayerUiState.kt` |
| FirebaseSyncManager | `core/data/src/main/java/.../sync/FirebaseSyncManager.kt` |
| FirebaseUserRepository | `core/data/src/main/java/.../FirebaseUserRepository.kt` |
| NyasaDatabase | `core/data/src/main/java/.../local/NyasaDatabase.kt` |
| SongDao | `core/data/src/main/java/.../local/dao/SongDao.kt` |
| ArtistDao | `core/data/src/main/java/.../local/dao/ArtistDao.kt` |
| GenreDao | `core/data/src/main/java/.../local/dao/GenreDao.kt` |
| RepositoryModule | `core/data/src/main/java/.../di/RepositoryModule.kt` |
| AndroidManifest | `app/src/main/AndroidManifest.xml` |

**Figma Design Source:** `figma.com/make/HCqiewiK6cfBLpuZ7g1XPV` — `src/app/components/aaos/`

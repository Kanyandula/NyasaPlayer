# 🚗 AAOS Music Player — Internal Architecture Report

> **Project:** NyasaPlayer
> **Module:** `:automotive` (Android Automotive OS)
> **Date:** March 2026
> **Status:** MVP — Phase 8 (Polish & CTS Compliance) in progress

---

## 1️⃣ Executive Summary

### Objective

NyasaPlayer ships a **native AAOS music app** that runs directly on automotive head units — no phone projection required. The app shares its playback engine, data layer, and domain models with the existing mobile app while providing a purpose-built UI optimized for driver safety and CTS compliance.

### Scope

| What Was Built | What Is Shared | What Is Automotive-Only |
|---|---|---|
| Full browse & playback experience | `:core:playback` (MediaLibraryService, queue, persistence) | `:automotive` module (Compose UI, CarUxRestrictions) |
| Google Sign-In auth on head unit | `:core:data` (repositories, Room DB, Firebase sync) | `AutomotivePlayerViewModel` (500ms polling, restrictions-aware) |
| Offline-first catalog browsing | `:core:common` (models, theme, utilities) | `CarUxRestrictionsHandler` (driving state observer) |
| CTS-compliant distraction rules | Firebase project & security rules | `AutomotiveActivity` (single entry point) |
| Cross-device playback state sync | `NetworkMonitor`, error handling patterns | Car-optimized screens (76dp touch targets, content limiting) |

### Key Architectural Decisions

1. **Single `PlaybackService` owner** — both mobile and AAOS are `MediaController` clients talking to the same `MediaLibraryService`
2. **Separate `:automotive` module** (not build flavors) — clean separation, independent manifest and dependencies
3. **Separate ViewModels** — `AutomotivePlayerViewModel` is purpose-built for car constraints, not a shared abstraction
4. **Same Firebase project** — one UID across phone + head unit, shared catalog and user data
5. **Offline-first** — Firestore syncs to Room on startup; UI reads from Room (works without network)

### Current Status

| Area | Status |
|---|---|
| Core playback | Production-ready |
| Catalog browsing | MVP-complete |
| Authentication | MVP-complete (Google Sign-In) |
| CTS compliance | Validated in code, pending emulator/DHU testing |
| Cross-device sync | Implemented, pending end-to-end testing |
| Test coverage | Core playback + data layer covered; automotive UI tests pending |

---

## 2️⃣ Problem Statement & Goals

### Why Native AAOS Instead of Android Auto Projection?

| Factor | Android Auto (Projection) | Native AAOS |
|---|---|---|
| Phone dependency | Required — app runs on phone, projected to screen | None — runs directly on head unit |
| Offline operation | Limited by phone connection | Full offline support via local Room DB |
| System integration | Sandboxed by projection protocol | Direct access to Car APIs, audio focus, system media center |
| Browse tree | Template-constrained | Full Compose UI with custom screens |
| Background playback | Depends on phone process | Native foreground service on head unit |

**Decision:** Native AAOS gives us deeper integration, offline capability, and a richer UX that justifies the additional module investment.

### In Scope

- Playback control (play, pause, skip, seek, shuffle, repeat)
- Browse tree (recently played, genres, artists, albums, all songs)
- MediaSession integration (steering wheel controls, system media center, assistant)
- Liked songs management
- Cross-device playback state resume
- One-tap Google Sign-In (CTS-compliant, no keyboard)
- Offline catalog browsing

### Out of Scope

- Full-text search typing (violates distraction rules while driving)
- Account management on head unit (password change, email update)
- Playlist creation/editing
- Download management (device-local, not cross-device)
- Advanced personalization (recommendations, algorithmic feeds)
- Multi-zone audio
- Voice assistant deep integration

---

## 3️⃣ Platform Investigation Summary

### 3.1 AAOS vs Android Auto

| Aspect | Android Auto | AAOS |
|---|---|---|
| Runtime | Phone app projected to car display | Native app on embedded Android OS |
| UI framework | Car App Library templates | Full Jetpack Compose |
| Process lifecycle | Phone process, car is a display | Head unit process, survives ignition cycles |
| API access | Limited Car App APIs | Full Car platform APIs (`android.car.*`) |
| Distribution | Google Play (phone) | Google Automotive App Store |
| CTS requirement | Car App Library compliance | Full CTS media + distraction test suite |

### 3.2 Key Platform Constraints

| Constraint | Specification | Our Implementation |
|---|---|---|
| Touch targets | >= 76dp minimum | `CarTouchTargetSize = 76.dp` enforced on all interactive elements |
| Text input | Prohibited while driving | Google Sign-In only (one-tap); no `TextField` in any screen |
| Content limiting | `maxCumulativeContentItems` (typically 12) | `BrowseShell` applies `.take(maxItems)` to all list data |
| Tap depth | <= 6 taps to any action | Root -> Category -> Song = 3 taps (within limit) |
| Animations | No distracting motion | Scrollbar animation 150ms (well under 2s limit) |
| Video | Prohibited for driver display | Audio-only app; all images are static album art |
| Audio focus | Must respect system policies | ExoPlayer handles via `USAGE_MEDIA` + `CONTENT_TYPE_MUSIC` |

### 3.3 System Integration Points

| Integration | Component | Status |
|---|---|---|
| MediaSession | `MediaLibraryService` in `:core:playback` | Complete — steering wheel controls, system media center |
| Browse tree | `MediaBrowseTree` — 4 root categories, 3 levels deep | Complete — genres, artists, recently played, all songs |
| `CarUxRestrictionsManager` | `CarUxRestrictionsHandler` singleton | Complete — reactive StateFlow, wired to all screens |
| Audio focus | ExoPlayer built-in handling | Complete — `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC)` |
| Assistant | `MediaLibraryService.onSearch()` / `onGetSearchResult()` | Complete — delegates to `MediaBrowseTree.search()` |

---

## 4️⃣ High-Level Architecture

```
+-------------------------------------------------------------+
|                    :core:common                              |
|    Domain Models - Theme - UI Components - NetworkMonitor    |
+--------------------------+----------------------------------+
                           |
+--------------------------v----------------------------------+
|                     :core:data                               |
|   Repository Interfaces - Offline*Repository Implementations |
|   Room Database (Songs, Artists, Genres, Albums, Downloads)   |
|   FirebaseSyncManager - Firebase DAOs                        |
+--------------------------+----------------------------------+
                           |
+--------------------------v----------------------------------+
|                   :core:playback                             |
|   PlaybackService (MediaLibraryService -- single owner)      |
|   PlaybackQueueManager - PlaybackStatePersistence            |
|   BasePlayerStateCollector - MediaBrowseTree                 |
|   SongMediaItemMapper - PlaybackCommands                     |
+---------------+------------------------------+--------------+
                |                              |
+---------------v--------------+  +------------v---------------+
|        :app (Mobile)         |  |     :automotive (AAOS)     |
|                              |  |                            |
|  PlayerViewModel             |  |  AutomotivePlayerViewModel |
|  (250ms polling)             |  |  (500ms polling)           |
|                              |  |                            |
|  Screens:                    |  |  Screens:                  |
|  ForYou, Search,             |  |  CarHome, CarBrowse,       |
|  Library, Profile,           |  |  CarLibrary, CarFullPlayer,|
|  ExpandedPlayer,             |  |  CarAuth, CarMiniPlayer    |
|  MiniPlayer                  |  |                            |
|                              |  |  CarUxRestrictionsHandler  |
|  NavHost (bottom tabs)       |  |  (driving state observer)  |
+------------------------------+  +----------------------------+
```

### Single Source of Truth

`PlaybackService` is the **single owner** of:
- ExoPlayer instance
- MediaSession
- Playback queue state
- Playback position

Both `:app` and `:automotive` connect as `MediaController` clients via `ListenableFuture<MediaController>`. Neither directly touches ExoPlayer — all commands flow through `SessionCommand` IPC.

---

## 5️⃣ Key Architectural Decisions & Tradeoffs

### Decision 1: `MediaLibraryService` Instead of `MediaSessionService`

| | Option A: `MediaSessionService` + `CarAppService` | Option B: `MediaLibraryService` (Chosen) |
|---|---|---|
| Browse tree | Requires separate `CarAppService` implementation | Built-in via `onGetLibraryRoot()` / `onGetChildren()` |
| Assistant | No built-in search | `onSearch()` / `onGetSearchResult()` for free |
| System media center | Basic metadata only | Full hierarchical browsing |
| Complexity | Two service classes to maintain | Single service, more callbacks |

**Why Chosen:** `MediaLibraryService` is the standard for media apps on AAOS. It provides browse tree support, assistant integration, and system media center compatibility in one class. The alternative would require maintaining a separate `CarAppService` for browse functionality.

**Tradeoff:** More callbacks to implement (`onGetLibraryRoot`, `onGetChildren`, `onGetItem`, `onSearch`, `onGetSearchResult`), but these map naturally to our existing repository layer.

### Decision 2: Separate `:automotive` Module vs Build Flavors

| | Option A: Build Flavors | Option B: Separate Module (Chosen) |
|---|---|---|
| Code separation | Shared source sets with conditional compilation | Clean module boundary |
| Manifest | Single manifest with flavor-specific overrides | Independent manifests |
| Dependencies | Flavor-scoped dependencies | Module-scoped dependencies |
| Build time | Single build graph | Parallel module builds |
| Testing | Shared test infrastructure | Independent test targets |

**Why Chosen:** AAOS has fundamentally different manifest requirements (`android.hardware.type.automotive`, `automotive_app_desc.xml`), different activities, and different compile-only dependencies (`android.car.jar`). A separate module provides a clean boundary and avoids conditional compilation complexity.

**Tradeoff:** Slightly more Gradle configuration, but the separation prevents accidental coupling between mobile and automotive UI layers.

### Decision 3: Separate ViewModels vs Shared ViewModel

| | Option A: Shared ViewModel | Option B: Separate ViewModels (Chosen) |
|---|---|---|
| Code reuse | Maximum — one ViewModel class | Shared base class (`BasePlayerStateCollector`) |
| Platform specifics | Conditional logic inside ViewModel | Clean, purpose-built classes |
| Polling interval | Runtime switch | Compile-time constant (250ms vs 500ms) |
| Restrictions | Automotive-only state mixed with mobile | `UxRestrictionState` only in automotive VM |
| Testability | Mock platform differences | Test each ViewModel independently |

**Why Chosen:** Mobile and automotive have fundamentally different state shapes. Mobile has `PlayerMode` (Hidden/Mini/Expanded), like state per-screen, and 250ms position polling for smooth scrubbing. Automotive has `UxRestrictionState`, simpler navigation (no mode switching), and 500ms polling (progress bar only). Sharing a base class (`BasePlayerStateCollector`) for controller connection and event handling gives us code reuse where it matters without platform-branching inside the ViewModel.

**Tradeoff:** Two ViewModel classes to maintain, but they share the complex controller lifecycle logic via `BasePlayerStateCollector`.

### Decision 4: Google Sign-In on Head Unit vs Phone-Paired Auth

| | Option A: Phone-Paired Only | Option B: Google Sign-In on Head Unit (Chosen) |
|---|---|---|
| UX | "Pair your phone" screen, QR code flow | One-tap Google button, immediate auth |
| CTS compliance | No input required | One-tap (no keyboard), CTS-compliant |
| Dependency | Requires companion phone | Standalone — works without phone |
| Implementation | Deep link / QR pairing protocol | Standard Firebase Auth + Credential Manager |

**Why Chosen:** AAOS head units have a Google account associated with the vehicle. One-tap Google Sign-In leverages this existing account without requiring a companion phone or keyboard input. This is simpler, more reliable, and CTS-compliant.

**Tradeoff:** Users must have a Google account on the vehicle. Email/password auth is not available on AAOS (would require keyboard).

### Decision 5: Same Firebase Project vs Separate

| | Option A: Separate Firebase Projects | Option B: Same Project (Chosen) |
|---|---|---|
| Data isolation | Separate user data per platform | Shared user data, one UID |
| Cross-device resume | Requires sync protocol | Automatic — same `users/{uid}/playbackState` |
| Maintenance | Two consoles, two rule sets | One console, one rule set |
| Security rules | Simpler (platform-scoped) | Must handle both clients |

**Why Chosen:** The entire value proposition of cross-device resume depends on sharing a Firebase project. Same UID means liked songs, recently played, and playback state are instantly available on both devices. Security rules scope data to `users/{uid}`, which works regardless of which device makes the request.

**Tradeoff:** Both apps share the same `google-services.json`. Security rules must be written to handle both mobile and automotive access patterns (they're identical in practice).

---

## 6️⃣ Playback & State Architecture

### Ownership Chain

```
ExoPlayer (media playback engine)
     |
     v
PlaybackQueueManager (in-memory queue state, shuffle logic)
     |
     v
PlaybackService (MediaLibraryService -- MediaSession owner)
     |
     +-- MediaSession (system integration: steering wheel, assistant)
     |
     v
MediaController (IPC boundary -- Bundle commands)
     |
     v
BasePlayerStateCollector (shared state sync: events, polling)
     |
     +-- PlayerViewModel (mobile: 250ms polling, expanded/mini mode)
     +-- AutomotivePlayerViewModel (AAOS: 500ms polling, restrictions)
          |
          v
     Compose UI (collectAsState -> render)
```

### Custom IPC Commands

Queue management crosses the IPC boundary via `SessionCommand`:

| Command | Purpose | Bundle Keys |
|---|---|---|
| `nyasa.SET_QUEUE` | Set queue and start playback at index | `KEY_SONGS` (JSON), `KEY_START_INDEX` |
| `nyasa.SHUFFLE_PLAY` | Shuffle songs and play | `KEY_SONGS` (JSON) |
| `nyasa.RESTORE_STATE` | Restore saved playback state | `KEY_SONGS`, `KEY_START_INDEX`, `KEY_POSITION_MS`, `KEY_REPEAT_MODE` |
| `nyasa.TOGGLE_SHUFFLE` | Toggle shuffle on/off | (none) |

Songs are serialized as JSON in Bundles. **IPC limit:** ~200 songs per transaction (Binder transaction size).

### Position Polling

| Platform | Interval | Reason |
|---|---|---|
| Mobile | 250ms | Smooth scrubber animation |
| AAOS | 500ms | Progress bar sufficient fidelity |

Polling runs only when `controller.isPlaying` to avoid unnecessary updates.

### Queue Persistence & Cross-Device Resume

```
PlaybackService
     |
     +-- Periodic save (every 30 seconds while playing)
     |        +-- PlaybackStatePersistence.save()
     |              +-- UserRepository.savePlaybackState(uid, state)
     |                    +-- Firestore: users/{uid}/playbackState/current
     |
     +-- Final save (on service destroy, 2s timeout)
              +-- PlaybackStatePersistence.saveFinal()

Resume flow (on app start):
     PlaybackStatePersistence.restore()
          +-- UserRepository.getPlaybackState(uid)
               +-- Rebuild queue from SongRepository.getSongsByIds()
                    +-- CMD_RESTORE_STATE -> PlaybackService
```

**Persisted State:**
- `currentSongId` — which song was playing
- `positionMs` — playback position
- `queueSongIds` — ordered list of song IDs in queue
- `queueIndex` — current index in queue
- `repeatMode` — Off / All / One
- `savedAt` — timestamp

### Process Death Handling

- `PlaybackService` runs as a foreground service with persistent notification
- On process death: service is recreated by the system, playback state is lost in memory
- On next app launch: `PlaybackStatePersistence.restore()` rebuilds from Firestore
- **Best-effort:** If the final save didn't complete, the last periodic save (<=30s old) is used

---

## 7️⃣ AAOS-Specific Design Considerations

### 7.1 Driver Distraction Compliance

| Rule | Enforcement Mechanism | Implementation |
|---|---|---|
| 76dp touch targets | `CarTouchTargetSize = 76.dp` constant | All buttons, tabs, list items use this minimum |
| No text input | No `TextField` in any automotive screen | Google Sign-In (one-tap), search bar is click-to-browse only |
| Content limiting | `UxRestrictionState.limitedContentItems` | `BrowseShell` applies `.take(maxItems)` to all lists |
| 6-tap limit | Browse tree depth <= 3 | Root -> Category -> Song = 3 taps |
| No video | Audio-only app | All visual content is static `AsyncImage` |
| Animation limits | Scrollbar: 150ms `tween` | Well under 2-second CTS threshold |

**CarUxRestrictionsHandler Flow:**

```
CarUxRestrictionsManager (system service)
     |
     v  OnUxRestrictionsChangedListener
CarUxRestrictionsHandler (converts flags -> UxRestrictionState)
     |
     v  restrictions: StateFlow<UxRestrictionState>
AutomotivePlayerViewModel (observes, updates AutomotiveUiState)
     |
     v  uiState.restrictions
BrowseShell (applies .take(maxItems) to all content lists)
```

**Restriction Flags Tracked:**

| Flag | Field | Effect |
|---|---|---|
| `UX_RESTRICTIONS_NO_TEXT_MESSAGE` | `noTextEntry` | Prevents text input (already excluded by design) |
| `maxCumulativeContentItems` | `limitedContentItems` | Limits list items during driving |
| `UX_RESTRICTIONS_NO_VIDEO` | `noVideo` | Prevents video display (audio-only app) |
| `UX_RESTRICTIONS_NO_FILTERING` | `noFiltering` | Prevents complex search/filter operations |

### 7.2 Audio Focus Strategy

ExoPlayer handles audio focus automatically via `AudioAttributes`:

```kotlin
AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .build()
```

| Interruption | Expected Behavior |
|---|---|
| Navigation announcement | Duck (lower volume temporarily) |
| Phone call | Pause, resume after call ends |
| Assistant activation | Pause, resume after assistant completes |
| Another media app | Lose focus, pause |

Audio focus handling is delegated to ExoPlayer's built-in focus management. No custom `AudioFocusRequest` logic is needed — Media3 handles this correctly for `USAGE_MEDIA`.

### 7.3 Lifecycle Handling

| Event | Behavior |
|---|---|
| Ignition off | `PlaybackService` saves state via `saveFinalState()` (2s timeout) |
| Ignition on | `AutomotiveApplication.onCreate()` starts `FirebaseSyncManager`, restores state |
| Process death | Foreground service notification persists; state restored from Firestore on next launch |
| User profile switch | `AuthRepository.currentUser` changes; new UID scopes all user data |
| Boot completed | `AutomotiveActivity` launches as `MAIN/LAUNCHER`; checks auth state |

---

## 8️⃣ Backend & Data Considerations

### Data Architecture

```
Firestore (Cloud)                          Room (Local)
+---------------------+                   +---------------------+
| songs (collection)  | ---- sync ---->   | songs (table)       |
| artists             | ---- sync ---->   | artists             |
| genres              | ---- sync ---->   | genres              |
| albums              | ---- sync ---->   | albums              |
|                     |                   | downloads (local)   |
| users/{uid}/        |                   +---------------------+
|   profile           | <-- direct read/write --> ViewModel
|   likedSongs/       | <-- direct read/write --> ViewModel
|   recentlyPlayed/   | <-- direct read/write --> ViewModel
|   playbackState/    | <-- direct read/write --> Persistence
+---------------------+

Realtime Database
+---------------------+
| homeFeed/default    | <-- direct read --> HomeFeedRepository
+---------------------+
```

### Sync Strategy: `FirebaseSyncManager`

**One-shot Firestore -> Room sync on startup:**

1. Listens to Firestore collection snapshots (4 parallel flows: songs, artists, genres, albums)
2. Converts Firestore documents -> Room entities
3. Calls `dao.sync()` — upserts all, deletes records not in the latest snapshot
4. Continues listening for real-time updates

**Retry with exponential backoff:**

| Attempt | Delay |
|---|---|
| 0 | 5 seconds |
| 1 | 10 seconds |
| 2 | 20 seconds |
| 3 | 40 seconds |
| 4 | 80 seconds |
| 5 | 160 seconds |
| 6+ | 300 seconds (capped) |

**Permanent errors (give up immediately):** `PERMISSION_DENIED`, `NOT_FOUND`, `UNAUTHENTICATED`

### Security Rules

**Firestore (`firestore.rules`):**
- Catalog collections (`songs`, `genres`, `artists`, `albums`): **read-only** for authenticated users; writes disabled (admin SDK only)
- User data (`users/{uid}/**`): **read/write scoped** to authenticated user's own UID

**Realtime Database (`database.rules.json`):**
- `homeFeed`: **read-only** for authenticated users
- Everything else: **denied**

### Room Database (v4)

| Entity | Table | Key Fields | Source |
|---|---|---|---|
| `SongEntity` | `songs` | mediaId, title, artistId, albumId, genreIds, popularity, URLs | Firestore sync |
| `ArtistEntity` | `artists` | id, name, imageUrl, songIds, popularity | Firestore sync |
| `GenreEntity` | `genres` | id, name, songIds, popularity | Firestore sync |
| `AlbumEntity` | `albums` | id, name, artistId, songIds, popularity, releaseDate | Firestore sync |
| `DownloadEntity` | `downloads` | mediaId, status, filePath | Local only |

**Migration strategy:** Explicit `MIGRATION_3_4` adds the `albums` table via `CREATE TABLE IF NOT EXISTS`. Room's `addMigrations()` applies it automatically on upgrade, preserving all existing data.

### Limitations

| Limitation | Impact | Mitigation |
|---|---|---|
| Queue IPC size | ~200 songs max per Bundle transaction | Warn on exceeding; pagination for large catalogs |
| Genre query uses `LIKE` search | Full table scan for genre filtering | Acceptable for catalogs < 10K; add composite index for larger |
| Download data is device-local | AAOS can't see phone downloads | By design — downloads are storage-bound to device |
| No Firestore composite indexes | May slow queries for large catalogs | Add indexes when catalog exceeds 10K items |

---

## 9️⃣ Performance & Constraints

### Measured / Expected Metrics

| Metric | Value | Notes |
|---|---|---|
| Position polling (mobile) | 250ms | Smooth scrubber animation |
| Position polling (AAOS) | 500ms | Progress bar sufficient fidelity |
| Persistence interval | 30 seconds | Periodic state save while playing |
| Final save timeout | 2 seconds | Blocking save on exit |
| Sync retry initial delay | 5 seconds | Exponential backoff start |
| Sync retry max delay | 300 seconds | 5-minute cap |
| Max queue size (IPC) | ~200 songs | Binder transaction limit |
| Browse tree depth | 3 levels | Root -> Category -> Song |
| Min touch target | 76dp | CTS requirement |
| Scrollbar animation | 150ms | Well under 2s CTS limit |
| Clock update interval | 60 seconds | `CarTopBar` time display |

### Known Bottlenecks

| Bottleneck | Scenario | Mitigation |
|---|---|---|
| Binder transaction size | Queue > 200 songs | JSON serialization minimizes payload; warn on exceeding |
| Full table scan on genre query | `LIKE '%genreId%'` | Acceptable for current catalog size; index if needed |
| Firestore snapshot listener | Large catalog updates | Sync manager processes in batch via `dao.sync()` |
| Cold start sync | First launch with empty Room DB | Loading state in UI; catalog typically < 1000 items |

### Offline Behavior

| Component | Offline Behavior |
|---|---|
| Catalog browsing | Works — reads from Room (previously synced) |
| Playback | Streaming URLs fail; downloaded content would work |
| Like/unlike | Firestore offline cache queues writes; applies on reconnect |
| Profile creation | Firestore offline cache retries; silently fails |
| Recently played logging | Firestore offline cache queues; applies on reconnect |
| Playback state save | Firestore offline cache queues; applies on reconnect |
| Home feed | Realtime Database offline cache serves last known data |

---

## 🔟 Testing & Validation Strategy

### 10.1 Environments

| Environment | Status | Purpose |
|---|---|---|
| AAOS Emulator (`Automotive_Distant_Display_with_Google_Play`) | Available | Full AAOS simulation, CTS validation |
| Desktop Head Unit (DHU) | Not installed | Android Auto projection testing (not primary target) |
| Physical head unit | Not available | Production validation |

### 10.2 Test Coverage

| Layer | Tests | Location |
|---|---|---|
| `MediaBrowseTree` | Root structure, children, search, pagination | `core/playback/src/test/.../MediaBrowseTreeTest.kt` |
| Sync backoff | Exponential delay calculation, permanent error detection | `core/data/src/test/.../FirebaseSyncManagerBackoffTest.kt` |
| Offline repositories | Room-backed reads, sync operations | `core/data/src/test/.../offline/` |
| Entities & converters | Domain <-> Entity mapping, type converters | `core/data/src/test/.../local/` |
| Fake test doubles | `FakeSongDao`, `FakeAlbumDao`, `FakeAlbumRepository`, etc. | `core/data/src/test/.../fake/` |

### 10.3 Scenarios to Validate (Phase 8)

| Scenario | Test Method | Status |
|---|---|---|
| Network loss during playback | Toggle airplane mode on emulator | Pending |
| Audio focus interruption | Play notification sound / start navigation | Pending |
| Process death and restore | Force-stop app, relaunch | Pending |
| Large queue (200+ songs) | Load full catalog into queue | Pending |
| Ignition cycle | Simulate power off/on in emulator | Pending |
| Restriction changes | Switch between parked/driving in emulator | Pending |
| Browse tree from system media center | Open system media app, browse NyasaPlayer | Pending |
| Steering wheel controls | Use emulator media button simulation | Pending |

### 10.4 Known Test Gaps

- No unit tests for `CarUxRestrictionsHandler` (requires Car API mocks)
- No unit tests for `AutomotivePlayerViewModel` (requires MediaController mocks)
- No Compose UI tests for automotive screens
- No integration tests for end-to-end playback flow
- CTS media test suite not yet run (`cts-tradefed run cts -m CtsMediaTestCases`)

---

## 1️⃣1️⃣ Failure Modes & Edge Cases

| Failure | Handling | Recovery |
|---|---|---|
| `MediaController` disconnect | `onControllerConnectionFailed()` -> error state in UI | `CarErrorOverlay` with retry button; retry reconnects controller |
| Firebase sync failure | Exponential backoff (5s -> 300s) | Retries up to 20 times; permanent errors abort immediately |
| Album art loading failure | Coil `AsyncImage` shows placeholder | `SubcomposeAsyncImage` with `ArtistPlaceholder` gradient fallback |
| Playback state corruption | `PlaybackStatePersistence.restore()` catches all exceptions | Returns null; app starts fresh without restoring queue |
| Room migration failure | Explicit `MIGRATION_3_4` with `CREATE TABLE IF NOT EXISTS` | Migration is idempotent; if schema already exists, no-op |
| Google Sign-In failure | Error message displayed in `CarAuthScreen` | User can retry; error text shows status code |
| Like toggle failure | Optimistic UI rollback + error `Snackbar` | Reverts UI to previous state; shows error notification |
| Network loss | `NetworkMonitor` detects via `ConnectivityManager` | `OfflineBanner` displayed; streaming playback fails fast with error |
| Car service unavailable | `CarUxRestrictionsHandler.connect()` try/catch | Falls back to unrestricted state (`limitedContentItems = MAX_VALUE`) |

---

## 1️⃣2️⃣ Known Limitations

| Limitation | Impact | Path Forward |
|---|---|---|
| No voice search | Users can't search by voice through assistant | Implement `onSearch()` result display in UI |
| No multi-zone audio | Rear-seat passengers share driver's playback | Requires `CarAudioManager` zone APIs |
| No head-unit-specific auth | Falls back to Google Sign-In only | Consider QR code pairing from companion phone |
| Large catalogs need pagination | >10K songs may slow queries | Add Firestore composite indexes, implement lazy pagination |
| Download data is device-local | AAOS can't play phone downloads | By design — storage is device-bound |
| No queue management UI | Users can't reorder or remove queue items | Future feature — add `CarQueueScreen` |
| `FirebaseUser` in `AuthRepository` | Leaks Firebase types into domain layer | TODO: Replace with domain `User` type |
| ~~Destructive Room migration~~ | ~~Existing users lose local cache on upgrade~~ | **Resolved** — explicit `MIGRATION_3_4` preserves data on upgrade |
| `minSdk = 29` (not 28) | Excludes oldest AAOS head units | Acceptable — Android 10 is minimum for modern AAOS |

---

## 1️⃣3️⃣ Future Improvements

### Short-Term (Next Release)

- **CTS validation** — Run full test suite on AAOS emulator and DHU
- ~~**Explicit Room migration**~~ — **Done** — `MIGRATION_3_4` preserves data on v3->v4 upgrade
- **Artist detail screen** — Dedicated screen for browsing an artist's albums and songs
- **Album detail screen** — Dedicated screen for album tracklist with shuffle play

### Medium-Term

- **Voice assistant integration** — Surface search results from `MediaBrowseTree.search()` in UI
- **Cross-device queue sync** — Real-time queue synchronization between phone and head unit
- **Playback analytics** — Track listening patterns, skip rates, popular content
- **Browse tree caching** — Cache `MediaBrowseTree` results for faster system media center browsing

### Long-Term

- **Multi-zone audio** — Separate playback streams for driver and passengers via `CarAudioManager`
- **OTA resilience** — Handle system updates gracefully without data loss
- **Android TV reuse** — Leverage `:core:playback` and `:core:data` for a TV app module
- **Offline downloads on AAOS** — Download management specific to head unit storage
- **Adaptive UI** — Support different display sizes (cluster display, rear-seat entertainment)

---

## 1️⃣4️⃣ Lessons Learned

### Architecture

- **`MediaLibraryService` was the right call.** It simplified assistant integration and system media center browsing significantly. The alternative (`MediaSessionService` + separate `CarAppService`) would have doubled the service maintenance burden.

- **Separating ViewModels avoided platform branching.** A shared ViewModel would have accumulated `if (isAutomotive)` checks. Separate ViewModels with a shared base class (`BasePlayerStateCollector`) gave us code reuse where it matters (controller lifecycle) and clean separation where platforms diverge (UI state shape, polling interval, restrictions).

- **AAOS restrictions affect architecture more than expected.** `CarUxRestrictionsHandler` isn't just a UI concern — it flows through the ViewModel into the state model and influences data queries (`.take(maxItems)`). This should be designed upfront, not bolted on.

- **Offline-first is critical for automotive.** Vehicles lose connectivity in tunnels, garages, and rural areas. Syncing Firestore -> Room on startup and reading from Room thereafter ensures the catalog is always available. This pattern is more important for AAOS than mobile.

### Development Process

- **Testing automotive lifecycle is harder than mobile.** The AAOS emulator doesn't perfectly simulate ignition cycles, and `CarUxRestrictionsManager` requires the Car API stubs. Unit testing automotive-specific code requires mocking the `Car` class, which isn't straightforward.

- **Same Firebase project simplifies everything.** Shared UIDs, shared security rules, shared console. The "separate project" option would have doubled operational overhead for no real benefit.

- **Phased implementation worked well.** Breaking the work into 8 phases (extract playback -> upgrade service -> schema changes -> scaffold module -> screens -> wire playback -> Firebase config -> polish) allowed incremental validation. Each phase built on confirmed-working code.

- **Static analysis (Detekt + Lint) caught issues early.** Zero-tolerance `maxIssues: 0` with compose-rules plugin prevented accessibility issues (`ContentDescription` enforcement) and code quality regressions (`MagicNumber`, `ModifierMissing`).

---

## Appendix

### A. Module Dependency Graph

```
settings.gradle.kts includes:
  :app
  :automotive
  :core:common
  :core:data
  :core:playback

Dependency flow (-> means "depends on"):
  :app         -> :core:playback -> :core:data -> :core:common
  :automotive  -> :core:playback -> :core:data -> :core:common
```

### B. Manifest Comparison

| Attribute | `:app` (Mobile) | `:automotive` (AAOS) |
|---|---|---|
| Application class | `.NyasaPlayerApplication` | `.AutomotiveApplication` |
| Entry activity | `.ui.MainActivity` | `.ui.AutomotiveActivity` |
| App category | (default) | `audio` |
| Hardware features | (default) | `automotive` required, `touchscreen` not required |
| Services | `PlaybackService` (in `:core:playback`) | Same (shared) |
| Meta-data | (none) | `com.android.automotive` -> `automotive_app_desc.xml` |

### C. Key Configuration Constants

```kotlin
// Playback
AutoPositionPollIntervalMs = 500L        // AAOS position polling
MobilePositionPollIntervalMs = 250L      // Mobile position polling
PersistenceIntervalMs = 30_000L          // State save interval
FinalSaveTimeoutMs = 2_000L              // Blocking save timeout

// Sync
INITIAL_RETRY_DELAY_MS = 5_000L          // Backoff start
MAX_RETRY_DELAY_MS = 300_000L            // Backoff cap (5 min)
MAX_RETRY_ATTEMPTS = 20                  // Give up after 20 retries

// Content
RecentlyPlayedLimit = 12                 // Max recently played items
PopularLimit = 8                         // Top songs query limit
FeaturedPlaylistsMax = 10               // Browse screen album limit

// UI (CTS)
CarTouchTargetSize = 76.dp              // CTS minimum touch target
CarListArtSize = 80.dp                  // Album art thumbnail
CarMiniPlayerHeight = 112.dp            // Persistent bottom bar
CarCardCornerRadius = 16.dp             // Card rounding
```

### D. Firestore Data Model

```
Firestore
+-- songs/{mediaId}
|   +-- title, subtitle, artistId, artistName
|   +-- albumId, albumName, genreIds[]
|   +-- songUrl, coverUrl, imageUrl, audioUrl
|   +-- durationMs, popularity, isExplicit
|
+-- artists/{artistId}
|   +-- name, imageUrl, songIds[], popularity
|
+-- genres/{genreId}
|   +-- name, songIds[], popularity
|
+-- albums/{albumId}
|   +-- name, artistId, artistName, imageUrl
|   +-- songIds[], popularity, releaseDate
|
+-- users/{uid}
    +-- profile (displayName, email, photoUrl, createdAt)
    +-- likedSongs/{mediaId} (likedAt)
    +-- recentlyPlayed/{mediaId} (playedAt)
    +-- playbackState/current (songId, positionMs, queue, repeatMode)

Realtime Database
+-- homeFeed/default (home feed sections)
```

### E. MediaSession Command Protocol

```
Client (ViewModel)                     Service (PlaybackService)
       |                                        |
       +-- sendCustomCommand(CMD_SET_QUEUE) --> |
       |   Bundle: {songs: JSON, startIndex}    |
       |                                        +-- deserialize songs
       |                                        +-- queueManager.setQueue()
       |                                        +-- player.setMediaItems()
       |                                        +-- player.play()
       |                                        |
       +-- sendCustomCommand(SHUFFLE_PLAY) -->  |
       |   Bundle: {songs: JSON}                |
       |                                        +-- queueManager.setQueueShuffled()
       |                                        +-- player.setMediaItems()
       |                                        +-- player.play()
       |                                        |
       +-- sendCustomCommand(TOGGLE_SHUFFLE) -> |
       |                                        +-- queueManager.toggleShuffle()
       |                                        +-- player.setMediaItems() (reorder)
       |                                        |
       <-- Player.Listener events --------------|
           (onMediaItemTransition,              |
            onPlaybackStateChanged,             |
            onPlayerError)                      |
```

---

*This report documents the NyasaPlayer AAOS implementation as of March 2026. For the latest architecture decisions and phase status, see `docs/AAOS_ARCHITECTURE.md`.*

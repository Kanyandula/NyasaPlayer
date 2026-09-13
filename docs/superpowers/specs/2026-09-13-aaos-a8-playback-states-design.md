# AAOS A8 — Playback error, no connection, loading: design

The last phase of the PRD's original eight. The contract gives it four screens; this design builds on
the one that exists, turns one into a behaviour, records one as already satisfied, and moves the
fourth to its own phase.

## What exists today

**Verified in code on 2026-09-13, not taken from the contract**, which describes all four as unbuilt:

| # | Contract row | What the code already has |
|---|---|---|
| 19 | `CarPlaybackErrorOverlay` — message, Try again, Skip next, Dismiss | `CarErrorOverlay(error, onDismiss, onRetry, modifier)` in `auto/ui/components/`, drawn by `AutomotiveApp` whenever `playerState.error != null`. Dismiss and **Retry** (the contract calls it Try again; the label stays), no Skip next. Its buttons are hand-rolled `Box`es with `padding(vertical = 20.dp)` and do not apply `carTouchTarget()`. |
| 16 | `CarNoConnectionScreen` — offline state, Retry, Browse Downloads | `OfflineBanner(isOffline = playerState.isOffline)`; `AutomotivePlayerViewModel.observeNetworkState()` feeds `AutomotiveUiState.isOffline` from `NetworkMonitor`. No screen, and nothing uses `isOffline` except the banner. |
| 18 | `CarLoadingScreen` — shared static skeletons | Home (`CarRowSkeleton`), Browse (`BrowseSkeleton`) and Library (`LibrarySkeleton`) each render a skeleton inside the chrome while `isLoading` is true. |
| 15 | `CarDownloadsScreen` | A visibly disabled Downloads row in `CarLibraryScreen`. `SongDownloadManager` lives in `:app`, which `:automotive` does not depend on (`CarDetailScreen` KDoc, D12). |

Two behaviours matter more than the screens:

- **The catalog works offline.** Genres and albums come from Room; Firestore serves its cache. Lists
  mostly load with no network. What fails is **streaming**.
- **Offline streaming on the car fails slowly.** A play attempt with no network queues the song,
  ExoPlayer buffers until it gives up, and only then does `onPlaybackError` raise the overlay titled
  "No Connection". Mobile fails fast: `PlayerViewModel` checks `isOnline` before streaming, in three
  places.

**Retry already retries.** The car's `onRetry` is `clearError()` then `togglePlayPause()`. After a
player error `isPlaying` is false, so the toggle sends `play()`. In Media3 1.5.1 a controller's
`play()` reaches `MediaSessionImpl.handleMediaControllerPlayRequest`, which calls
`Util.handlePlayButtonAction`; that calls `prepare()` when the player is `STATE_IDLE`. The failed item
is re-prepared, not merely resumed. (Verified in `media3-session-1.5.1-sources.jar` and
`media3-common-1.5.1-sources.jar`.)

## Decisions taken

Each was put to the user and answered; none is open.

1. **Downloads leave A8.** Files are per device and the car cannot start a download, so a car
   Downloads screen would always be empty. Car downloads become **A9**, which moves
   `SongDownloadManager` into a shared module, moves local-URI resolution into shared code — including for restored sessions, which today are
   queued with their streaming URLs even when downloaded — adds
   parked-only download actions, builds screen 15, and enables the Library row.
2. **NoConnection is a behaviour, not a screen.** The banner stays. A play attempt while offline
   raises the existing overlay *immediately*. No full-screen state: offline-first lists still work,
   and a blocker would hide them.
3. **The offline rule is shared.** One pure predicate in `:core:playback`, called by both ViewModels,
   rather than a car-only copy or a service-side refusal.

## Scope

### 1. The rule — `:core:playback`

A new file holding one extension:

```kotlin
/** Can start right now: the network is up, or the file is already on this device. */
fun Song.isPlayableNow(isOnline: Boolean): Boolean =
    isOnline || resolvedAudioUrl.startsWith("file:")
```

- `resolvedAudioUrl` (`audioUrl.ifBlank { songUrl }`, in `core/common/.../models/Song.kt`) is the field
  `SongMediaItemMapper` streams from, so the rule reads what the player reads.
- `file:` and not `file://`: mobile's `resolveSongUri()` builds the local URI with `File.toURI()`,
  which produces `file:/data/...`.
- It knows nothing about downloads. A song is local because a caller rewrote its URI, which mobile
  already does and A9 will do on the car.

### 2. The car — `AutomotivePlayerViewModel`

Three guards, all raising `PlayerError` with the wording `onPlaybackError` already uses for a
network failure: title **"No Connection"**, message **"Check your vehicle's internet connection"**.

| Guard | Condition | `isRetryable` |
|---|---|---|
| `playSong(songs, song)`, before `setQueue` | the tapped `song` is not `isPlayableNow` — it is what starts, and a mixed list must not wave it through | **false** |
| `shufflePlay(songs)`, before the transport call | no song in `songs` is `isPlayableNow` | **false** |
| `togglePlayPause()`, when about to play | the current song (`uiState.playback.currentSong`) is not `isPlayableNow` | true |
| the snapshot observer in `observePlaybackSnapshot()` | `snapshot.isBuffering && snapshot.playWhenReady && isOffline` — pause through the transport, then raise | true |

**Why the buffering guard needs `playWhenReady`.** Buffering alone is not a play attempt. A restore
runs `applyQueueToPlayer()` — which calls `exoPlayer.prepare()` — and only then sets
`playWhenReady = false` (`PlaybackService`'s restore handler), so a restored-but-paused session
buffers. Offline, a guard on `isBuffering` alone would put an overlay in front of a driver who has
pressed nothing, which T3's D-T3.5 rules out. `isPlaying` cannot stand in: it is false *while*
buffering. So `PlaybackSnapshot` gains `playWhenReady: Boolean = false`, which the collector keeps
current from `Player.Listener.onPlayWhenReadyChanged`, sets in `syncSnapshotFromPlayer`, and sets to
`false` in `applyRestored`. The listener is an object member, so `BasePlayerStateCollector`'s
detekt function ceiling is not touched.

**Why a refused play offers no Retry.** The refused song was never queued. Retry is
`togglePlayPause()`, which would act on whatever *else* is current, and `PlayerError.isRetryable`'s
KDoc forbids exactly that: *"must not offer a Retry that pauses/resumes someone else's queue."* The
driver taps the song again.

**`togglePlayPause()` must copy mobile's structure, not just gain a check.** Today the car's version
is one line, `stateCollector.transport.togglePlayPause()`, and never learns whether it is about to
play. To guard only the play half it has to read `transport.isPlaying()` first, as mobile does, and
keep mobile's `null` branch: when `isPlaying()` is `null` it calls `transport.togglePlayPause()` anyway
and returns, because a query cannot trigger a rebuild and the toggle can (T14, D65). Dropping that
branch would make play the one car control that gives up on a lost controller instead of rebuilding
it.

`isOffline` is already in `AutomotiveUiState`; no new dependency.

### 3. Mobile — `PlayerViewModel`

Two of its three `!isOnline && !isDownloaded` checks become `isPlayableNow` on the **resolved** song:

- `playSong`: `resolveSongUri(song).isPlayableNow(isOnline)`.
- `shufflePlay`: `resolvedSongs.any { it.isPlayableNow(isOnline) }` replaces the `zip` comparison.
- `togglePlayPause` **keeps its current check**, `downloadManager.getLocalFileUri(currentMediaId)`.
  It cannot switch to the rule without changing behaviour: a restored session's songs come from
  `PlaybackStatePersistence` via `songRepository.getSongsByIds()` and reach the snapshot through
  `applyRestored` without `resolveSongUri()`, so a restored song that *is* downloaded still carries
  its `https:` URL. Today's mediaId check lets it play; `isPlayableNow` would refuse it.

Behaviour, wording and `handleOfflineBuffering()` stay as they are. **This is a refactor with no
test net:** `:app`'s only unit test is `SongMediaItemMapperTest`, so the mobile change is verified on
a device (see Testing).

### 4. The overlay — `CarErrorOverlay`

- Actions move onto the existing **`CarPillButton`** (`CarControls.kt`), which applies
  `carTouchTarget()` at `CarPillButtonHeight` (76dp) and already carries the gold-label contrast rule.
  Retry is `filled = true`; Dismiss and Skip next are `filled = false`.
- New parameter `onSkipNext: (() -> Unit)?`; `null` hides the button.
- `AutomotiveApp` passes it only when `playerState.playback.hasNext && !playerState.isOffline`.
  Offline, skipping raises the next track's error.
- Skip next calls a new `AutomotivePlayerViewModel` function that clears the error, skips, then plays
  through the transport — see Open items for why *then plays*.

### 5. Documents

- `docs/AAOS_PRD.md`: §6.3 rows 15, 16, 18, 19; §9's A8 row; a new **A9 — car downloads** row.
- `docs/AAOS_SCREEN_CONTRACT.md`: rows 15, 16, 18, 19 to match.
- `docs/aaos-DESIGN.md`: **D71**, below.

## Out of scope

- **Car downloads** — A9.
- **A full-screen NoConnection state** — decision 2.
- **Playback started outside the custom UI.** The OEM media template and Assistant reach
  `PlaybackService` directly and keep today's slow failure. Only a service-side refusal would cover
  them; that option was not chosen. Recorded as a known gap in D71.
- **A parked-only shimmer** for loading. The contract calls it optional; it stays unbuilt.

## Testing

**JVM, `:core:playback`** — `isPlayableNow`:

- online with an `https:` URL → true
- offline with an `https:` URL → false
- offline with a `file:/` URL → true
- offline, blank `audioUrl`, `file:/` `songUrl` → true (the `resolvedAudioUrl` fallback)

**JVM, `:core:playback`** — `playWhenReady` in the snapshot, on the real-`MediaSession` harness
(`ReconnectingCollectorTest` / `ConnectedTransportTest`, D64): it follows `play()` and `pause()`, and
`applyRestored` leaves it false.

**Compose, `:automotive` debug variant** — `CarErrorOverlay`:

- Skip next is shown if and only if `onSkipNext` is non-null
- Retry is shown if and only if `error.isRetryable`

**AAOS emulator, `oem` flavor** (`svc wifi disable` / `svc data disable` for offline):

- offline, tap a song → overlay at once, no Retry, nothing queued
- offline, relaunch onto a restored session → **no** overlay until play is pressed
- network lost mid-track → buffering → overlay with Retry; network back, Retry → plays
- a non-network playback error with a next track → Skip next plays the next track
- driving (`inject-vhal-event`): overlay still dismissible, Skip next and Retry still work

**Mobile device regression** — the refactor's only check:

- offline, tap a streamed song → refused with today's wording
- offline, tap a downloaded song → plays
- offline, shuffle a list mixing both → starts
- Worth running in the same session as the T14 mobile pass that `docs/T14_VERIFICATION.md` still owes.

## Open items to settle during implementation

1. **Does a seek leave an errored player idle?** Standard ExoPlayer behaviour is that seeking does not
   leave `STATE_IDLE`, which is why Skip next plays after it. Not verified in this repo; the plan
   checks it on the emulator before relying on it. If a seek alone does resume, the extra `play()` is
   harmless.
2. **Contrast.** Read the measured-contrast table in `aaos-DESIGN.md` before the device pass, and
   sample the outlined pills on the overlay's card.
3. **Mobile's `handleOfflineBuffering()` has the same restore trigger.** It checks
   `isBuffering && !isOnline`, so an offline mobile restore likely raises its "Connection lost"
   error too. Not device-verified. With `playWhenReady` on the snapshot, mobile can adopt the same
   condition in one line; whether to do that here or file it is for the review of this spec.
4. **A non-network playback error on demand.** The emulator pass needs one for Skip next. How to
   produce it (a test catalog entry with a bad URL, or an unsupported file) is the plan's to decide.

## Decisions to record

**D71 — A8 is three states, not four screens.** NoConnection is a behaviour: offline play fails before
the attempt, with the existing overlay, because offline-first lists still work and a full-screen
blocker would hide them. Loading is satisfied by the per-screen skeletons. A refused play offers no
Retry, because the refused song was never queued. Retry needs no change: Media3 re-prepares an
idle player on a controller's `play()`. Car downloads move to A9. Known gap: playback started from the
OEM template or Assistant still fails slowly offline.

## Risks

- **The mobile refactor ships without unit coverage.** Mitigated by keeping it a like-for-like
  substitution and by the device regression; a `PlayerViewModel` test harness is not in scope.
- **Guards in two ViewModels can drift.** The rule is shared; the three call sites per surface are
  not. A9 is the natural moment to fold them into the collector if it grows room under detekt's
  function ceiling.

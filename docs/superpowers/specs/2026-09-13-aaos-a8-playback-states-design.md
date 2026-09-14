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
3. **The offline rule is shared.** One pure predicate in `:core:playback`, rather than a car-only
   copy or a service-side refusal. The car calls it in A8; mobile adopts it in T28.
4. **A8 leaves `:app` untouched.** Surface differences are kept by module, not by `isMobileApp` /
   `isAaosApp` checks in shared code: a surface that should not get a behaviour yet simply does not
   call it. Everything A8 adds to `:core:playback` is additive — a new function, and a snapshot field
   with a default — so mobile's behaviour cannot change. Mobile's adoption, and the restore trigger in
   its `handleOfflineBuffering()`, are **T28**, verified on a phone, because `PlayerViewModel` has no
   unit tests.

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
| the stall guard, confirmed after 1.5 s | `isStreamStalledOffline` — offline, buffering, trying to play, and the current song is not `isPlayableNow` | true |

**`togglePlayPause()` is not guarded.** It stays the pre-A8 one-liner and plays whatever is already
buffered offline; the stall guard is what pauses it if that turns out to be nothing. Guarding the
toggle too would refuse a resume that already has audio to play.

**Why the stall guard needs `playWhenReady`, and needs confirming.** Buffering alone is not a play
attempt. A restore runs `applyQueueToPlayer()` — which calls `exoPlayer.prepare()` — and only
then sets `playWhenReady = false` (`PlaybackService`'s restore handler), so a restored-but-paused
session buffers. Offline, a guard on `isBuffering` alone would put an overlay in front of a driver
who has pressed nothing, which T3's D-T3.5 rules out. `isPlaying` cannot stand in: it is false
*while* buffering. So `PlaybackSnapshot` gains `playWhenReady: Boolean = false`, which the
collector keeps current from `Player.Listener.onPlayWhenReadyChanged`, sets in
`syncSnapshotFromPlayer`, and sets to `false` in `applyRestored`. The listener is an object
member, so `BasePlayerStateCollector`'s detekt function ceiling is not touched. Media3 also masks
the controller to `STATE_BUFFERING` on every seek from a non-idle player, so the guard confirms
`isStreamStalledOffline` again after 1.5 s before pausing — a seek inside audio already buffered
must keep playing.

**Why a refused play offers no Retry.** The refused song was never queued. Retry is
`togglePlayPause()`, which would act on whatever *else* is current, and `PlayerError.isRetryable`'s
KDoc forbids exactly that: *"must not offer a Retry that pauses/resumes someone else's queue."* The
driver taps the song again.

`isOffline` is already in `AutomotiveUiState`; no new dependency.

### 3. Mobile — untouched

No file in `:app` changes. `PlayerViewModel` keeps its own three offline checks and
`handleOfflineBuffering()` exactly as they are; T28 moves it onto the shared rule. The one thing T28
must not do, recorded here because the Codex review found it: switch `togglePlayPause` to
`isPlayableNow`. Restored songs reach the snapshot through `applyRestored` without `resolveSongUri()`,
so a restored song that *is* downloaded still carries its `https:` URL, and the rule would refuse what
today's `getLocalFileUri(currentMediaId)` check allows.

### 4. The overlay — `CarErrorOverlay`

- Actions move onto the existing **`CarPillButton`** (`CarControls.kt`), which applies
  `carTouchTarget()` at `CarPillButtonHeight` (76dp) and already carries the gold-label contrast rule.
  Retry is `filled = true`; Dismiss and Skip next are `filled = false`.
- New parameter `onSkipNext: (() -> Unit)?`; `null` hides the button.
- It shows only for an error about the current item — the `isRetryable` rule Retry already uses — so a
  failed like or an empty genre never offers a transport action.
- `AutomotiveApp` passes it only when `playback.hasNext && playback.queueSize > 1 && !isOffline`.
  `hasNext` alone is true under repeat-all for a queue of one, which would replay the failed item;
  offline, skipping raises the next track's error.
- `playSong` and `shufflePlay` return whether playback started, and `AutomotiveApp`'s six play call
  sites open the full player only on `true`. Today they open it unconditionally, which would put the
  overlay over an empty full player.
- Skip next calls a new `AutomotivePlayerViewModel` function that clears the error, skips, then plays
  through the transport — see Open items for why *then plays*.

### 5. Documents

- `docs/AAOS_PRD.md`: §6.3 rows 15, 16, 18, 19; §9's A8 row; a new **A9 — car downloads** row.
- `docs/AAOS_SCREEN_CONTRACT.md`: rows 15, 16, 18, 19 to match.
- `docs/aaos-DESIGN.md`: **D71**, below.

## Out of scope

- **Car downloads** — A9.
- **Mobile** — T28: adopting the rule, and the restore trigger in `handleOfflineBuffering()`.
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

**AAOS emulator, `oem` flavor.** Not `svc wifi disable` / `svc data disable`: on this emulator they
took down `car_service`, `CarLauncher` and `audioserver` (recorded in the A4-era emulator notes). The
plan establishes an offline method first and checks the car stack survives it:

- offline, tap a song → overlay at once, no Retry, nothing queued
- offline, relaunch onto a restored session → **no** overlay until play is pressed
- network lost mid-track → buffering → overlay with Retry; network back, Retry → plays
- a non-network playback error with a next track → Skip next plays the next track
- driving (`inject-vhal-event`): overlay still dismissible, Skip next and Retry still work

**`:app`** — `./gradlew :app:testDebugUnitTest :app:assembleDebug` stays green. Nothing in `:app`
changes, so there is no mobile device pass in A8.

## Open items to settle during implementation

1. **Does a seek leave an errored player idle?** Standard ExoPlayer behaviour is that seeking does not
   leave `STATE_IDLE`, which is why Skip next plays after it. Not verified in this repo; the plan
   checks it on the emulator before relying on it. If a seek alone does resume, the extra `play()` is
   harmless.
2. **Contrast.** Read the measured-contrast table in `aaos-DESIGN.md` before the device pass, and
   sample the outlined pills on the overlay's card.
3. **A non-network playback error on demand.** The emulator pass needs one for Skip next. How to
   produce it (a test catalog entry with a bad URL, or an unsupported file) is the plan's to decide.

## Decisions to record

**D71 — A8 is three states, not four screens.** NoConnection is a behaviour: offline play fails before
the attempt, with the existing overlay, because offline-first lists still work and a full-screen
blocker would hide them. Loading is satisfied by the per-screen skeletons. A refused play offers no
Retry, because the refused song was never queued. Retry needs no change: Media3 re-prepares an
idle player on a controller's `play()`. Car downloads move to A9. A8 leaves `:app` untouched — surfaces differ by module, not by
`isMobileApp`-style checks in shared code — and mobile adopts the rule in T28. Known gap: playback started from the
OEM template or Assistant still fails slowly offline.

## Risks

- **The two surfaces disagree until T28 lands.** Mobile keeps its inline checks and its restore
  trigger; the car uses the rule. That is the price of not touching `:app` here, and T28 is filed so it
  is not permanent.
- **Guards in two ViewModels can drift.** The rule is shared; the three call sites per surface are
  not. A9 is the natural moment to fold them into the collector if it grows room under detekt's
  function ceiling.

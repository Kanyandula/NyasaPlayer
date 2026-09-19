# T28 — mobile uses the shared offline rule

- **Date:** 2026-09-19
- **Ticket:** `docs/tickets/T28-mobile-offline-rule.md`
- **Depends on:** A8 (`Song.isPlayableNow`, `PlaybackSnapshot.isStreamStalledOffline`), A9 (restore
  resolves local URIs)
- **Modules:** `:app` only
- **Verification:** `./gradlew :app:testDebugUnitTest :app:assembleDebug test detekt :app:lintDebug`,
  then a phone pass on `Pixel_9_Pro_Fold_API_35`

## Why

`:core:playback` owns the offline rule for both surfaces:

```kotlin
fun Song.isPlayableNow(isOnline: Boolean): Boolean = isOnline || resolvedAudioUrl.startsWith("file:")

fun PlaybackSnapshot.isStreamStalledOffline(isOnline: Boolean): Boolean =
    !isOnline && isBuffering && playWhenReady && currentSong?.isPlayableNow(isOnline = false) != true
```

A8 gave the car those and deliberately left `:app` alone, so mobile still decides the same question
four times in `PlayerViewModel`, each time by hand. Two surfaces, two rules, one of them written
out four times: the drift is the defect, and it has already produced one behavioural gap.

**The gap.** `handleOfflineBuffering` pauses and shows "Connection lost" whenever the player
buffers while offline. It has no local-file exemption, so a *downloaded* song that buffers offline —
a seek into an unbuffered part of a local file will do it — is paused and told the connection is
gone. The shared `isStreamStalledOffline` exempts local files by construction.

## What the probe found

The ticket predicted a second bug: an offline restore buffers (`applyQueueToPlayer` calls
`prepare()` before `playWhenReady = false`), so the guard would fire and show an error to someone
who has pressed nothing. It says to reproduce that on a phone before changing anything.

Probed on 2026-09-19, `Pixel_9_Pro_Fold_API_35`, the app paused mid-stream, then airplane mode,
`am force-stop`, relaunch:

- restore completed offline from Firestore's cache, about 40 s in — `state=ERROR(7), position=93687`,
  the position the pause left;
- the mini player showed the restored track, paused, progress bar purple, **no error**;
- the expanded player, opened straight after, showed **no error banner**.

So the predicted error does not appear on this build. The `playWhenReady` clause still goes in — it
is the car's rule and it is correct — but this spec claims it as *consistency plus the local-file
exemption*, not as a fix for an observed bug.

## What changes

All four decisions move onto the shared rule. Three are the ticket's; the fourth is explained below.

| Call site | Today | After |
|---|---|---|
| `playSong` | `downloadManager.getLocalFileUri(song.mediaId) != null`, then `!isOnline && !isDownloaded` | `!downloadManager.resolveLocalUri(song).isPlayableNow(isOnline)` |
| `shufflePlay` | `isOnline \|\| resolvedSongs.zip(songs).any { (r, o) -> r.audioUrl != o.audioUrl }` | `resolvedSongs.none { it.isPlayableNow(isOnline) }` |
| `togglePlayPause` | `getLocalFileUri(currentMediaId) != null`, then `!isOnline && !isDownloaded` | `!isOnline && (current == null \|\| !current.isPlayableNow(isOnline))` |
| `handleOfflineBuffering(isBuffering)` | `isBuffering && !isOnline` | `snapshot.isStreamStalledOffline(isOnline)` — takes the snapshot, not a Boolean |

`shufflePlay`'s `zip` comparison is worth naming: it asks "did resolution change any URL", a proxy
for "is anything local" that misreads two whole classes of song — one whose catalogue `audioUrl` is
already the local URI (resolution changes nothing, so it reads as not-downloaded), and one whose
`audioUrl` is blank while `songUrl` is `file:` (the proxy never looks at `songUrl`, but
`resolvedAudioUrl` falls back to it). `none { it.isPlayableNow(isOnline) }` asks the question
directly and is right in both.

### Why `togglePlayPause` is now in scope

The ticket excludes it, and gives a reason: restored songs reach the snapshot through
`applyRestored` without local-URI resolution, so a restored downloaded song still carries its
`https:` URL, and `isPlayableNow` would refuse what today's check allows.

**That reason is obsolete.** A9 moved resolution into `PlaybackStatePersistence.restore()`
(`PlaybackStatePersistence.kt:99`), with a comment saying so: "Both surfaces restore through this one
function (A9)." A restored downloaded song now carries a `file:` URL, and `applyRestored` puts that
same song into the snapshot, so the case the ticket was protecting against is gone.

Leaving it out would leave the one hand-rolled copy T28 exists to remove.

**The check changes what it asks, so every queue path was traced.** Today's test asks the download
repository by `mediaId` — resolution-independent. `isPlayableNow` reads the song's URL, so the two
agree only where the queue holds resolved songs. Mobile's queue has exactly three app-initiated
entry points and all three resolve: `playSong` (`PlayerViewModel.kt:191-192`), `shufflePlay`
(`:210`) and restore (`PlaybackStatePersistence.kt:99`). The overflow sheet's "Add to queue",
"Play next" and "Start mix" enqueue nothing at all — they are unwired in
`SongOverflowSheet.kt` (the menu item takes a defaulted `onClick: () -> Unit = {}`, the quick
actions hardcode `.clickable { }`), so there is no fourth path to worry about.

`currentSong` is not only written by those three, though: every media transition rebuilds it from
the live `MediaItem` (`BasePlayerStateCollector.kt:140`, `:235`). That round-trip preserves the
URL — `resolveLocalUri` rewrites `audioUrl` and `songUrl`, `toMediaItem` writes both into extras,
`toSong` reads them back — so skipping through a queue that *was* resolved stays resolved. Skip is
safe because the queue was, not on its own account.

One path does queue unresolved songs, and it is not mobile's UI:
`PlaybackService.onAddMediaItems` (`PlaybackService.kt:163-182`) maps catalogue songs straight to
`MediaItem` with no `resolveLocalUri`. It serves external controllers — Assistant, Bluetooth, system
media resumption — and the service is exported on both surfaces. A downloaded song arriving that way
carries its `https:` URI, so it **cannot play offline today either**: the player has a remote URI and
fails. Today's check merely lets the attempt start and end in `Source error`; the shared rule refuses
it up front with "Can't stream while offline". That is a better failure, not a regression — but it is
a behaviour change on that path, and it is recorded here rather than discovered later.

The underlying bug — `onAddMediaItems` ignoring downloads — is real, affects both surfaces, and
lives in `:core:playback`, outside this ticket's `:app` scope. It goes to `docs/BACKLOG.md`.

### Behaviour that must not change

- The three error messages stay exactly as they are: "This song isn't available offline"
  (`showOfflineError`), "Can't stream while offline. Download songs for offline playback."
  (`togglePlayPause`), "Connection lost. Download songs for offline playback."
  (`handleOfflineBuffering`). T28 changes decisions, not words.
- `togglePlayPause` keeps reading the live player for `isPlaying` before anything else (T13/T14);
  only its offline branch changes.
- A null `currentSong` refuses **only while offline**, exactly as today. The tempting one-liner
  `currentSong?.isPlayableNow(isOnline) != true` is wrong: for a null song it evaluates to `true`
  whatever the network is doing, so an online tap with no current song would be refused with an
  offline error. The `!isOnline` gate stays explicit for that reason.

## The pure decision, and what the tests can reach

`PlayerViewModel` takes six collaborators. Two are interfaces with fakes already in the repo —
`UserRepository` and `AuthRepository` (faked in `MediaBrowseTreeTest.kt`). The other four are final
classes that cannot be faked by subclassing: `ControllerConnection`, `PlaybackStatePersistence`,
`NetworkMonitor` and `SongDownloadManager`. Two of those four are the blockers —
`ControllerConnection` needs a real `SessionToken` and `NetworkMonitor` needs Robolectric's
connectivity shadows. `:app` has no Robolectric in its test dependencies, and the
MediaSession-over-fake-player pattern that solves this is a private class inside `:core:playback`'s
`ConnectedTransportTest.kt`, which `:app` cannot see. A ViewModel-level harness is a ticket of its
own (recorded in `docs/BACKLOG.md`).

So the decision comes out of the ViewModel and is tested where it can be reached:

```kotlin
// app/src/main/java/com/example/nyasaplayer/player/OfflinePlaybackGate.kt
package com.example.nyasaplayer.player

/**
 * What mobile is allowed to start right now, decided by `:core:playback`'s shared rule (T28).
 *
 * Extracted from `PlayerViewModel` only so it can be tested: the ViewModel's six collaborators are
 * final `@Singleton` classes, so nothing below it is reachable from a plain JVM test.
 */
internal object OfflinePlaybackGate {

    /** A single resolved song the caller asked to play. */
    fun refuses(resolved: Song, isOnline: Boolean): Boolean = !resolved.isPlayableNow(isOnline)

    /** A resolved queue: one playable song is enough to start. */
    fun refuses(resolved: List<Song>, isOnline: Boolean): Boolean =
        resolved.none { it.isPlayableNow(isOnline) }
}
```

`PlayerViewModel` calls `OfflinePlaybackGate.refuses(...)` at the three start sites:

```kotlin
// playSong
if (OfflinePlaybackGate.refuses(resolvedSong, isOnline)) { showOfflineError(song); return }

// shufflePlay
if (OfflinePlaybackGate.refuses(resolvedSongs, isOnline)) { showOfflineError(songs.first()); return }

// togglePlayPause — the !isOnline gate stays: a null song must not refuse an online tap
val current = _uiState.value.currentSong
if (!isOnline && (current == null || OfflinePlaybackGate.refuses(current, isOnline))) {
    /* today's error */
    return
}
```

`playSong` resolves the tapped song before the check rather than after, so the single resolve at
the top serves both the gate and the queue it builds. The buffering guard calls
`snapshot.isStreamStalledOffline(isOnline)` directly, because that shared function is already the
whole decision and wrapping it would add nothing.

**What the unit tests prove:** the rule refuses and allows the right things.
**What they do not prove:** that `PlayerViewModel` calls the gate, or that the UI shows the error.
The phone pass covers that, and this spec says so rather than implying the tests are sufficient.

### Tests — `app/src/test/java/com/example/nyasaplayer/player/OfflinePlaybackGateTest.kt`

Plain JUnit, no Robolectric, no new dependencies. One case per *decision* behind the acceptance
criteria — the criteria themselves are device checks, because they are about what the ViewModel and
the UI then do:

| Case | Expectation |
|---|---|
| offline, streamed song (`https:`) | refused |
| offline, downloaded song (`file:`) | allowed |
| online, streamed song | allowed |
| offline, list mixing one `file:` and three `https:` | allowed |
| offline, list of only `https:` | refused |
| offline, empty list | refused |
| offline, song with a blank `audioUrl` and a `file:` `songUrl` | allowed — `resolvedAudioUrl` falls back, which the old `zip` proxy never saw |
| offline, song whose catalogue `audioUrl` is already the local URI | allowed — resolution changes nothing, so the old proxy read it as not downloaded |

The stall rule keeps its existing coverage in `:core:playback`'s `OfflinePlaybackTest`; T28 adds no
cases there, because it introduces no new behaviour in that function.

## Acceptance criteria

From the ticket, unchanged, all four verified on the phone:

1. Offline, tapping a streamed song refuses with today's wording.
2. Offline, tapping a downloaded song plays it.
3. Offline, shuffling a list that mixes downloaded and streamed songs starts playback.
4. Offline, relaunching onto a restored session shows no error until play is pressed.

Plus the gap this spec adds:

5. Offline, a downloaded song that buffers is not paused and not shown "Connection lost".

Criterion 4 already passes before the change — the probe above. It is kept as a regression check:
adding `playWhenReady` must not make it start failing.

## Out of scope

- A `PlayerViewModel` test harness (see above) — backlog.
- Surface checks such as `isMobileApp` in shared code (D71).
- The silent offline-download refusal found on 2026-09-19 (`docs/T29_VERIFICATION.md`). It is an
  offline-behaviour gap on the same screen, which makes it tempting; it is a different code path
  (`SongDownloadManager`, not the player) and belongs to its own backlog line.

## Risks

- **No net under `:app`.** Its only test today is `SongMediaItemMapperTest`, and the gate's tests
  do not cover the wiring. Every criterion above is therefore a device check, and the phone pass is
  the gate, not a formality.
- **`resolveLocalUri` is called once more per `playSong`** than before (it already resolved the whole
  list on the next line). Negligible: it is a map lookup in `DownloadRepository`.
- **The buffering guard's signature changes** from `(isBuffering: Boolean)` to `(snapshot)`. One
  caller, in the same file.

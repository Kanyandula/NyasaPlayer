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
| `togglePlayPause` | `getLocalFileUri(currentMediaId) != null`, then `!isOnline && !isDownloaded` | `!isOnline && currentSong?.isPlayableNow(isOnline = false) != true` |
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
- A null `currentSong` refuses **only while offline**, exactly as today. `?.isPlayableNow(…) != true`
  is `true` for a null song whatever the network is doing, so dropping the `!isOnline` gate would
  refuse an online tap with no current song. Guarded by it, the expression is the same shape
  `isStreamStalledOffline` uses for the same reason.

## Tests: none new, and why

The first draft of this spec put the decision in an `OfflinePlaybackGate` object in `:app` so it
could be unit-tested, on the reasoning that `PlayerViewModel` cannot be constructed in a plain JVM
test. Review killed it, correctly, on two counts:

- **It is a second name for the car's expression.** `AutomotivePlayerViewModel` already writes
  `!resolvedSong.isPlayableNow(isOnline)` and `resolvedSongs.none { it.isPlayableNow(isOnline) }`
  inline. A mobile wrapper at inverted polarity gives one rule two vocabularies — the drift T28
  exists to end.
- **Its tests proved nothing new.** `:core:playback`'s `OfflinePlaybackTest` already covers online
  and offline streamed songs, an offline local file, and the `songUrl` fallback. The rest of the
  proposed cases tested Kotlin's `none`.

So mobile inlines the rule exactly as the car does, and adds no tests. The four call sites are
covered by `OfflinePlaybackTest` for the rule and by the phone pass for the wiring — which is all
the earlier design would have got anyway, since a gate's tests cannot show that `PlayerViewModel`
calls it.

A `PlayerViewModel` harness stays out of scope and in `docs/BACKLOG.md`. Note for whoever picks it
up: `:app` has no Robolectric, but `:core:playback` does and drives a real `MediaController` over
`SimpleBasePlayer` in `PlayWhenReadySnapshotTest` — "the ViewModel is not JVM-testable" is an
assumption to check, not a fact.

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

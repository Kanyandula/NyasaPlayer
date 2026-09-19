# T32 - A downloaded song reads as undownloaded for the first moments after process start

- **Slice:** downloads, offline playback — both surfaces
- **Depends on:** nothing; pre-dates A9, T28 and T31
- **Status:** Fixed 2026-09-19, unit-covered. Device evidence is the T31 car pass; the race itself was only ever reproducible in a test — see Device attempt
- **Verification Command:** `./gradlew :core:data:testDebugUnitTest :core:playback:testDebugUnitTest
  test detekt`, plus a cold-start device pass
- **Design Reference:** `core/data/.../offline/OfflineDownloadRepository.kt`;
  `core/data/.../download/LocalUri.kt`; T31's Outcome
- **Risk Tags:** offline playback, startup race, both surfaces, silent failure
- **Affected Modules:** `:core:data` (the repository), possibly `:core:playback` and the two
  ViewModels depending on the shape chosen

## Problem

`OfflineDownloadRepository` answers "where is this song's file?" from an in-memory map that it
fills **asynchronously in its `init`**:

```kotlin
private val filePathCache = ConcurrentHashMap<String, String>()

init {
    scope.launch {                                  // Dispatchers.IO
        val completed = downloadDao.getAllCompletedOnce()
        completed.forEach { entity -> … filePathCache[entity.mediaId] = entity.filePath }
    }
}

override fun getLocalFilePath(mediaId: String): String? = filePathCache[mediaId]
```

`getLocalFilePath` does not wait for that load. Every reader between process start and the query
landing is told, silently and incorrectly, that nothing is downloaded:

| Reader | What a cold read costs |
|---|---|
| `PlaybackService.onAddMediaItems` → `playableItems` (T31) | The point of T31: a downloaded song is queued with its `https:` URI and will not play offline |
| `PlaybackStatePersistence.restore()` (A9) | A restored downloaded song comes back pointing at the stream |
| `PlayerViewModel.playSong` / `shufflePlay`; `AutomotivePlayerViewModel`'s equivalents (T28) | Offline, the shared rule refuses a song the user *has* downloaded |
| `resolveDownloadState` in mobile's overflow sheet | The sheet offers "Download" for a song already on disk — and it is read inside `remember(song.mediaId)` (`SongOverflowWithDownload.kt:21`), so a cold answer sticks for the life of that composition, not just until the cache warms |

`onAddMediaItems` is the worst of them because it is the coldest entry point in the app: an
external controller — the car's OEM template, Assistant, Bluetooth — can connect and ask for
playback within a second of process start, before the app's own UI has done anything.

Two further sharp edges in the same `init`:

- The load is not retried and its result is not observable, so a failure leaves the cache
  permanently empty for the life of the process — every song reads as undownloaded until restart.
- `scope.launch` has no `try`/`catch` and the scope has no `CoroutineExceptionHandler`, so a DAO
  failure propagates to the default handler rather than being logged and recovered.

**Reproduced, in a test rather than on a device.**
`OfflineDownloadRepositoryTest.getLocalFilePath_whileTheInitialLoadIsStillRunning_saysNotDownloaded`
holds the startup query open with a gated `FakeDownloadDao` and asks the repository for a path
while the load is in flight: it answers null for a song that is downloaded. That test pins the
defect as it stands today — when this ticket is fixed, the assertion flips from `assertNull` to the
path, which is how the fix will prove itself.

An attempt to provoke it on the car failed first; the window is narrower than anything a person can
drive by hand.

## Device attempt — 2026-09-19, `AAOS_AOSP_33_userdebug`

Run at the end of T31's car pass, with three songs downloaded and the network off
(`ping` → `Network is unreachable`). Criterion three below is the one being probed: play a
downloaded song from the OEM template before the path cache can load.

| Attempt | Result |
|---|---|
| Kill the app, then tap play in the template, ×3 | Process never died: `am kill` is refused while the app holds a foreground service, and the pid was unchanged each time — the memory note about comparing pids, not presence, earning its keep again |
| Pause first, then kill, ×3 | Still refused. A connected media browser binds the service, which keeps the process out of `am kill`'s reach |
| `am force-stop`, then play from the template | The template could not start the service at all: `force-stop` puts the package in the stopped state, so an external component cannot bind it. This is a testing artefact, not a product behaviour |
| Leave the template (HOME), kill, reopen the template, play | The process was killed (pid 4891 → 5142), but the media browser rebinds within a second and the fastest path back to a tap was ~9 s — orders of magnitude wider than a one-shot Room query |
| **Full reboot, offline, play from the template as the first action on the device** | **Played from the file.** `state=3`, position advancing, `pid` unchanged from the one the system started at boot |

So the race did not bite on any sequence that can be driven by hand, including the coldest realistic
one. Two things that follow:

- **The severity drops.** The window is real in the code but narrow enough that a person cannot hit
  it; the car media app also connects to media sources at boot, which warms the cache before a
  driver can touch anything.
- **The evidence had to be a test, not a device** — and now is one, in
  `core/data/src/test/.../offline/OfflineDownloadRepositoryTest.kt`. Trying again on a head unit
  would add nothing.

## Scope

- Make the path lookup wait for the initial load rather than racing it, for the callers that can
  wait. `playableItems` and `PlaybackStatePersistence.restore()` are already `suspend`; the two
  ViewModels resolve inside coroutines.
- Decide and document what the non-suspend readers do. `resolveDownloadState` is called from
  composition and cannot suspend; `observeDownload` already exists and is a `Flow`, which is the
  shape a composable wants anyway — and would fix the `remember` staleness in the same move.
- Handle a failed load: log it, and let a later read retry rather than caching emptiness forever.

## Out Of Scope

- Removing the cache. `getLocalFilePath` is called per song in list-shaped paths, so a DAO round
  trip per call is not obviously better; measure before proposing it.
- Any change to when downloads are recorded, or to `resolveLocalUri`'s contract of rewriting both
  URL fields.

## Evidence that exists now

`OfflineDownloadRepositoryTest`, three cases, `runBlocking` because the repository loads on
`Dispatchers.IO` and a virtual-time scheduler does not drive it:

| Case | Asserts |
|---|---|
| `…whileTheInitialLoadIsStillRunning_saysNotDownloaded` | **The defect.** The download row is in the database from the start and the startup query has been asked but not answered; the repository says the song is not downloaded |
| `…onceTheLoadLands_findsTheFile` | Once the query answers, the path is there |
| `…completedRowWithNoPath_staysNull` | The load's one branch: a completed row with a blank path is not cached |

`FakeDownloadDao` (`core/data/src/test/.../fake/`) is the gate: it is constructed **with the rows
already in the database**, `completedLoadStarted` says the startup query has been asked, and
`releaseCompletedLoad()` lets it answer.

**The first case was checked by mutation**, because a test that pins a defect is worthless if it
passes for another reason. With `getLocalFilePath` patched to fall back to
`downloadDao.getByMediaId` on a cache miss, it fails (`AssertionError`, the path is found) while
the other two still pass. An earlier draft of this test seeded nothing, so its null meant "no
download exists" rather than "the load has not landed"; review caught it, and the mutation check is
what proves the replacement is honest.

## Acceptance Criteria

- Given the initial load has not completed, when a suspend caller resolves a downloaded song, then
  it gets the local file rather than the stream.
- Given the initial load fails, when a caller resolves later, then the repository tries again rather
  than answering "not downloaded" for the rest of the process.
- Given a cold boot with no network, when a downloaded song is played immediately from the car's
  OEM template, then it plays from the file.

## Notes

- `markCompleted` writes the cache and `removeDownload` / `removeAllDownloads` clear it, so the
  cache is authoritative once the initial load lands. The defect is the window, not the design.
- Whatever shape is chosen, it should keep one resolution path for both surfaces — T28 spent its
  review budget removing a second way to ask the same question.

## Outcome

`DownloadRepository.awaitDownloadIndex()` is the fix: the index loads once under a mutex, and a
load that threw is **not** remembered as done, so the next caller retries rather than the process
spending its life believing nothing is downloaded. The startup warm-up now goes through the same
function and logs a failure instead of handing an uncaught exception to a scope with no handler —
the second sharp edge this ticket listed.

The two cold paths await it before resolving: `PlaybackStatePersistence.restore()` and
`SongRepository.playableItems` (T31's), both already `suspend`.

**`getLocalFilePath` is unchanged, deliberately.** It is called from composition, where nothing can
suspend, so making it correct would have meant a suspend contract across the interface, the
composable and all four `playSong`/`shufflePlay` entry points — one of which returns `Boolean`
synchronously. Instead mobile's overflow sheet stopped calling it: `rememberDownloadState` observes
the download row, which is immune to the index entirely and also fixes the stale `remember` that
kept a cold answer for the life of a composition.

So the original race test still passes and is meant to: it pins what `getLocalFilePath` does for a
caller that does not await. The ticket's earlier claim that the assertion would flip was wrong, and
the test now says what it actually guards.

### What the tests cover

| Test | Guards |
|---|---|
| `…whileTheInitialLoadIsStillRunning_saysNotDownloaded` | The raw getter still answers from the cache only |
| `awaitDownloadIndex_returnsOnlyOnceTheIndexIsLoaded` | Acceptance criterion 1 |
| `awaitDownloadIndex_afterAFailedLoad_triesAgain` | Acceptance criterion 2 |
| `PlayableItemsTest.downloadedSong_whenTheIndexHasNotLoadedYet_isStillRequestedFromTheFile` | The await at T31's call site |
| `PlaybackStatePersistenceTest.restore_downloadedSong_whenTheIndexHasNotLoadedYet_stillComesBackLocal` | The await on the restore path |
| `DownloadStateTest` (6 cases) | The sheet's mapping, including a `Completed` row whose file is gone |

The last two were each checked by deleting their await and watching them fail; the repository test
by patching the getter to fall back to the DAO. `TestDownloadRepository` grew
`pathsAfterIndexLoads`, which is what makes "index not loaded" distinguishable from "nothing
downloaded" — the distinction the first draft of this ticket's test missed.

### Left racing, on purpose

The four `playSong` / `shufflePlay` entry points resolve without awaiting, because they are
non-suspend — the car's even returns `Boolean` — and a human tap is far slower than the load. Six
call sites in all; recorded in `docs/BACKLOG.md` rather than silently skipped. Awaiting once in
each ViewModel's `init` would narrow the window further without closing it, which is why it was
not done.

### Not done here

Acceptance criterion 3 — cold boot, offline, play from the car template — passed **before** this
fix (`docs/tickets/T31-…` device pass), because the window is too narrow to hit by hand. Re-running
it would prove nothing this change did not already prove in tests.

`./gradlew test detekt :app:lintDebug :automotive:lintOemDebug` — BUILD SUCCESSFUL, **844 tests,
0 failures**, detekt and lint clean.

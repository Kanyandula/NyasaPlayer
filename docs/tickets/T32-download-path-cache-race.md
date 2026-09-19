# T32 - A downloaded song reads as undownloaded for the first moments after process start

- **Slice:** downloads, offline playback — both surfaces
- **Depends on:** nothing; pre-dates A9, T28 and T31
- **Status:** Filed, not specced
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

**Not yet observed on a device.** This is read from the code. T31's owed car pass is the run most
likely to surface it — cold boot, offline, play a downloaded song immediately — and until then the
window's real width is unknown.

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

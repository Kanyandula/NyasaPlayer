# T31 - A downloaded song played from outside the app ignores its download

- **Slice:** playback, downloads — both surfaces
- **Depends on:** A9 (`DownloadRepository.resolveLocalUri`, restore already uses it)
- **Status:** Fixed and unit-covered; the device pass is owed — see Outcome
- **Verification Command:** `./gradlew :core:playback:testDebugUnitTest test detekt`, plus a device
  pass on each surface
- **Design Reference:** `core/data/.../download/LocalUri.kt` (the resolution contract);
  `docs/superpowers/specs/2026-09-19-t28-mobile-offline-rule-design.md` (where this was found)
- **Risk Tags:** offline playback, both surfaces, external controllers, driver-facing on the car
- **Affected Modules:** `:core:playback`

## Problem

`PlaybackService.onAddMediaItems` (`PlaybackService.kt:163-182`) turns requested media ids into
playable items with

```kotlin
val resolved = songRepository.getSongsByIds(ids).map { it.toMediaItem() }
```

and never calls `DownloadRepository.resolveLocalUri`. The songs come from the catalogue, so they
carry their remote `audioUrl`, and `toMediaItem` sets the playback URI from `resolvedAudioUrl`
(`SongMediaItemMapper.kt:53`). A song the user has fully downloaded is therefore queued with its
`https:` URI and fails offline, with the file sitting on the device unused.

Every other path resolves. `playSong` and `shufflePlay` resolve in `PlayerViewModel`
(`PlayerViewModel.kt:191-192`, `:210`), and A9 moved resolution into
`PlaybackStatePersistence.restore()` (`PlaybackStatePersistence.kt:99`) precisely so that "a
downloaded song restored with its streaming URL is one the driver cannot resume offline". This is
the one entry point that was missed.

`onAddMediaItems` is not a corner: the service is exported on both surfaces, and it is where a
controller's play request turns into items — `playFromMediaId` from a browse item, which on the car
is how the OEM media template plays anything at all, and `setMediaItems` from any connected
controller. (Not system media resumption: media3 routes that through `onPlaybackResumption`, which
this app does not override, so resumption is not supported at all.)

Found while speccing T28 (`:app`-only, so it could not fix this). Since T28, the symptom on mobile
is sharper: `togglePlayPause` reads the song's URL, so play on such a track is refused up front with
"Can't stream while offline" rather than attempting a load that fails. Earlier and clearer, but the
wording is now wrong in the other direction — the song *is* downloaded.

## Scope

- Resolve local URIs in `onAddMediaItems`, the same way `restore()` does.
- A unit test in `:core:playback` proving a downloaded song requested by media id comes back with a
  `file:` URI, and an undownloaded one keeps its remote URL.

## Out Of Scope

- `onGetChildren` / `onSearch` browse metadata. Browse items are descriptions, not playback
  requests; the URI that matters is the one `onAddMediaItems` returns.
- Any change to when downloads are recorded or cleaned up.

## Acceptance Criteria

- Given a downloaded song, when an external controller asks for it by media id, then the queued
  item points at the local file.
- Given the device is offline and the song is downloaded, when it is requested that way, then it
  plays.
- Given a song that is not downloaded, then nothing about its handling changes.

## Notes

- `PlaybackService` would need a `DownloadRepository` to do this; it is an interface in
  `:core:data`, already injected into `PlaybackStatePersistence`, so the seam exists.
- The car half is the more visible one: the OEM template is a first-class surface there, and A9
  shipped downloads on the assumption that a downloaded song plays offline from anywhere.

## Outcome

`SongRepository.playableItems(ids, downloads)` in `PlayableItems.kt` does fetch, `resolveLocalUri`,
map. `PlaybackService` injects `DownloadRepository` — the binding was already reachable,
`PlaybackStatePersistence` takes it — and `onAddMediaItems` calls it, so a controller's play request
gets the same resolution the restore path has had since A9.

`MediaBrowseTree.toPlayableItem` also builds items from an unresolved `resolvedAudioUrl`
(`MediaBrowseTree.kt:210`). Harmless today: the template plays by media id, which comes back through
`onAddMediaItems` and is resolved there. Worth knowing before anyone makes browse items directly
playable.

Why a function rather than the one line inline: nothing instantiates `PlaybackService` in a test,
and standing it up needs Robolectric plus Hilt. Lifting the three steps out makes the rule reachable
from `PlayableItemsTest`, which uses the `TestSongRepository` and `TestDownloadRepository` fakes this
module's tests already have (the download fake stopped being file-private; it was not copied).

Four cases: a downloaded song comes back pointing at its file, an undownloaded neighbour keeps its
stream URL, a download whose file has been deleted falls back to the stream, and an id the catalogue
does not know is absent.

`./gradlew test detekt :app:lintDebug :automotive:lintOemDebug` — BUILD SUCCESSFUL, **830 tests,
0 failures**, detekt and lint clean. (Wider than the header's command, which stays as the minimum
for a change to this file.)

### Owed

The device pass. The unit test proves the resolution; it does not prove the plumbing through a real
external controller. The car is the surface that matters — the OEM template plays everything through
`playFromMediaId` — and a pass there needs a test album back in Firestore and a song downloaded on
the car, which is why it was deferred rather than run with this change (owner's call, 2026-09-19).
Until then this is a fix with unit evidence, not a verified one.

**Run it cold.** `DownloadRepository.getLocalFilePath` reads an in-memory cache that
`OfflineDownloadRepository` fills asynchronously in its `init` (`OfflineDownloadRepository.kt:27-36`).
`onAddMediaItems` is the coldest entry point there is — a controller can connect and play within a
second of process start — so a pass that plays a downloaded song *immediately* after a cold boot,
offline, is the one that would catch the resolution silently not applying. That race is not T31's
to fix; it is a backlog line of its own.

# T31 - A downloaded song played from outside the app ignores its download

- **Slice:** playback, downloads — both surfaces
- **Depends on:** A9 (`DownloadRepository.resolveLocalUri`, restore already uses it)
- **Status:** Filed, not specced
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

`onAddMediaItems` is not a corner: the service is exported on both surfaces, so it serves Assistant
("play X"), Bluetooth/AVRCP, Wear, system media resumption, and `playFromMediaId` from a browse
item — which on the car is how the OEM media template plays anything at all.

Found while speccing T28 (`:app`-only, so it could not fix this).

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

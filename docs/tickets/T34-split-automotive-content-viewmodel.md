# T34 - `AutomotiveContentViewModel` has outgrown one class

- **Slice:** car UI architecture
- **Depends on:** nothing; blocks nothing, but every future car-content slice pays for it
- **Status:** Filed 2026-09-19. The file's own note has been asking for this since A6.
- **Verification Command:** `./gradlew :automotive:testDebugUnitTest detekt :automotive:lintOemDebug`
- **Design Reference:** `automotive/.../auto/viewmodel/AutomotiveContentViewModel.kt`;
  `docs/AAOS_PRD.md` §6.3 (screen 15's data source); decision D23
- **Risk Tags:** maintainability, suppression debt, no user-visible behaviour
- **Affected Modules:** `:automotive`

## Problem

`AutomotiveContentViewModel` is 634 lines and **24 functions** against detekt's threshold of 16,
held open by a file-level `@Suppress("TooManyFunctions")` whose own comment says what should happen
instead:

> the next slice to touch it should split it rather than suppress again. See spec D23.

Two slices have touched it since and neither split it. A6 moved search out; A9 added downloads in.
The suppression comment is also now stale — it says 22 functions, and there are 24.

What one class currently owns: genres, albums, playlists, recently-played, liked songs, popular
songs, favourite artists, detail navigation, favourites, **and** the download list with its remove /
remove-all / retry / download commands.

This matters beyond tidiness because **D23 is a project decision — "this project does not answer a
threshold with a suppression"** — and this file is the standing exception to it.

## Scope

- Split along the seam the file already has. The obvious line is **downloads**: `observeDownloads`,
  `removeDownload`, `removeAllDownloads`, `retryDownload`, `downloadSongs` are a self-contained
  group with their own repository dependency and their own screen.
- Whatever comes out must keep one source of truth for the screens that read both — the Library
  screen shows liked songs and downloads together.
- Delete the file-level suppression when the count drops under the threshold. If it cannot be
  deleted, the split did not go far enough.

## Out Of Scope

- Changing any car screen's behaviour, layout, or state shape. This is a move, and the existing
  car tests are the check that it was only a move.
- Touching `AutomotivePlayerViewModel` or `AutomotiveAuthViewModel`.
- The PRD §6.3 wording that names the content VM as screen 15's data source — update the reference
  if the split moves it, but do not renegotiate the requirement.

## Acceptance Criteria

- Given the split has landed, when detekt runs, then it passes **without** a `TooManyFunctions`
  suppression anywhere in the car's viewmodel package.
- Given the split has landed, when the existing `:automotive` tests run, then they pass unchanged
  except for construction sites.
- Screens 15 and the Library screen behave identically before and after — verified on the AAOS
  emulator, not only in unit tests.

## Notes

Recorded in `docs/BACKLOG.md` since A9. This ticket exists because "the next slice will split it"
has now been said three times without a slice doing it.

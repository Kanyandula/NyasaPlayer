# AAOS A6 - Search and Search Results

- **Slice:** A6 (screens 5 and 6)
- **Depends on:** A1 restrictions, A2 chrome, A3/A4 shared content components, A5 merged baseline
- **Status:** Implemented and device-verified on `ek/aaos-a6-t1-search-viewmodel`; review-ready with recorded carve-outs
- **Spec:** `docs/superpowers/specs/2026-08-20-aaos-search-design.md`
- **Plan:** `docs/superpowers/plans/2026-08-20-aaos-a6-search.md`
- **Verification Record:** `docs/AAOS_A6_VERIFICATION.md`
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest :core:data:testDebugUnitTest`
- **Design Reference:** `docs/AAOS_SCREEN_CONTRACT.md` rows 5-6 · `docs/AAOS_PRD.md` §7 rows 5-6
  · `docs/aaos-DESIGN.md` D31-D40 · `docs/aaos-app.html` (prototype)
- **Risk Tags:** driver distraction compliance · text entry · search state split · result-scope
  drift
- **Affected Modules:** `:automotive`; `:core:data` for album-name song search parity

## Problem

The system-bar search control is still disabled in the custom AAOS launcher. The route exists in
the restriction model (`CarSheet.Search`), and `AutomotiveContentViewModel` has hidden search
state, but no shipped screen can enter or render it.

A6 was blocked by Q2: how text entry should work on a head unit. The spec resolves that by using
the platform/system IME only when `UX_RESTRICTIONS_NO_KEYBOARD` is absent, and by showing a
system/Assistant voice-search prompt when text entry is restricted. The app must not draw a custom
keyboard, request `RECORD_AUDIO`, or create an in-app recorder.

## Scope

- Enable the system-bar search icon.
- Add `CarSearchScreen` for idle/search-entry.
- Add `CarSearchResultsScreen` for submitted song results.
- Split search out of `AutomotiveContentViewModel` into `AutomotiveSearchViewModel`.
- Search on explicit submit, not every keypress.
- Maintain session-only recent queries.
- Keep results viewable while driving when text entry is inactive.
- Refuse active text entry under `NO_KEYBOARD` with the existing gate and explanation.
- Cap visible results by `maxCumulativeContentItems` while distraction optimization is required.
- Align offline song search with mobile by matching album names as well as title and artist.

## Result Scope Decision

A6 ships **song-only results**. Album, artist and playlist result cards are deferred to
`docs/tickets/T4-automotive-multi-entity-search.md`.

The reason is structural: `SongRepository.searchSongs()` and the media-session search path already
define a song-search contract, while album/artist/playlist result cards need typed result models,
repository search APIs, and a real artist-detail destination. Filtering incidental in-memory lists
inside A6 would make the custom launcher disagree with system voice search and would hide cache
timing as "no results."

## Out Of Scope

- Custom on-screen keyboard.
- In-app microphone recording or `RECORD_AUDIO`.
- Album/artist/playlist result cards.
- Persistent search history.
- Settings/profile system-bar destinations.
- Mobile search changes.
- Rewriting `PlaybackService.onSearch` / `onGetSearchResult`.

## Acceptance Criteria

- Given the user is signed in, when they tap the system-bar search icon, then the search sheet
  opens.
- Given text entry is allowed, when search opens, then a platform-backed text field is editable and
  can submit with the IME Search action or the sheet CTA.
- Given `NO_KEYBOARD` is active, when search opens, then no editable field or keyboard appears and
  the system/Assistant voice-search prompt is shown.
- Given the user submits a non-blank query, when results return, then screen 6 shows a top song
  result and song rows.
- Given no songs match, when results return, then an explicit no-results state is shown.
- Given search fails, when results are shown, then an error and retry path are available.
- Given the vehicle is moving and results are visible, then the result list is capped to
  `maxCumulativeContentItems`.
- Given the vehicle starts moving while the field is actively editing, then the app evicts from
  text entry and explains why.
- Given a visible result is tapped, then playback starts from the visible result list at that song.
- Given a query is submitted repeatedly, then recent queries de-duplicate and cap at five.
- Given manifests are inspected, then no variant requests `RECORD_AUDIO`.

## Notes

The A6 follow-up is already known: multi-entity search cards. Do not slip that into A6 as an
implementation detail; it needs its own data contract.

# T4 - Multi-entity search results for the automotive launcher

- **Slice:** search follow-up after A6
- **Depends on:** A6 song-search implementation; D33 result-scope decision
- **Status:** Spec/plan ready — implementation not started
- **Spec:** `docs/superpowers/specs/2026-08-21-aaos-t4-multi-entity-search-design.md`
- **Plan:** `docs/superpowers/plans/2026-08-21-aaos-t4-multi-entity-search.md`
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest :core:data:testDebugUnitTest`
- **Design Reference:** `docs/superpowers/specs/2026-08-20-aaos-search-design.md` D33;
  original `docs/AAOS_SCREEN_CONTRACT.md` screen 6 album/artist card wording
- **Risk Tags:** data contract, cache consistency, navigation depth, artist detail scope
- **Affected Modules:** `:automotive`; likely `:core:data`; possibly `:core:playback`

## Problem

A6 deliberately ships song-only search results. The original screen-contract wording also wanted
album and artist result cards, but the repository layer does not currently expose album, artist or
playlist search APIs, and the custom launcher has no general artist-detail destination. The only
artist screen is liked-songs scoped, which is not a valid destination for an arbitrary catalogue
artist result.

Implementing cards by filtering whatever lists happen to be loaded in memory would make custom
launcher search disagree with `PlaybackService.onSearch` / `onGetSearchResult`, and it would turn
cache timing into false "no result" states.

## Scope

- Define a typed search result model for songs, albums, artists and playlists.
- Add repository APIs or a single search service that can produce those result types
  consistently.
- Add a general catalogue artist detail destination; do not reuse the liked-songs artist screen
  for arbitrary catalogue artist results.
- Align custom launcher results with the media-session search path where possible.
- Add tests that prove a query can return multiple result types without relying on incidental
  in-memory collectors.
- Update screen 6 wording once cards actually ship.

## Out Of Scope

- Reopening A6 text-entry behaviour.
- Custom voice capture.
- Persistent search history.
- Mobile search redesign unless the shared data contract makes a small compatibility update
  necessary.

## Acceptance Criteria

- Given a query matches songs and albums, when automotive search runs, then results preserve their
  type and render in the intended sections.
- Given a result card is tapped, then it opens a destination that already exists and is valid under
  the current depth restrictions.
- Given driving restrictions cap content, then the total rendered result stream obeys the cap
  without hiding the active destination behind a stale result.
- Given system voice search uses the media-session path, then its result semantics do not diverge
  from the custom launcher without a documented reason.
- Given unit tests run, then result typing, ordering, empty states and cache-miss behaviour are
  covered.

## Notes

This is the follow-up that closes the result-card part of the original screen 6 ambition. It is not
an A6 acceptance blocker.

Spec decisions settled on 2026-08-21:

- Use repository search primitives plus an automotive-facing coordinator.
- Preserve typed results through UI tap handling.
- Render fixed sections: featured, songs, albums, artists, playlists.
- Keep `PlaybackService` search song-only for T4, with launcher-only non-song enrichment
  documented.
- Apply the driving cap cumulatively across the rendered result stream.

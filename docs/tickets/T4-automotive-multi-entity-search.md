# T4 - Multi-entity search results for the automotive launcher

- **Slice:** search follow-up after A6
- **Depends on:** A6 song-search implementation; D33 result-scope decision
- **Status:** Implemented and device-verified
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

## Outcome

Shipped across seven tasks on `ek/aaos-t4-multi-entity-search`. Design records D43-D47 in
`docs/aaos-DESIGN.md` carry the decisions; screen 6's contract row is rewritten and screen 21
(`CarArtistScreen`) is new.

**Two forks the plan did not anticipate**, both found by writing the media-session parity test
rather than by reasoning about it:

- The launcher trimmed committed queries in its ViewModel while `MediaBrowseTree` passed
  Assistant's query through untouched, so `" grace "` was two different searches on two surfaces
  of one feature. Normalization moved into the repository layer, below both callers (D44).
- The coordinator re-sorted its song section by match quality, away from the popularity order
  Assistant is served. Sections now keep their repository's order and the cross-type comparator
  only picks the featured card (D43) — less code, and the fork is gone.

**Two defects fixed on the way through**, both pre-existing and neither in T4's scope:

- `SongDao.search` did not escape LIKE wildcards, so a driver searching `50%` matched the whole
  catalogue. Fixed for all three search queries at once rather than adding two more instances.
- `:core:playback`'s test suite had stopped compiling on `main` — `MediaBrowseTreeTest`'s
  `TestAuthRepository` never implemented `AuthRepository.currentUserId`.

**Coverage:** the `:automotive`, `:core:data`, `:core:playback` and `:app` suites, `detekt`, lint
and both flavour assembles all pass. Every non-trivial behaviour was mutation-checked;
three mutations survived their first test and were only killed after the test was strengthened —
the wildcard escaping (decoys shared no prefix with the query), the query trim (the parity test
runs on fakes that trim themselves), and nothing covered `capped()` until `SearchResultCapTest`
existed.

## Device verification

Run on `AAOS_AOSP_33_userdebug` (API 33, driver user 10) on 2026-08-21, parked.

Confirmed: sectioned results render with the featured card above them; empty sections are absent;
a song result plays and opens the full player; an artist card opens the catalogue artist detail on
the Library tab, with Play/Shuffle/Back and no like hearts; a playlist card opens playlist detail.
Cross-type ranking showed itself unprompted — searching "beat" made the playlist *Beats* the top
result over two songs that matched only secondarily, with an "Open" pill rather than "Play".

**It also found a defect no JVM test had:** the result list would not scroll, so everything below
the fold was unreachable. `carConsumeTouches()` consumed every pointer change on the main pass and
swallowed drags along with taps. Fixed in the helper, with a test that fails on the old
implementation.

`CarQueueScreen` carries the same modifier, and rather than assume, both builds were run: on the
pre-fix build a 13-song queue showed five rows and would not scroll; on the fixed build the same
queue scrolls. A5's queue had been unscrollable since T6 shipped the modifier.

**Not exercised:** the cumulative driving cap. This account's catalogue returns about five results
for any query, well under `maxCumulativeContentItems` (21 on this image), so the cap never binds
and there is nothing to observe. It stays covered by `SearchResultCapTest` only.

## Not verified
- **`FirebasePlaylistRepository.searchPlaylists`.** Firestore is not JVM-testable here, so the
  one-shot read and its in-memory name filter are covered only through fakes.
- **Launcher/Assistant parity below the repository.** `SearchParityTest` proves neither caller adds
  divergence on top of one shared repository call, which is the shape of both forks T4 found. Both
  sides run through the same fake, so it does not exercise the DAO; the repository's own
  normalization is covered by `CatalogSearchDaoTest`.
- **The shell wiring for a non-song tap.** `routeSearchResult` is unit-tested, but the step it
  hands off to — close the sheet, move to the Library tab, set `drillDown` — is composable-local
  state in `AutomotiveApp` and has no test.

## Left for later

- **Three detail screens are one function.** `CarAlbumScreen`, `CarPlaylistScreen` and the new
  `CarArtistScreen` are byte-identical forwarders to `CarDetailBody` differing only in one empty
  state string, and `DetailRoute` now has three identical branches naming them. Making the body
  public and selecting `emptyBody` at the single call site removes about 110 lines. Two-thirds of
  that duplication predates T4, so it is a refactor of A3 screens rather than part of this slice.
- **The exact/prefix/substring ladder exists four times** — two DAO `CASE` blocks,
  `matchQuality()` and `FirebasePlaylistRepository.nameMatchTier`. A shared helper would span
  `:core:data` and `:automotive` for three lines each, which costs more than it saves. Recorded as
  a drift risk, not a task.
- **The `:automotive` search fakes keep `query.trim()`** even though `AutomotiveCatalogSearch`
  trims before calling them. It is unreachable today, but the repository interfaces document
  trimming as part of the search contract, and a fake that quietly breaks that contract would
  mislead the first test to call it directly.

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

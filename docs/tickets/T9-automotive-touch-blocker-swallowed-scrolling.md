# T9 — `carConsumeTouches()` swallowed scrolling on the surfaces it protected

- **Slice:** defect — regression from T6
- **Depends on:** nothing; the fix ships inside T4
- **Status:** Fixed on `ek/aaos-t4-multi-entity-search` (PR #38); needs a post-merge device re-check
- **Verification Command:** `./gradlew :automotive:testOemDebugUnitTest --tests "*CarConsumeTouchesTest*"`
- **Design Reference:** `docs/aaos-DESIGN.md` D41 (and its T4 amendment) ·
  `docs/tickets/T6-automotive-navigation-state-encoding.md`
- **Risk Tags:** input handling · shared UI primitive · silent loss of content
- **Affected Modules:** `:automotive`

## Problem

T6 added `carConsumeTouches()` so a full-screen surface would stop taps reaching `BrowseShell`
behind it — on device, tapping empty space in the open search sheet was pressing the nav rail and
switching tabs. The modifier consumed **every** pointer change on the main pass.

That also consumed drags, including the ones the surface's own content needed. Any scrollable
inside a protected surface stopped scrolling, with no error and no visual cue: the list simply did
not move.

The KDoc asserted the opposite — "the surface's own children still handle their touches first, only
what they leave unhandled is blocked". Children *are* dispatched first on the main pass, so the
reasoning looked sound; it just is not what the runtime does.

## Impact

Verified on `AAOS_AOSP_33_userdebug`, driver user 10, by installing the pre-fix build:

- **`CarQueueScreen`** — a 13-song queue rendered five rows and would not scroll. Eight songs were
  unreachable on the screen whose entire job is showing what plays next. Shipped broken since T6.
- **`CarSearchResultsScreen`** — with T4's sections, everything below the fold (Artists, Playlists)
  was unreachable. Before T4 the song-only list had the same defect past a screenful of results.

Checked and **not** affected:

- **`CarSearchScreen`** — its two chip rows scroll horizontally and would be blocked the same way,
  but at 1024dp both rows fit, and recent queries are capped at five. No visible impact today.
- **`CarLibraryScreen`** — applies the modifier to a modal card with no scrollable content.

## Root cause

One shared helper, so one fix. `carConsumeTouches()` is now `detectTapGestures { }`: the thing that
leaked was a tap, and a tap detector consumes taps without touching drags. It publishes no
semantics, so FR-2.6 still holds — the reason T6 rejected a no-op `clickable`.

Two other shapes were tried on device first and are recorded so nobody re-derives them:

- Consuming only changes that arrived unconsumed — scrolling still blocked.
- `Modifier.weight(1f)` on the list, on a theory that the Column was overflowing — no effect;
  reverted.

Drags now pass through to whatever is underneath. Nothing under a full-screen surface is visible to
drag at, and an unscrollable sheet is the worse failure.

## Why the suite did not catch it

T6 was device-verified for the defect it was fixing — taps leaking to the nav rail — and nobody
dragged inside the surfaces the modifier was applied to. The JVM tests asserted tap blocking, the
property under change. A fix for one gesture broke another on two screens and stayed green through
three review agents.

`CarConsumeTouchesTest` now renders a list inside a blocking surface and swipes it. It fails on the
old implementation.

## Left to do

- **Post-merge device re-check** of both surfaces, since the fix reaches `main` inside T4's PR
  rather than on its own branch.
- **`docs/AAOS_A5_VERIFICATION.md`** records the queue as verified; that pass predates T6 and its
  row-tap check did not require scrolling, so it is not wrong — but a reader should know the queue
  was unscrollable for the whole T6-to-T4 window.
- **Any future surface** taking `carConsumeTouches()` should be checked with a drag, not only a
  tap.

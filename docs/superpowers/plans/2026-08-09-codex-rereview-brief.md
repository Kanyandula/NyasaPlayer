# Re-review brief for Codex — AAOS A3 spec, round 2

**Review target:** `docs/superpowers/specs/2026-08-09-aaos-browse-library-detail-design.md`
**Round 1 brief:** `docs/superpowers/plans/2026-08-09-codex-review-brief.md`

This is a **scoped confirming pass**, not a fresh review. You returned four findings and a
verdict of "not safe to turn into an implementation plan". All four were accepted; nothing was
declined. Below is what changed per finding.

---

## Finding 1 — artist detail lost after process death · **accepted**

You were right, and the fix removes the failure rather than guarding it.

`CarDestination.Artist` now carries `artistName` alongside `artistId` (§2.2), and the
resolve-against-`favoriteArtists`-or-clear rule is **deleted**. There is no lookup left to fail
and no clearing rule to fire during the gap. The track list stays a live filter over
`likedSongs` and is legitimately empty until the first emission.

Recorded as **D16** with your process-death reasoning. §7.2 gained manual check 8 —
`adb shell am kill` while inside artist detail — and §9 gained the matching risk row.

I did not take the `favoriteArtistsLoaded` flag option: a loaded-flag exists only to protect a
lookup, and there is no longer a lookup.

## Finding 2 — the `getSongsByIds` ordering claim was false · **accepted**

Confirmed at `core/data/.../offline/OfflineSongRepository.kt:21-28`. §3.3 was rewritten from
"the ViewModel must compensate" to "the repository already guarantees this and A3 adds no
ordering code". The `orderByIds` extraction is **gone**, along with its test in §7.1 and its
definition-of-done item.

§3.3 now records two things as explicitly out of scope: the ViewModel's redundant re-ordering
in `loadRecentlyPlayed()` / `observeLikedSongs()`, and `FakeSongRepository` diverging from
every real implementation by not preserving request order. I did not fold the fake's fix into
A3 — it is a `:core:data` test-fidelity bug in a module this slice otherwise does not touch.

## Finding 3 — Album download does not satisfy screen 11 · **accepted, waived with a blocker**

Your correction to my rationale was right: the subsystem exists, and "nothing to call" was the
wrong reason. The distinction I have recorded instead is the module boundary —
`DownloadRepository` (`:core:data`) is reachable but is Room bookkeeping only, while
`SongDownloadManager` (`@Singleton` in `:app`) is what actually fetches and writes the file.
Wiring only the repository ships a button that marks a track "downloading" forever.

**D12 is now a recorded module blocker** rather than a rationale: *downloads are unreachable
from `:automotive` until `SongDownloadManager` leaves `:app`*, extraction assigned to A8, which
owns downloads and needs it regardless. §4.3 states the deviation as knowing.

This is the one place A3 knowingly ships a screen short of its contract row. If you think the
extraction belongs in A3 instead, say so plainly — it is the finding I am least settled on.

## Finding 4 — Library loading/error coverage missing · **accepted**

§4.2 gained `isLoading`, `errorMessage` and `onRetry` on the signature, row-shaped placeholders
rather than a full-screen spinner, and retry wired to `retryLoad()`. It also now states that
"omit rows with no data" applies to data rows only — the disabled Downloads row is never
omitted, including on the empty screen. §7.2 check 9 and definition-of-done item 6 cover it.

## Your low-confidence notes 2 and 3 · **both applied**

- The artist path is documented as shell-owned and live-filtered (§3.2, D16).
- §3.2 now specifies that cancellation alone is insufficient — a coroutine resuming from
  `getSongsByIds` can write before observing cancellation — so the write is guarded by
  re-checking the destination is still current, and `CancellationException` is rethrown.
  §7.1 cases 3 and 4 are the tests for exactly this.

## What I want from this pass

Scoped to the above. Please confirm or reject:

1. Does D16 actually close finding 1, or does carrying `artistName` in the destination create a
   staleness problem of its own — a renamed artist showing the old name after a restore?
2. Is §3.3 now accurate about `OfflineSongRepository`, and is leaving the ViewModel's redundant
   re-ordering in place the right call for this slice?
3. Is the D12 waiver acceptable, or does screen 11 need the `:app` → shared-module extraction
   pulled into A3?
4. Do §7.1 cases 3 and 4 describe a test that can actually be written against the design in
   §3.2, or does the guard need to be specified more precisely for that to be testable?
5. Anything the four fixes broke elsewhere in the spec.

Then: is it safe to turn into an implementation plan?

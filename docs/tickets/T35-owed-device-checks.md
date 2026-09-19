# T35 - Four device checks are owed, on two surfaces

- **Slice:** verification debt
- **Depends on:** nothing. Needs a signed-in `Pixel_9_Pro_Fold_API_35` and a running
  `AAOS_AOSP_33_userdebug`.
- **Status:** Filed 2026-09-19. Each item has been carried in a "still owed" note since its own
  ticket shipped; this collects them so one sitting can clear them all.
- **Verification Command:** device passes, recorded in the relevant `docs/*_VERIFICATION.md`
- **Design Reference:** `docs/T10_VERIFICATION.md`, `docs/T13_VERIFICATION.md`,
  `docs/T14_VERIFICATION.md`, `docs/AAOS_SHIP_RECORD.md`
- **Risk Tags:** unverified shipped behaviour, both surfaces
- **Affected Modules:** none — no code change expected unless a check fails

## Problem

The 2026-09-19 phone sitting cleared five owed passes (T25, T29, T13, T14, T10) and explicitly did
**not** cover four more. They are scattered across four verification documents, which is how they
keep being deferred.

| # | Surface | Check | Where it is owed from |
|---|---|---|---|
| 1 | Mobile | **T3's D55 index fix** — restore resumes the song the driver was on, not the index it was at. Shared `:core:playback` code, verified on the car, never on a phone | `docs/tickets/T3-automotive-playback-restore.md:88`, `docs/T10_VERIFICATION.md:70` |
| 2 | Mobile | **T7's skeletons** — the shared `CarRowSkeleton` replaced four copies; the mobile-visible result was never looked at on a device | `docs/AAOS_SHIP_RECORD.md:49` |
| 3 | Car | **T13's `skipNext` repeat-all wrap** — skipping forward off the end of the queue with repeat-all on | `docs/BACKLOG.md`, T13's still-owed note |
| 4 | Car | **A driving-state pass** — confirm no new surface appears mid-drive after T13 and T14 | `docs/T14_VERIFICATION.md:52` |

None of these is a suspected bug. They are shipped behaviour nobody has watched on the surface it
ships to.

## Scope

- Run all four in one sitting, phone first (items 1–2), then the car (items 3–4).
- Record each in the verification document that owes it, dated, with the evidence that decides it —
  `dumpsys media_session` for the car, not a screenshot of the Now Playing surface.
- For the driving-state pass, follow `docs/AAOS_DRIVING_STATE_TESTING.md` rather than inventing a
  procedure.

## Out Of Scope

- Fixing anything a check turns up. A failure becomes its own ticket with the evidence attached —
  this one is about looking, not repairing.
- Re-running the five passes the 2026-09-19 sitting already cleared.

## Acceptance Criteria

- All four checks have a dated result in their owning document, pass or fail.
- `docs/BACKLOG.md`'s "Owed phone checks" section no longer lists anything as still owed, or says
  what is left and why.

## Notes

The phone AVD needs a manual sign-in — the owner signs in; nothing here enters credentials.
`am crash` on API 35 needs a pid, and `am kill` is refused while a foreground service holds the
process: compare pids, never presence.

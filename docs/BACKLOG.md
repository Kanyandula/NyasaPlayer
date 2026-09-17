# Backlog — after the AAOS PRD

`docs/AAOS_PRD.md` is finished as written before anything here is picked up (owner, 2026-09-15).
One line per item. An item becomes a `docs/tickets/` entry only when it is picked up, or if it turns
out to be a bug a driver or user would hit, or a safety gap.

## Specced or filed tickets, parked

- **T27** — report a controller found disconnected as a non-fatal (`docs/tickets/T27-tripwire-non-fatal.md`).
- **T28** — mobile uses the shared offline rule (`docs/tickets/T28-mobile-offline-rule.md`).
- **T20** — audio quality preference (`docs/tickets/T20-audio-quality-preference.md`).
- **T19** — car phone and email sign-in; deferred past ship, the PRD's §12 exception (`docs/tickets/T19-car-auth-phone-and-email.md`).
- **T18** — car PIN opt-in; deferred past ship, the PRD's §12 exception (`docs/tickets/T18-car-pin-opt-in.md`).
- **T8** — search draft-query hoist, deferred on measurement (`docs/tickets/T8-automotive-search-draft-query-hoist.md`).

## Owed phone checks

`Pixel_9_Pro_Fold_API_35` can take installs (`docs/T26_VERIFICATION.md`).

- **T25** — phone release crash reaches the dashboard; phone debug crash does not (`docs/T25_VERIFICATION.md` → "The phone").
- **T29** — phone offline/online pass (`docs/T29_VERIFICATION.md` → "The phone").
- **T14, T13, T10** — the mobile passes their Outcomes leave open.

## Known, not fixed

- `songs/0CvN4z9xMVoRSi6iTjgp` (`mediaId` 190, "mighty") is 705 bytes of zeros in Firebase Storage
  with `durationMs` 0 — a broken upload in the catalogue, not an app bug
  (`docs/AAOS_A9_VERIFICATION.md`, 2026-09-17).

- A 404 is called "No Connection" on the car (`docs/AAOS_A8_VERIFICATION.md`, "Findings recorded, not fixed here").
- `CLAUDE.md` doesn't mention Crashlytics or `CrashReporter`.
- The outlined `CarPillButton` border is 1.4:1 against WCAG 1.4.11's 3:1 for component boundaries (non-text, outside NFR-2).
- The queue's `RemoveConfirmDialog` renders inline inside its lazy item (`CarQueueScreen.kt:335`), not as a modal over the queue.
- Nav-rail labels are 13sp (`CarNavRail.kt:41`), under NFR-3's 14sp floor.
- `AutomotiveContentViewModel` is past detekt's function threshold and now owns downloads too; its file-level `TooManyFunctions` note says the next slice to touch it should split it, and A9 added to it instead (PRD §6.3 names the content VM as screen 15's data source).

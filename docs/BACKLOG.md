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

## Owed phone checks — cleared 2026-09-19

All five ran on `Pixel_9_Pro_Fold_API_35` in one sitting: T25 (release crash sent, debug crash not,
one event on the dashboard), T29 (offline banner, refusal wording, recovery), T13 (transport set,
`dismiss()`, the offline-buffering pause), T14 (both ways of finishing the Activity), T10 (restore
after `force-stop`, with the service watched from the first launch). Each record's own "The phone" /
"Mobile" section carries the evidence.

Still owed from those tickets, and not phone work: T13's `skipNext` repeat-all wrap and a
driving-state pass on the car (T13, T14).

## Known, not fixed

- `songs/0CvN4z9xMVoRSi6iTjgp` (`mediaId` 190, "mighty") is 705 bytes of zeros in Firebase Storage
  with `durationMs` 0 — a broken upload in the catalogue, not an app bug
  (`docs/AAOS_A9_VERIFICATION.md`, 2026-09-17).

- A 404 is called "No Connection" on the car (`docs/AAOS_A8_VERIFICATION.md`, "Findings recorded, not fixed here").
- `CLAUDE.md` doesn't mention Crashlytics or `CrashReporter`.
- The outlined `CarPillButton` border is 1.4:1 against WCAG 1.4.11's 3:1 for component boundaries (non-text, outside NFR-2).
- The queue's `RemoveConfirmDialog` renders inline inside its lazy item (`CarQueueScreen.kt:335`), not as a modal over the queue.
- A download refused because the phone is offline tells the user nothing: the overflow sheet closes
  like a download has started, no Snackbar follows, and Downloads still reads "No downloads yet"
  rather than showing a failed row (`docs/T29_VERIFICATION.md`, 2026-09-19).
- A like made while Firestore's backend was unreachable appeared to be dropped on reconnect — the
  heart filled, then unfilled once the network returned, with no tap in between. Seen once, not
  reproduced; `toggleLike`'s optimistic rollback would explain it (`docs/T13_VERIFICATION.md`,
  2026-09-19).
- `AutomotiveContentViewModel` is past detekt's function threshold and now owns downloads too; its file-level `TooManyFunctions` note says the next slice to touch it should split it, and A9 added to it instead (PRD §6.3 names the content VM as screen 15's data source).

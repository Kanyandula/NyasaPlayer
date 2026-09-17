# A9 — verification record

Covers the device pass for A9 (car downloads) on `ek/prd-a9-downloads`.

- **Date:** 2026-09-16, download pass 2026-09-17
- **Branch:** `ek/prd-a9-downloads` at `554e317` (A9 code at `15a0f03`, queue fix at `6d5f874`)
- **Car AVD:** `AAOS_AOSP_33_userdebug`, driver user 10, `oem` debug, signed in
- **Driving state:** injected per `docs/AAOS_DRIVING_STATE_TESTING.md` —
  `cmd car_service inject-vhal-event 0x11400400 8` (gear DRIVE) plus
  `inject-continuous-events 0x11600207 40 -s 5 -d 60` (speed), and gear PARK (`4`) to come back

## Gates

From the branch, before the device pass: 620 tests across all modules, 0 failures; detekt clean; lint
clean on both flavors. The Exit measurement with screen 15 and the album Download control in its
inventory: 119 cases in 161 frames, 695 interactive nodes, 0 below 76dp; 1103 text nodes, 0 below
7:1, 0 never measured whole.

## What passed on the device

| Check | Result |
|---|---|
| Library → Downloads | The Downloads card is live in the Library's Downloads row, labelled "Offline music", and opens screen 15 |
| Downloads, empty, parked | "No downloads yet", the line "Download songs while parked and they play without a connection", a live **Browse Music** CTA, and **Remove All** disabled because there is nothing to remove |
| Downloads, driving | **Remove All** reads **Locked**; the banner reads "Park the car to remove or retry downloads. Downloaded songs still play."; **Browse Music** is disabled. The screen itself stays readable — view-only, as US-15 asks |
| Queue remove confirmation, parked → driving | Opened "Remove from queue?" for a row while parked; on the real gear-and-speed transition the confirmation disappeared, **Clear Queue** became **Locked**, and the "Park the car to remove or clear your queue" banner appeared. This is `6d5f874` on a real transition rather than a test flag |
| Restriction transition, both ways | Injecting DRIVE and returning to PARK moved the UI both ways with the app left running |

## The download itself — 2026-09-17

The first pass could not reach the download action at all: it lives only on the album screen
(screen 11, `albumDownloadControl`), and the catalogue had no albums. With the owner's approval one
was added — Firestore `albums/test_album_a9_downloads`, "Test Album (A9 downloads)", two existing
songs (`mediaId` 170 and 190) — and the rest of the pass ran.

| Check | Result |
|---|---|
| Album screen, parked | The **Download** control is there beside Play, Shuffle and Back |
| Download, parked | Both songs fetched; the control became **Downloaded** and disabled. `files/downloads/` holds `170.audio` (4,852,096 bytes) and `190.audio` (705 bytes), each exactly the size Firebase Storage reports for its source |
| Downloads screen, populated | "2 songs · 5 MB", a row per song with its own size, a delete button each, **Remove All** live |
| Plays from the file | Airplane mode on, app force-stopped and relaunched, then play: the offline banner showed and playback ran from 38.2s to 47.3s with `Active default network: none`. A fresh process with no network can only be reading the file |
| Remove one | The 705-byte row went; `files/downloads/` kept only `170.audio`; the header fell to "1 song · 5 MB" |
| Remove All | Confirmation, then `files/downloads/` empty and the empty state back. Playback continued from the open file handle, which is ordinary Unix behaviour |
| Downloads while driving | **Remove All** reads **Locked** and the banner explains why (verified again on this pass) |

**The download control while driving is not device-verified.** `albumDownloadControl`
(`CarDownloadItem.kt:109`) returns "Parked only", disabled, when `isDriving` — visible and refused,
as FR-2.6 asks. On this pass the app was evicted to the car launcher while driving before the album
screen could be reached, which is the outer gate (drill-down refused in motion) doing its job. The
unit tests cover the control itself.

## Found on this pass

- **The "Remove all downloads?" confirmation is clipped on an 800×1024 head unit.** Its Cancel and
  Remove buttons are cut off by the dialog's own bottom edge — tappable, but only half drawn. The
  measurement suite renders at 1280×800dp, where it fits, so nothing caught it. Filed as
  `docs/tickets/T30-remove-all-dialog-clipped.md`.
- **One catalogue song is a broken file.** `mighty` (`mediaId` 190) is 705 bytes of zeros in Firebase
  Storage, and `durationMs` is 0. The app downloaded it faithfully — this is the source, not the
  downloader. Recorded in `docs/BACKLOG.md`.
- **The test album stays in Firestore** until the owner deletes it: `albums/test_album_a9_downloads`.

## Emulator note

The first attempt died: `system_server` crashed and restarted, taking SystemUI, the phone and the
network stack with it, and the launcher activity then looked as if it did not exist. A reboot hung,
so the emulator was killed, its stale `multiinstance.lock` removed, and it was cold-booted. Every
result above is from after that cold boot, with load settled to 2.2 and 655 MB free — the health
check `docs/` records elsewhere as the precondition for believing any device finding.

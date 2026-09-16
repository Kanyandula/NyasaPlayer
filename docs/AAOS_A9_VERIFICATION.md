# A9 — verification record

Covers the device pass for A9 (car downloads) on `ek/prd-a9-downloads`.

- **Date:** 2026-09-16
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

## Not verified — no album in this catalog

The download action lives only on the album screen (screen 11, `albumDownloadControl`). This
emulator's signed-in catalogue has **no albums**: the Library has no Albums row, a search for
"guitar" returns Songs and Artists only, and the search screen's **Albums** shortcut lands on a
Library with no album row. So these could not be reached:

- downloading an album's songs while parked, and the progress and done states;
- playback from the downloaded file rather than the network;
- removing one download, and Remove All with content present;
- the download control refused while driving.

The unit and Robolectric tests on the branch cover these paths. What is owed is the on-device pass,
which needs at least one album document in the catalogue (`albums` in Firestore, with songs that
reference it). Nothing in A9's code is suspected: the parts that could be reached behaved as
specified.

## Emulator note

The first attempt died: `system_server` crashed and restarted, taking SystemUI, the phone and the
network stack with it, and the launcher activity then looked as if it did not exist. A reboot hung,
so the emulator was killed, its stale `multiinstance.lock` removed, and it was cold-booted. Every
result above is from after that cold boot, with load settled to 2.2 and 655 MB free — the health
check `docs/` records elsewhere as the precondition for believing any device finding.

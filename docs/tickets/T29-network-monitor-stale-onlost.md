# T29 - NetworkMonitor can miss the network going away

- **Slice:** correctness, shared by both surfaces
- **Depends on:** —
- **Status:** Filed, not specced
- **Verification Command:** `./gradlew :core:common:testDebugUnitTest`, plus a device pass
- **Design Reference:** `docs/AAOS_A8_VERIFICATION.md` (findings); D71
- **Risk Tags:** shared module, both surfaces, mobile behaviour change
- **Affected Modules:** `:core:common` (and every consumer of `NetworkMonitor.isOnline`)

## Problem

During the A8 device pass, the first airplane-mode toggle left the car app believing it was online:
no offline banner, `isOffline` false, while `dumpsys connectivity` reported no default network. A
relaunch, and a second toggle, both flipped it correctly. One miss in two live transitions; not yet
reproduced on demand.

The likely cause is in `NetworkMonitor`: every callback — `onAvailable`, `onLost`,
`onCapabilitiesChanged` — answers by calling `checkCurrentConnectivity()`, which reads
`connectivityManager.activeNetwork` and `getNetworkCapabilities(...)` synchronously. The platform
documentation for `NetworkCallback` warns against exactly that: the objects those synchronous calls
return are not guaranteed to be current inside a callback. An `onLost` that reads a stale active network
answers "online", and nothing corrects it until the next callback.

It matters more after A8. The car's fail-fast offline guards (D71) and mobile's own offline checks
read `isOnline`; when it is wrong, both fall back to streaming and failing slowly.

## Scope

- **Reproduce first**, on the AAOS emulator and a phone: toggle airplane mode repeatedly with the app
  in the foreground and record how often the banner fails to appear.
- Derive the answer from the callback's own arguments rather than synchronous queries: `onLost` →
  offline; `onCapabilitiesChanged(network, caps)` → `caps` has `INTERNET` and `VALIDATED`; leave
  `onAvailable` to the capabilities callback that follows it.
- A JVM test for the state machine, with the callback driven directly.

## Out Of Scope

- Changing what "online" means (the `VALIDATED` requirement stays).
- The car's classification of every `IOException` as "No Connection" — separate finding in the A8 record.

## Acceptance Criteria

- Given the app is in the foreground, when the network goes away, then `isOnline` becomes false
  every time across repeated toggles, on both surfaces.
- Given the network returns and validates, then `isOnline` becomes true.

## Notes

This changes mobile behaviour, which is why A8 filed it rather than fixing it (spec decision 4: A8
leaves `:app`'s behaviour untouched). It needs its own phone pass.

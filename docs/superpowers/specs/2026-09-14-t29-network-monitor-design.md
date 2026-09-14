# T29 — NetworkMonitor answers from its callbacks: design

Ticket: `docs/tickets/T29-network-monitor-stale-onlost.md`. Affects `:core:common`, and through it every
consumer of `NetworkMonitor.isOnline` on both surfaces.

## What exists today

`NetworkMonitor` (`core/common/.../util/NetworkMonitor.kt`) is a `@Singleton` that registers a default
network callback and exposes `isOnline: StateFlow<Boolean>`. Every callback — `onAvailable`, `onLost`,
`onCapabilitiesChanged` — ignores its arguments and calls `checkCurrentConnectivity()`, which reads
`connectivityManager.activeNetwork` and `getNetworkCapabilities(...)` synchronously and answers
`INTERNET && VALIDATED`.

Five consumers read it: the car's `AutomotivePlayerViewModel` (A8's offline guards) and, on mobile,
`PlayerViewModel`, `ProfileViewModel` and `SongDownloadManager`.

**The platform says not to do this.** From `ConnectivityManager.NetworkCallback.onAvailable`'s own
documentation (API 34 sources): *"Do NOT call getNetworkCapabilities(Network) or getLinkProperties(Network)
or other synchronous ConnectivityManager methods in this callback as this is prone to race conditions
(there is no guarantee the objects returned by these methods will be current). Instead, wait for a call
to onCapabilitiesChanged."* `onLosing` carries the same warning. The same documentation guarantees that
from API 26 (O), `onAvailable` *"will always immediately be followed by a call to onCapabilitiesChanged"*,
and that a default-network callback, once handed a new network, *"will no longer receive method calls
about other networks"*.

**It was seen failing.** During A8's device pass, the first airplane-mode toggle left the car app
"online" — no banner — while `dumpsys connectivity` reported no default network. One miss in two live
transitions (`docs/AAOS_A8_VERIFICATION.md`, findings).

**A8 raised the stakes.** A false "online" makes a car song tap stream and fail slowly; a false
"offline" refuses every song tap and shuffle from the car's custom UI.

## Decisions taken

1. **Online means the default network has `INTERNET` and is not a `CAPTIVE_PORTAL`.** Not `VALIDATED`.
   A network that never validates — an OEM or telematics APN that blocks Google's probe — would read
   offline forever, and since A8 that refuses all playback on the car. A network not yet validated just
   after connecting also stops reading offline. A captive-portal Wi-Fi reads offline, which is right: it
   cannot stream until someone logs in. The cost: a network that claims internet and has none reads
   online, and streaming fails slowly — the pre-A8 behaviour, not a new failure.
2. **Answers come from the callbacks' own arguments**, through a small pure state tracker, tested with
   plain JUnit. No new test dependency in `:core:common`.
3. **This changes mobile on purpose.** It is the reason T29 was not folded into A8, whose spec kept
   `:app`'s behaviour untouched.

## Design

### 1. `DefaultNetworkState` — the rule, with no Android types

New `internal` class beside `NetworkMonitor` in `core/common/.../util/`:

```kotlin
internal class DefaultNetworkState {
    private var current: Long? = null
    var isOnline: Boolean = false
        private set

    fun available(network: Long) {
        current = network
    }

    fun capabilities(network: Long, hasInternet: Boolean, isCaptivePortal: Boolean) {
        if (network == current) isOnline = hasInternet && !isCaptivePortal
    }

    fun lost(network: Long) {
        if (network == current) {
            current = null
            isOnline = false
        }
    }
}
```

- Networks are identified by `Network.getNetworkHandle()` (API 23): a `Long`, so the class needs no
  Android types and no Robolectric.
- `available` decides nothing. The answer waits for that network's capabilities, as the platform
  documentation asks.
- Events about a network that is not the current default are ignored, so a late `onLost` for the
  network the default just switched away from cannot flip a working connection to offline.

### 2. `NetworkMonitor` — forwards, publishes

Public surface unchanged: `isOnline: StateFlow<Boolean>`. No consumer changes.

- `onAvailable(network)` → `state.available(network.networkHandle)`. **On API 24–25 only**, it then
  reads `connectivityManager.getNetworkCapabilities(network)` and feeds it to `state.capabilities(...)`:
  those versions do not guarantee the capabilities callback that follows, and without it an old phone
  could stay offline after reconnecting. It is the one synchronous read left, confined to the versions
  where it is the only source. The car is API 29+ and never takes this path.
- `onCapabilitiesChanged(network, caps)` →
  `state.capabilities(network.networkHandle, caps.hasCapability(INTERNET), caps.hasCapability(CAPTIVE_PORTAL))`.
- `onLost(network)` → `state.lost(network.networkHandle)`.
- After each, `_isOnline.value = state.isOnline`.
- **Starting value**, before registration: the same rule applied to `activeNetwork`'s capabilities, and
  the tracker seeded with that network. This read is outside any callback, so the warning does not apply;
  registration then delivers the current default network at once and corrects it if it moved.

`checkCurrentConnectivity()` is replaced by one private helper that takes a `Network`, reads its
capabilities synchronously and feeds `state.available` + `state.capabilities` — used for the starting
value (with `activeNetwork`) and on the API 24–25 path (with the callback's network), and nowhere else.

## Out of scope

- `onBlockedStatusChanged` (API 29) — an app blocked by data saver or background restrictions still reads
  online. Not seen; a separate ticket if it is.
- The car classifying every `IOException` as "No Connection" (A8 record, findings).
- A `PlayerViewModel` test harness. Mobile is verified on a device, as before.

## Testing

**JVM, `:core:common`** — `DefaultNetworkStateTest`, plain JUnit:

- available, then capabilities with internet → online
- available, then capabilities with internet and captive portal → offline
- available, then capabilities without internet → offline
- online, then lost → offline
- default switches A → B (available B, capabilities B), then a late lost A → still online
- capabilities for a network that is not current → ignored
- lost for a network that is not current → ignored
- nothing yet → offline

**Reproduce first, then compare — AAOS emulator** (`AAOS_AOSP_33_userdebug`, user 10, `oem` debug):
with the app in the foreground on Home, toggle `cmd connectivity airplane-mode enable/disable` ten
times, waiting ten seconds each way, and after each transition record whether the offline banner
matches `dumpsys connectivity`'s default network. Run it on `main` before any change, then on the fix.
Not `svc wifi/data disable` (it crashed this emulator's car stack). Pass: ten of ten on the fix. The
baseline is recorded as found, whatever it is — a zero-miss baseline does not weaken the fix, which
follows the platform's documented contract, but it does mean the reproduction did not reproduce.

**Phone pass** — `Medium_Phone_API_35`, one emulator at a time: the same ten toggles against mobile's
banner; offline play of a streamed song refused, a downloaded one plays; downloads refuse to start
offline; reconnection clears the banner and the error path.

**Both surfaces** — `./gradlew test detekt :app:assembleDebug :automotive:assembleOemDebug`.

## Documents

- `docs/tickets/T29-network-monitor-stale-onlost.md`: Scope gains the definition change; Out Of Scope
  loses "the `VALIDATED` requirement stays"; acceptance criteria unchanged.
- `docs/aaos-DESIGN.md`: **D72** — online means `INTERNET` and not `CAPTIVE_PORTAL`, answered from the
  callbacks' arguments; why not `VALIDATED` (A8's refusals make a false offline worse than a false online).
- `CLAUDE.md` "Error handling that IS in place" → the `NetworkMonitor` bullet: the new rule.
- A dated verification record, `docs/T29_VERIFICATION.md`, in the shape of `docs/T14_VERIFICATION.md`.

## Risks

- **Mobile's behaviour changes.** More networks read online: an unvalidated network no longer shows the
  banner or blocks offline checks. Intended; verified on a phone.
- **API 24–25 keeps one synchronous read.** Old phones only; the platform offers nothing else there.
- **The reproduction may not reproduce.** One miss in two is thin evidence. The fix stands on the
  platform's documented contract either way; the record says which happened.

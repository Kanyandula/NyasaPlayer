# T29 — NetworkMonitor answers from its callbacks: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `NetworkMonitor.isOnline` stops missing network loss, and "online" becomes *has `INTERNET` and is not a `CAPTIVE_PORTAL`*, decided from the callbacks' own arguments.

**Architecture:** A pure, `internal` `DefaultNetworkState` tracker keyed by `Network.getNetworkHandle()` holds the rule and is tested with plain JUnit. `NetworkMonitor` forwards each callback's arguments into it under one lock and publishes the result; it registers before seeding the starting value, and only API 24–25 keep a synchronous capabilities read.

**Tech Stack:** Kotlin, Android `ConnectivityManager.NetworkCallback`, `kotlinx.coroutines` `StateFlow`, JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-14-t29-network-monitor-design.md` — read it first; it is binding. Where this plan and an existing call site disagree, **the call site wins** — copy it and say so in your report.

## Global Constraints

- Online = default network has `NetworkCapabilities.NET_CAPABILITY_INTERNET` and not `NET_CAPABILITY_CAPTIVE_PORTAL`. `VALIDATED` is no longer read.
- `NetworkMonitor`'s public surface does not change: `class NetworkMonitor @Inject constructor(context: Context)`, `val isOnline: StateFlow<Boolean>`. No consumer file changes.
- No new dependencies. `:core:common` tests stay plain JUnit (`testImplementation(libs.junit)` only).
- `minSdk` 24 in `:core:common`. `Network.getNetworkHandle()` and `NET_CAPABILITY_CAPTIVE_PORTAL` are API 23; `registerDefaultNetworkCallback` is API 24 — no version guard needed for them.
- Detekt `maxIssues: 0`; max line length 120; trailing commas; no wildcard imports.
- Commit messages: subject ≤72 chars, body wrapped at 72 explaining why, **no AI attribution, no `Co-Authored-By` trailer**.
- Never commit to `main`; the branch is `ek/t29-network-monitor`.
- Unit tests run on the debug variant (T23).

## File map

| File | Change | Responsibility |
|---|---|---|
| `core/common/src/main/java/com/example/nyasaplayer/core/common/util/DefaultNetworkState.kt` | create | the rule and the default-network state |
| `core/common/src/test/java/com/example/nyasaplayer/core/common/util/DefaultNetworkStateTest.kt` | create | the rule's cases |
| `core/common/src/main/java/com/example/nyasaplayer/core/common/util/NetworkMonitor.kt` | modify | forward callbacks, lock, seed after registering |
| `scripts/aaos-network-toggle-check.sh` | modify | take the user and launch command from the environment, for the phone pass |
| `docs/tickets/T29-network-monitor-stale-onlost.md`, `docs/aaos-DESIGN.md`, `CLAUDE.md` | modify | the new rule, D72 |
| `docs/T29_VERIFICATION.md` | create | dated device record |

---

### Task 1: `DefaultNetworkState`

**Files:**
- Create: `core/common/src/main/java/com/example/nyasaplayer/core/common/util/DefaultNetworkState.kt`
- Test: `core/common/src/test/java/com/example/nyasaplayer/core/common/util/DefaultNetworkStateTest.kt`

**Interfaces:**
- Produces: `internal class DefaultNetworkState` with `val isOnline: Boolean`, `fun seed(network: Long?, hasInternet: Boolean, isCaptivePortal: Boolean)`, `fun available(network: Long)`, `fun capabilities(network: Long, hasInternet: Boolean, isCaptivePortal: Boolean)`, `fun lost(network: Long)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.nyasaplayer.core.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The online rule, driven the way `NetworkMonitor`'s callbacks drive it (T29). Plain JVM: networks are
 * `Network.getNetworkHandle()` longs.
 */
class DefaultNetworkStateTest {

    private val a = 101L
    private val b = 202L
    private val state = DefaultNetworkState()

    @Test
    fun nothingYet_isOffline() {
        assertFalse(state.isOnline)
    }

    @Test
    fun availableThenInternet_isOnline() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = false)
        assertTrue(state.isOnline)
    }

    @Test
    fun available_aloneDecidesNothing() {
        state.available(a)
        assertFalse(state.isOnline)
    }

    @Test
    fun captivePortal_isOffline() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = true)
        assertFalse(state.isOnline)
    }

    @Test
    fun noInternet_isOffline() {
        state.available(a)
        state.capabilities(a, hasInternet = false, isCaptivePortal = false)
        assertFalse(state.isOnline)
    }

    @Test
    fun onlineThenLost_isOffline() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = false)
        state.lost(a)
        assertFalse(state.isOnline)
    }

    @Test
    fun lateLostForThePreviousDefault_isIgnored() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = false)
        state.available(b)
        state.capabilities(b, hasInternet = true, isCaptivePortal = false)
        state.lost(a)
        assertTrue(state.isOnline)
    }

    @Test
    fun capabilitiesForANetworkThatIsNotCurrent_areIgnored() {
        state.available(a)
        state.capabilities(a, hasInternet = true, isCaptivePortal = false)
        state.capabilities(b, hasInternet = false, isCaptivePortal = false)
        assertTrue(state.isOnline)
    }

    @Test
    fun seedOnline_thenACallbackSaysOffline_isOffline() {
        state.seed(a, hasInternet = true, isCaptivePortal = false)
        state.lost(a)
        assertFalse(state.isOnline)
    }

    @Test
    fun callbackFirst_thenSeed_seedIsIgnored() {
        state.available(a)
        state.capabilities(a, hasInternet = false, isCaptivePortal = false)
        state.seed(a, hasInternet = true, isCaptivePortal = false)
        assertFalse(state.isOnline)
    }

    @Test
    fun seedWithNoNetwork_isOffline() {
        state.seed(null, hasInternet = true, isCaptivePortal = false)
        assertFalse(state.isOnline)
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*DefaultNetworkStateTest*'`
Expected: compilation fails — `Unresolved reference: DefaultNetworkState`.

- [ ] **Step 3: Write the tracker**

```kotlin
package com.example.nyasaplayer.core.common.util

/**
 * Whether the default network can carry traffic: it has `INTERNET` and is not a `CAPTIVE_PORTAL` (T29,
 * D72). Driven only by what the default-network callbacks report about the network they report on, so it
 * cannot read a stale answer, and ignores events about any network that is no longer the default.
 *
 * Not thread-safe on its own; `NetworkMonitor` calls it under one lock.
 */
internal class DefaultNetworkState {

    private var current: Long? = null
    private var heardFromCallback = false

    var isOnline: Boolean = false
        private set

    /** The starting value, applied only if no callback has spoken yet — a callback is newer. */
    fun seed(network: Long?, hasInternet: Boolean, isCaptivePortal: Boolean) {
        if (heardFromCallback) return
        current = network
        isOnline = network != null && hasInternet && !isCaptivePortal
    }

    /** Decides nothing: the answer waits for this network's capabilities, as the platform asks. */
    fun available(network: Long) {
        heardFromCallback = true
        current = network
    }

    fun capabilities(network: Long, hasInternet: Boolean, isCaptivePortal: Boolean) {
        heardFromCallback = true
        if (network == current) isOnline = hasInternet && !isCaptivePortal
    }

    fun lost(network: Long) {
        heardFromCallback = true
        if (network == current) {
            current = null
            isOnline = false
        }
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew :core:common:testDebugUnitTest --tests '*DefaultNetworkStateTest*'`
Expected: 11 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add core/common/src/main/java/com/example/nyasaplayer/core/common/util/DefaultNetworkState.kt \
        core/common/src/test/java/com/example/nyasaplayer/core/common/util/DefaultNetworkStateTest.kt
git commit -m "T29: the online rule as a pure tracker" -m "Internet and not a captive portal, decided only from what the callbacks
report about the network they report on. Events about a network that is
no longer the default are ignored, and a callback outranks the seed."
```

---

### Task 2: `NetworkMonitor` forwards to the tracker

No JVM test: the platform callback cannot be driven without Robolectric, which the spec rules out. The
logic that can be wrong lives in Task 1; this task is wiring, verified by compile, the suite, and the
device pass in Task 4. Read the whole file before replacing it.

**Files:**
- Modify: `core/common/src/main/java/com/example/nyasaplayer/core/common/util/NetworkMonitor.kt` (whole file)

**Interfaces:**
- Consumes: `DefaultNetworkState` (Task 1) — exact signatures above.
- Produces: no change to `NetworkMonitor`'s public surface.

- [ ] **Step 1: Replace the file**

```kotlin
package com.example.nyasaplayer.core.common.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the default network can carry traffic: `INTERNET` and not a `CAPTIVE_PORTAL` (D72).
 *
 * Every answer comes from the callbacks' own arguments. The platform documents that synchronous
 * `ConnectivityManager` reads inside these callbacks may be stale; doing exactly that missed about one
 * network loss in ten on the AAOS emulator (T29).
 */
@Singleton
class NetworkMonitor @Inject constructor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val lock = Any()
    private val state = DefaultNetworkState()
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = update {
                    state.available(network.networkHandle)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        // API 24–25 do not promise onCapabilitiesChanged after onAvailable, so read it —
                        // the one synchronous read left. Null counts as online: onAvailable has just
                        // declared this network the default and ready for use.
                        val caps = connectivityManager.getNetworkCapabilities(network)
                        state.capabilities(
                            network.networkHandle,
                            hasInternet = caps?.hasInternet() ?: true,
                            isCaptivePortal = caps?.isCaptivePortal() ?: false,
                        )
                    }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) = update {
                    state.capabilities(
                        network.networkHandle,
                        hasInternet = capabilities.hasInternet(),
                        isCaptivePortal = capabilities.isCaptivePortal(),
                    )
                }

                override fun onLost(network: Network) = update {
                    state.lost(network.networkHandle)
                }
            },
        )
        // Seed after registering: a network lost before registration can never reach onLost, so a seed
        // read earlier could stay "online" indefinitely. Any callback that has already arrived wins.
        val active = connectivityManager.activeNetwork
        val caps = active?.let { connectivityManager.getNetworkCapabilities(it) }
        update {
            state.seed(
                active?.networkHandle,
                hasInternet = caps?.hasInternet() == true,
                isCaptivePortal = caps?.isCaptivePortal() == true,
            )
        }
    }

    /** Callbacks run on ConnectivityThread and the seed on the constructing thread: one lock for both. */
    private inline fun update(change: () -> Unit) = synchronized(lock) {
        change()
        _isOnline.value = state.isOnline
    }
}

private fun NetworkCapabilities.hasInternet() = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

private fun NetworkCapabilities.isCaptivePortal() =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
```

- [ ] **Step 2: Build every consumer and run the suite**

Run: `./gradlew :core:common:testDebugUnitTest test detekt :app:assembleDebug :automotive:assembleOemDebug`
Expected: BUILD SUCCESSFUL. If detekt objects to the expression-bodied overrides returning `update`'s
result, switch them to block bodies calling `update { … }` and note it in the report.

- [ ] **Step 3: Commit**

```bash
git add core/common/src/main/java/com/example/nyasaplayer/core/common/util/NetworkMonitor.kt
git commit -m "T29: NetworkMonitor answers from its callbacks' arguments" -m "Every callback re-read activeNetwork synchronously, which the platform
documents as possibly stale; on the AAOS emulator it missed one network
loss in twenty transitions. Callbacks now feed the tracker under one
lock, the seed is read after registering, and only API 24-25 keep a
synchronous read."
```

---

### Task 3: Documents

**Files:**
- Modify: `docs/tickets/T29-network-monitor-stale-onlost.md`, `docs/aaos-DESIGN.md`, `CLAUDE.md`

Edit each on its unique string; never splice on a heading. Confirm with `git diff` that nothing else changed.

- [ ] **Step 1: T29 ticket**

- Status line → `- **Status:** Done — see Outcome and \`docs/T29_VERIFICATION.md\``.
- In Scope, replace the bullet beginning "Derive the answer from the callback's own arguments" with:
  "Derive the answer from the callbacks' own arguments rather than synchronous queries, through a pure
  tracker: `onLost` → offline; `onCapabilitiesChanged(network, caps)` → `INTERNET` and not
  `CAPTIVE_PORTAL`; `onAvailable` waits for the capabilities that follow it (API 24–25 excepted — see
  the spec)."
- In Out Of Scope, delete the bullet "Changing what "online" means (the `VALIDATED` requirement stays)."
- Append an `## Outcome` section: the rule changed from `VALIDATED` to `INTERNET` and not
  `CAPTIVE_PORTAL` (spec decision 1, and why); the baseline and the post-fix numbers from
  `docs/T29_VERIFICATION.md` (fill after Task 4); one sentence on mobile's behaviour change.

- [ ] **Step 2: D72**

Insert after D71's last line in `docs/aaos-DESIGN.md` (before the blank line and `## Components`), house
bullet format, two-space continuation indent, wrapped at 100:

```
- **D72 — Online means the default network has `INTERNET` and is not a `CAPTIVE_PORTAL`, answered
  from the callbacks' own arguments.** `NetworkMonitor` used to re-read `activeNetwork` inside every
  callback, which the platform documents as possibly stale, and to require `VALIDATED`. The first missed
  about one network loss in ten on the emulator (T29). The second would read a network that never
  validates — an OEM or telematics APN that blocks Google's probe — as offline forever, and since D71
  that refuses every song tap on the car. A false offline is now worse than a false online, which only
  falls back to the slow failure. A pure tracker keyed by network handle holds the rule; the monitor
  registers before it seeds, and only API 24–25 keep a synchronous read. Mobile changes with it.
```

- [ ] **Step 3: CLAUDE.md**

Replace the `NetworkMonitor` bullet (the line beginning "- **`NetworkMonitor`** (`:core:common` `util/`)")
with:

"- **`NetworkMonitor`** (`:core:common` `util/`) — singleton on `registerDefaultNetworkCallback` exposing
`isOnline: StateFlow<Boolean>`: the default network has `INTERNET` and is not a `CAPTIVE_PORTAL`, decided
from the callbacks' own arguments by `DefaultNetworkState` (D72); used by `PlayerViewModel`,
`ProfileViewModel`, `SongDownloadManager` and the car's `AutomotivePlayerViewModel`"

- [ ] **Step 4: Commit**

```bash
git add docs/tickets/T29-network-monitor-stale-onlost.md docs/aaos-DESIGN.md CLAUDE.md
git commit -m "T29: D72, the ticket, and CLAUDE.md on the new online rule"
```

(The ticket's Outcome numbers are filled in Task 4's commit.)

---

### Task 4: Device verification (controller)

**Files:**
- Modify: `scripts/aaos-network-toggle-check.sh`
- Create: `docs/T29_VERIFICATION.md` (shape of `docs/T14_VERIFICATION.md`)
- Modify: `docs/tickets/T29-network-monitor-stale-onlost.md` (Outcome numbers)

- [ ] **Step 1: Let the script drive the phone too**

At the top of `scripts/aaos-network-toggle-check.sh`, after `export ANDROID_SERIAL=…`, add:

```zsh
NT_USER=${NT_USER:-10}
NT_LAUNCH=${NT_LAUNCH:-"am start --user $NT_USER -n com.example.nyasaplayer/com.example.nyasaplayer.auto.ui.AutomotiveActivity"}
```

and change `export ANDROID_SERIAL=emulator-5554` to `export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}`.
Replace the two hard-coded `--user 10` uses and the `am start …AutomotiveActivity` line with `$NT_USER`
and `adb shell $NT_LAUNCH`. Defaults keep the car run identical to the baseline.

- [ ] **Step 2: Car — the same script on the fix**

`AAOS_AOSP_33_userdebug`, one emulator. `./gradlew :automotive:assembleOemDebug`, `adb install -r --user 10`
the APK, stop playback, run `scripts/aaos-network-toggle-check.sh`. Pass: `TOTAL: 0 misses / 20 valid`.
Baseline for comparison: 1 miss / 20 on `main` (spec, "What exists today").

- [ ] **Step 3: Phone**

Stop the AAOS emulator; boot `Medium_Phone_API_35`. `./gradlew :app:assembleDebug`, install, sign in,
stop playback. Run the script with `NT_USER=0 NT_LAUNCH="monkey -p com.example.nyasaplayer -c android.intent.category.LAUNCHER 1"`
(mobile's banner shows the same "No internet connection" text). Pass: 0 misses. Then by hand: offline,
tap a streamed song → refused with mobile's wording; downloads refuse to start offline; online again →
banner clears and a song plays.

- [ ] **Step 4: Record and commit**

Write `docs/T29_VERIFICATION.md`: date, branch and commit, AVDs, the gate, the baseline and post-fix
tables from the script's output, the phone checks, anything that could not be run and why. Fill the
ticket's Outcome numbers.

```bash
git add scripts/aaos-network-toggle-check.sh docs/T29_VERIFICATION.md docs/tickets/T29-network-monitor-stale-onlost.md
git commit -m "T29: device verification on the car and the phone"
```

---

### Task 5: Review and PR

- [ ] **Step 1:** `pr-review-toolkit:code-simplifier` on `git diff main...HEAD`; apply what holds, give reasons for what does not.
- [ ] **Step 2:** `correctness-reviewer` and `quality-reviewer` in parallel on the diff; verify each finding before acting; re-run `./gradlew test detekt`.
- [ ] **Step 3:** `gh pr create --base main`, title ≤72 chars; body: the rule change and why, the baseline and post-fix numbers, mobile's behaviour change, declined findings with reasons. No AI attribution.

# AAOS Slice A1 — Foundation & Restrictions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the token, component, flavor and driving-restriction foundation that the 20-screen AAOS design will be implemented against, and fix a live bug where search is gated on the wrong platform flag.

**Architecture:** Brand accent tokens go into `:core:common` under new names so mobile can migrate later; car-only surfaces stay in `:automotive`. Driving restrictions are modelled as a pure function over raw `Int`/`Boolean` values (the platform type is `compileOnly` and its stubs throw, so it can never appear in a unit test), wrapped by a thin platform-typed adapter. A derived `CarUiLocation` collapses five scattered pieces of navigation state into one value that a `gate()` function can decide on, including eviction when the vehicle starts moving.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, JUnit 4, Gradle Kotlin DSL, `android.car` (compileOnly), Detekt, Android Lint.

**Spec:** `docs/superpowers/specs/2026-08-02-aaos-foundation-restrictions-design.md`

## Global Constraints

- **Max line length: 120 characters.** Detekt enforces this.
- **Trailing commas required** on call and declaration sites.
- **No wildcard imports.**
- **Detekt runs at `maxIssues: 0`** — any issue fails the build. Run `./gradlew detekt` before every commit.
- **Detekt scans `*/src/main/java` only** (see root `build.gradle.kts` `source.setFrom`). Test sources are not scanned.
- **Composables that emit UI must accept `modifier: Modifier = Modifier`** as their first optional parameter. Detekt's compose-rules plugin enforces this (`ModifierMissing`).
- **`MagicNumber`**: extract literals to named constants. Top-level `val X = 76.dp` declarations are fine.
- **Kotlin jvmTarget 11**, `compileSdk = 35`, `minSdk = 29`.
- **`android.car.jar` is `compileOnly`** in `automotive/build.gradle.kts`. It is **not** on the unit-test classpath. Do not add it as `testCompileOnly` — see Task 3.
- **Commit messages:** no AI attribution, no `Co-Authored-By` trailers. Subject ≤72 chars.
- **Existing 7 car screens must keep compiling and running** throughout.

## Deliberately Deferred

Raised in review and consciously left out of A1. Not oversights — record them, do not
silently implement them.

- **`collectAsStateWithLifecycle`.** `AutomotiveApp.kt:48` and its siblings use
  `collectAsState()`. Migrating is good practice, but `androidx.lifecycle-runtime-compose`
  is **not in `gradle/libs.versions.toml`**, so this means adding a dependency and changing
  collection behaviour (collection stops in the background) across a file that seven screens
  render through. That is a behavioural change needing its own verification pass, not a
  drive-by edit inside a foundation slice. Raise it as a follow-up.
- **Finished components.** See Task 11 — the primitives are deliberately minimal.
- **The Favourites screen itself.** Task 5 adds the destination and routes it to Library's
  content. The real screen is a later slice.

## Already Done — Do Not Redo

Definition-of-done item 2 from the spec ("px→dp policy written into `docs/aaos-DESIGN.md`") **is already complete**, delivered in commit `333cc0c`. `docs/aaos-DESIGN.md` now opens with a "Units" section giving the conversion rule and the 112dp mini-player correction. Do not re-add it.

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `core/common/src/test/java/com/example/nyasaplayer/core/common/ui/theme/BrandContrastTest.kt` | Guards the gold tokens against the white-on-gold mistake |
| `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/UxFlags.kt` | Mirrored platform flag constants + the pure mapping function |
| `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/UxFlagsTest.kt` | Exhaustive tests for the mapping |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarUiLocation.kt` | The single value describing where the user is |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarRestrictionGate.kt` | `gate()` — pure allow/deny decision |
| `automotive/src/test/java/com/example/nyasaplayer/auto/ui/navigation/CarRestrictionGateTest.kt` | Gate matrix + eviction tests |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarRestrictionDialog.kt` | "Not available while driving" affordance |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarPrimitives.kt` | `Modifier.carTouchTarget()` |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarControls.kt` | `CarChip`, `CarPillButton`, `CarSectionHeader` |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarTrackRow.kt` | Track list row |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarEmptyState.kt` | Empty-state block |
| `automotive/src/oem/AndroidManifest.xml` | Declares the launcher activity and `distractionOptimized=true` metadata |
| `automotive/src/playstore/AndroidManifest.xml` | Declares no launcher activity |
| `docs/AAOS_DRIVING_STATE_TESTING.md` | adb recipe for driving state |

**Modified:**

| File | Change |
|---|---|
| `core/common/build.gradle.kts` | Add `testImplementation(libs.junit)` |
| `core/common/.../ui/theme/Color.kt` | Add four gold tokens |
| `automotive/build.gradle.kts` | Add `testImplementation(libs.junit)`, product flavors |
| `automotive/.../ui/theme/AutomotiveColors.kt` | Add car surface tokens, drop stale comment |
| `automotive/.../ui/theme/AutomotiveDimens.kt` | Add 6 tokens, change corner radius |
| `automotive/.../viewmodel/CarUxRestrictionsHandler.kt` | Use the new mapping; fix reconnect |
| `automotive/.../ui/navigation/CarScreen.kt` | Add `Favourites` |
| `automotive/.../ui/AutomotiveApp.kt` | Derive `CarUiLocation`, apply gate + eviction |
| `automotive/src/main/AndroidManifest.xml` | Move the launcher activity out to `src/oem/` |

---

### Task 1: Establish the driving-state adb recipe

This is a **spike**, not TDD. It blocks acceptance of the whole slice: without it, nothing built in Tasks 3–10 can be verified end to end. Do it first so a failure is discovered now rather than after ten tasks of work.

**Files:**
- Create: `docs/AAOS_DRIVING_STATE_TESTING.md`

**Interfaces:**
- Consumes: nothing
- Produces: a documented, repeatable way to put the emulator into driving state, referenced by the manual checklist in Task 10

- [ ] **Step 1: Start the automotive emulator**

Use the AVD named `Automotive_Distant_Display_with_Google_Play` (API 33). It is the one already used for AAOS verification in this project.

```bash
emulator -avd Automotive_Distant_Display_with_Google_Play -no-snapshot-load &
adb wait-for-device
adb shell getprop sys.boot_completed
```

Expected: `1`

- [ ] **Step 2: Read the current UX restriction state**

```bash
adb shell dumpsys car_service --services CarUxRestrictionsManagerService
```

Expected: output containing the current restrictions and a `requiresDistractionOptimization` value. Record the parked baseline.

- [ ] **Step 3: Try to inject a driving state**

Attempt these in order and record which works. They differ by system image, which is exactly why this is a spike:

```bash
# Option A — the car service's own injection command
adb shell cmd car_service inject-vhal-event 0x11600207 40

# Option B — gear + speed, if A is rejected
adb shell cmd car_service inject-vhal-event PERF_VEHICLE_SPEED 40
adb shell cmd car_service inject-vhal-event GEAR_SELECTION 8

# Option C — enumerate what this image supports, then pick from the list
adb shell cmd car_service -h
```

- [ ] **Step 4: Confirm the state actually changed**

```bash
adb shell dumpsys car_service --services CarUxRestrictionsManagerService
```

Expected: `requiresDistractionOptimization` flips to `true` and the restriction flags become non-zero.

- [ ] **Step 5: Confirm you can return to parked**

```bash
adb shell cmd car_service inject-vhal-event PERF_VEHICLE_SPEED 0
adb shell dumpsys car_service --services CarUxRestrictionsManagerService
```

Expected: back to the Step 2 baseline.

- [ ] **Step 6: Write the recipe down**

Create `docs/AAOS_DRIVING_STATE_TESTING.md` containing: the AVD name, the exact command that worked, the dumpsys command used as the oracle, the expected before/after output, and the command to return to parked.

**If no command works**, write that down instead — state which options were tried, the exact error output for each, and that restriction behaviour is therefore unverifiable on this image. That is a legitimate deliverable for this task. Report it before starting Task 2, because it changes what "done" means for Task 10.

- [ ] **Step 7: Commit**

```bash
git add docs/AAOS_DRIVING_STATE_TESTING.md
git commit -m "docs: record adb recipe for AAOS driving-state testing"
```

---

### Task 2: Gold brand tokens in :core:common

**Files:**
- Modify: `core/common/build.gradle.kts`
- Modify: `core/common/src/main/java/com/example/nyasaplayer/core/common/ui/theme/Color.kt`
- Test: `core/common/src/test/java/com/example/nyasaplayer/core/common/ui/theme/BrandContrastTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `NyasaGold`, `NyasaGoldDim`, `NyasaGoldBright`, `NyasaOnGold` — all `androidx.compose.ui.graphics.Color`, used by Tasks 7, 9, 11

`core/common` has **no test source set today**. This task creates it.

- [ ] **Step 1: Add the test dependency**

In `core/common/build.gradle.kts`, inside the existing `dependencies { }` block, add:

```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 2: Write the failing test**

Create `core/common/src/test/java/com/example/nyasaplayer/core/common/ui/theme/BrandContrastTest.kt`:

```kotlin
package com.example.nyasaplayer.core.common.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Guards the brand accent against the white-on-gold mistake.
 *
 * Gold is a light colour. White text on it measures 2.29:1, which is unusable.
 * [NyasaOnGold] exists so nobody reaches for Color.White out of habit.
 */
class BrandContrastTest {

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= LOW_CHANNEL_CUTOFF) v / LOW_CHANNEL_DIVISOR else ((v + OFFSET) / SCALE).pow(GAMMA)
    }

    private fun luminance(color: Color): Double =
        R_WEIGHT * channel(color.red) + G_WEIGHT * channel(color.green) + B_WEIGHT * channel(color.blue)

    private fun contrast(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + AMBIENT) / (minOf(a, b) + AMBIENT)
    }

    @Test
    fun onGold_overGold_meetsAaaForNormalText() {
        assertTrue(
            "NyasaOnGold on NyasaGold must be >= 7:1",
            contrast(NyasaOnGold, NyasaGold) >= AAA_NORMAL,
        )
    }

    @Test
    fun white_overGold_failsAa_whichIsWhyOnGoldExists() {
        assertTrue(
            "White on gold must fail AA — if this passes, the gold token changed",
            contrast(Color.White, NyasaGold) < AA_NORMAL,
        )
    }

    @Test
    fun onGold_overGoldBright_meetsAaaForNormalText() {
        assertTrue(
            "NyasaOnGold on NyasaGoldBright must be >= 7:1",
            contrast(NyasaOnGold, NyasaGoldBright) >= AAA_NORMAL,
        )
    }

    private companion object {
        const val LOW_CHANNEL_CUTOFF = 0.03928
        const val LOW_CHANNEL_DIVISOR = 12.92
        const val OFFSET = 0.055
        const val SCALE = 1.055
        const val GAMMA = 2.4
        const val R_WEIGHT = 0.2126
        const val G_WEIGHT = 0.7152
        const val B_WEIGHT = 0.0722
        const val AMBIENT = 0.05
        const val AAA_NORMAL = 7.0
        const val AA_NORMAL = 4.5
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :core:common:testDebugUnitTest --tests '*BrandContrastTest*'
```

Expected: FAIL to **compile**, with unresolved references to `NyasaGold`, `NyasaGoldBright`, `NyasaOnGold`.

- [ ] **Step 4: Add the tokens**

In `core/common/src/main/java/com/example/nyasaplayer/core/common/ui/theme/Color.kt`, below the existing `NyasaPrimary` / `NyasaPrimaryDark` declarations (leave those untouched — mobile migrates in Project B):

```kotlin
// --- Champagne gold: AAOS brand accent -------------------------------------
// Added for the AAOS design system. Mobile still uses NyasaPrimary; it migrates
// to gold in Project B. See docs/superpowers/specs/2026-08-02-aaos-foundation-restrictions-design.md 1.2
//
// Gold is a LIGHT colour. Never put white text on it — that measures 2.29:1.
// Always use NyasaOnGold for labels on a gold fill.
val NyasaGold = Color(0xFFC9A84C)
val NyasaGoldDim = Color(0xFF7A6428)
val NyasaGoldBright = Color(0xFFE0C169)
val NyasaOnGold = Color(0xFF0A0A0C)
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :core:common:testDebugUnitTest --tests '*BrandContrastTest*'
```

Expected: PASS, 3 tests.

If it fails with a `java.lang.RuntimeException: Stub!` or a `NoClassDefFoundError` on `Color`, add `testImplementation(libs.androidx.ui.graphics)` to `core/common/build.gradle.kts` and re-run. `androidx.compose.ui.graphics.Color` is a value class over a `ULong` and does not touch the Android framework, so this is unlikely, but the dependency may not be on the test classpath.

- [ ] **Step 6: Run Detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL. (The test file is not scanned; `Color.kt` is.)

- [ ] **Step 7: Commit**

```bash
git add core/common/build.gradle.kts \
        core/common/src/main/java/com/example/nyasaplayer/core/common/ui/theme/Color.kt \
        core/common/src/test/java/com/example/nyasaplayer/core/common/ui/theme/BrandContrastTest.kt
git commit -m "feat: add champagne gold brand tokens with contrast guard"
```

---

### Task 3: Pure UX restriction mapping

**Files:**
- Modify: `automotive/build.gradle.kts`
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/UxFlags.kt`
- Test: `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/UxFlagsTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `UxRestrictionState(noTextEntry, noSetup, noVideo, noFiltering, maxContentDepth, maxCumulativeContentItems, requiresDistractionOptimization)` with `isDistractionOptimized: Boolean`
  - `internal fun toUxState(activeRestrictions: Int, requiresDistractionOptimization: Boolean, maxContentDepth: Int, maxCumulativeContentItems: Int): UxRestrictionState`
  - `internal object UxFlags` with `NO_FILTERING`, `NO_KEYBOARD`, `NO_VIDEO`, `NO_SETUP`, `NO_TEXT_MESSAGE`
  - Used by Tasks 4, 6, 8, 10

**Why the signature takes raw values.** `android.car.jar` is `compileOnly`, so `CarUxRestrictions` is not on the unit-test classpath at all. Even if it were, every stub method body is `throw new RuntimeException("Stub!")` — verified by disassembly. Setting `testOptions.unitTests.isReturnDefaultValues = true` would silence the throw but make every getter return `0`/`false`, so the suite would pass while asserting against fabricated data. Taking `Int` and `Boolean` sidesteps all of it.

`automotive` has **no test source set today**. This task creates it.

- [ ] **Step 1: Add the test dependency**

In `automotive/build.gradle.kts`, inside `dependencies { }`, add:

```kotlin
    testImplementation(libs.junit)
```

Do **not** add `android.car.jar` to the test classpath.

- [ ] **Step 2: Write the failing test**

Create `automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/UxFlagsTest.kt`:

```kotlin
package com.example.nyasaplayer.auto.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform type CarUxRestrictions is compileOnly and its stubs throw, so the
 * mapping is tested through raw values instead. Flag literals are mirrored in
 * [UxFlags] and verified against the platform at runtime by assertUxFlagsMatchPlatform().
 */
class UxFlagsTest {

    private fun map(
        flags: Int = 0,
        distractionOptimized: Boolean = false,
        depth: Int = Int.MAX_VALUE,
        items: Int = Int.MAX_VALUE,
    ) = toUxState(flags, distractionOptimized, depth, items)

    // --- the regression this whole task exists for ---

    @Test
    fun noTextMessageFlagAlone_doesNotSetNoTextEntry() {
        val state = map(flags = UxFlags.NO_TEXT_MESSAGE)
        assertFalse(
            "NO_TEXT_MESSAGE is the messaging restriction, not the keyboard one",
            state.noTextEntry,
        )
    }

    @Test
    fun noKeyboardFlag_setsNoTextEntry() {
        assertTrue(map(flags = UxFlags.NO_KEYBOARD).noTextEntry)
    }

    // --- individual flags ---

    @Test
    fun noSetupFlag_setsNoSetup() {
        assertTrue(map(flags = UxFlags.NO_SETUP).noSetup)
    }

    @Test
    fun noVideoFlag_setsNoVideo() {
        assertTrue(map(flags = UxFlags.NO_VIDEO).noVideo)
    }

    @Test
    fun noFilteringFlag_setsNoFiltering() {
        assertTrue(map(flags = UxFlags.NO_FILTERING).noFiltering)
    }

    @Test
    fun baselineFlags_setNothing() {
        val state = map(flags = 0)
        assertFalse(state.noTextEntry)
        assertFalse(state.noSetup)
        assertFalse(state.noVideo)
        assertFalse(state.noFiltering)
    }

    @Test
    fun combinedFlags_setAllMatchingFields() {
        val state = map(flags = UxFlags.NO_KEYBOARD or UxFlags.NO_SETUP or UxFlags.NO_VIDEO)
        assertTrue(state.noTextEntry)
        assertTrue(state.noSetup)
        assertTrue(state.noVideo)
        assertFalse(state.noFiltering)
    }

    // --- caps pass through ---

    @Test
    fun contentCaps_passThroughUnchanged() {
        val state = map(depth = 1, items = 21)
        assertEquals(1, state.maxContentDepth)
        assertEquals(21, state.maxCumulativeContentItems)
    }

    // --- distraction optimization comes from the platform, not derived ---

    @Test
    fun isDistractionOptimized_followsPlatformValue_notFlags() {
        assertTrue(map(flags = 0, distractionOptimized = true).isDistractionOptimized)
        assertFalse(
            "must not be re-derived by ORing flags",
            map(flags = UxFlags.NO_KEYBOARD or UxFlags.NO_FILTERING, distractionOptimized = false)
                .isDistractionOptimized,
        )
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew :automotive:testDebugUnitTest --tests '*UxFlagsTest*'
```

Expected: FAIL to compile — `toUxState` and `UxFlags` unresolved.

- [ ] **Step 4: Write the implementation**

Create `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/UxFlags.kt`:

```kotlin
package com.example.nyasaplayer.auto.viewmodel

/**
 * Distraction rules for the current vehicle state.
 *
 * [isDistractionOptimized] comes from the platform's own answer rather than being
 * re-derived from flags, which is both indirect and wrong when a flag changes meaning.
 */
data class UxRestrictionState(
    val noTextEntry: Boolean = false,
    val noSetup: Boolean = false,
    val noVideo: Boolean = false,
    val noFiltering: Boolean = false,
    val maxContentDepth: Int = Int.MAX_VALUE,
    val maxCumulativeContentItems: Int = Int.MAX_VALUE,
    val requiresDistractionOptimization: Boolean = false,
) {
    val isDistractionOptimized: Boolean get() = requiresDistractionOptimization
}

/**
 * Mirrors android.car.drivingstate.CarUxRestrictions flag values.
 *
 * Mirrored rather than referenced because android.car.jar is compileOnly and absent
 * from the unit-test classpath. Values verified against the SDK stub;
 * assertUxFlagsMatchPlatform() re-checks them on device at connect time.
 */
internal object UxFlags {
    const val NO_FILTERING = 2
    const val NO_KEYBOARD = 8
    const val NO_VIDEO = 16
    const val NO_SETUP = 64
    const val NO_TEXT_MESSAGE = 128
}

/**
 * Pure mapping from raw restriction values to [UxRestrictionState].
 *
 * Takes primitives so it can be unit tested without the platform type.
 */
internal fun toUxState(
    activeRestrictions: Int,
    requiresDistractionOptimization: Boolean,
    maxContentDepth: Int,
    maxCumulativeContentItems: Int,
): UxRestrictionState = UxRestrictionState(
    noTextEntry = activeRestrictions and UxFlags.NO_KEYBOARD != 0,
    noSetup = activeRestrictions and UxFlags.NO_SETUP != 0,
    noVideo = activeRestrictions and UxFlags.NO_VIDEO != 0,
    noFiltering = activeRestrictions and UxFlags.NO_FILTERING != 0,
    maxContentDepth = maxContentDepth,
    maxCumulativeContentItems = maxCumulativeContentItems,
    requiresDistractionOptimization = requiresDistractionOptimization,
)
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :automotive:testDebugUnitTest --tests '*UxFlagsTest*'
```

Expected: PASS, 9 tests.

- [ ] **Step 6: Run Detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL. If `MagicNumber` fires on the flag literals, they are already `const val` inside an object, which the default config allows — if it still fires, add `@Suppress("MagicNumber")` on the `UxFlags` object with a comment saying the values are platform-defined.

- [ ] **Step 7: Commit**

```bash
git add automotive/build.gradle.kts \
        automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/UxFlags.kt \
        automotive/src/test/java/com/example/nyasaplayer/auto/viewmodel/UxFlagsTest.kt
git commit -m "feat: add testable UX restriction mapping over raw flag values"
```

---

### Task 4: Wire the fixed mapping into CarUxRestrictionsHandler

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/CarUxRestrictionsHandler.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt:239`

**Interfaces:**
- Consumes: `toUxState()`, `UxFlags`, `UxRestrictionState` from Task 3
- Produces: `CarUxRestrictionsHandler.restrictions: StateFlow<UxRestrictionState>` carrying the corrected fields; consumed by Task 10

This task deletes the old `UxRestrictionState` and the old private `toState()` from `CarUxRestrictionsHandler.kt` — they are replaced by Task 3's versions in `UxFlags.kt`.

**The bug being fixed:** the old mapping read `UX_RESTRICTIONS_NO_TEXT_MESSAGE` (the *messaging* restriction, value 128) for `noTextEntry`. The keyboard restriction is `UX_RESTRICTIONS_NO_KEYBOARD` (value 8). `AutomotiveApp.kt:270` gates search on the derived `isDistractionOptimized`, so search is currently gated on the wrong signal.

- [ ] **Step 1: Delete the old state class and mapping**

In `CarUxRestrictionsHandler.kt`, delete the entire `data class UxRestrictionState(...)` block near the top, and the entire `private fun CarUxRestrictions.toState(): UxRestrictionState` block at the bottom of the file. Both are superseded by `UxFlags.kt`.

- [ ] **Step 2: Add the platform wrapper and the flag guard**

At the bottom of `CarUxRestrictionsHandler.kt`, add:

```kotlin
/**
 * Thin platform adapter. Holds no logic — see [toUxState] for the tested mapping.
 */
private fun CarUxRestrictions.toUxState(): UxRestrictionState = toUxState(
    activeRestrictions = activeRestrictions,
    requiresDistractionOptimization = isRequiresDistractionOptimization,
    maxContentDepth = maxContentDepth,
    maxCumulativeContentItems = maxCumulativeContentItems,
)

/**
 * Fails loudly on device if the mirrored literals in [UxFlags] ever drift from the
 * platform. They cannot be referenced directly from unit tests, so this is where
 * they are proven correct.
 */
private fun assertUxFlagsMatchPlatform() {
    check(UxFlags.NO_FILTERING == CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING) { "NO_FILTERING drift" }
    check(UxFlags.NO_KEYBOARD == CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD) { "NO_KEYBOARD drift" }
    check(UxFlags.NO_VIDEO == CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) { "NO_VIDEO drift" }
    check(UxFlags.NO_SETUP == CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP) { "NO_SETUP drift" }
    check(UxFlags.NO_TEXT_MESSAGE == CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE) { "NO_TEXT_MESSAGE drift" }
}
```

- [ ] **Step 3: Call the guard and the new mapping, and fix the reconnect bug**

Replace the body of `connect()` with:

```kotlin
    @Suppress("TooGenericExceptionCaught")
    fun connect() {
        if (restrictionsManager != null) return
        assertUxFlagsMatchPlatform()
        try {
            val carInstance = car ?: Car.createCar(context) ?: return
            car = carInstance
            val manager = carInstance.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                as? CarUxRestrictionsManager ?: return
            restrictionsManager = manager
            _restrictions.value = manager.currentCarUxRestrictions.toUxState()
            val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                _restrictions.value = restrictions.toUxState()
            }
            listener = uxListener
            manager.registerListener(uxListener)
        } catch (e: Exception) {
            Log.e("CarUxRestrictions", "Failed to connect to Car service", e)
            car?.disconnect()
            car = null
            restrictionsManager = null
        }
    }
```

The guard was `if (car != null) return`, which made a failed first connect permanent for the process lifetime: `car` was set but `restrictionsManager` was left null, and every later `connect()` returned immediately. Guarding on `restrictionsManager` instead lets a later attempt succeed. A restriction layer that fails open, silently, is the failure mode that matters most here.

- [ ] **Step 4: Update the renamed field at the call site**

`automotive/.../ui/AutomotiveApp.kt:239` reads:

```kotlin
    val maxItems = playerState.restrictions.limitedContentItems
```

Change to:

```kotlin
    val maxItems = playerState.restrictions.maxCumulativeContentItems
```

- [ ] **Step 5: Build to verify it compiles**

```bash
./gradlew :automotive:assembleDebug
```

Expected: BUILD SUCCESSFUL. If any other reference to `limitedContentItems` fails, update it the same way.

- [ ] **Step 6: Re-run the unit tests**

```bash
./gradlew :automotive:testDebugUnitTest
```

Expected: PASS, 9 tests (unchanged — this task adds no tests, the mapping is already covered).

- [ ] **Step 7: Run Detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/CarUxRestrictionsHandler.kt \
        automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt
git commit -m "fix: gate text entry on NO_KEYBOARD, not NO_TEXT_MESSAGE"
```

---

### Task 5: CarScreen.Favourites and CarUiLocation

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarScreen.kt`
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarUiLocation.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt` (the `when` branch, Step 3)

**Interfaces:**
- Consumes: nothing
- Produces:
  - `CarScreen.Favourites`
  - `enum class CarOverlay { FullPlayer, Queue }`
  - `enum class CarSheet { Settings, Profile, Search }`
  - `data class CarUiLocation(tab: CarScreen, overlay: CarOverlay?, drillDepth: Int, sheet: CarSheet?, textEntryActive: Boolean)` with `fun tabRoot(): CarUiLocation`
  - Used by Tasks 6 and 10

Where the user is currently lives in five places across three owners: `currentScreen`, `showFullPlayer`, `showQueue`, `selectedArtist` (all `rememberSaveable` in `AutomotiveApp.kt:83-87`) and `contentState.searchQuery` in `AutomotiveContentViewModel`. This task introduces one value derived from those; it does not replace them, so existing screens keep working.

- [ ] **Step 1: Add the Favourites destination**

Replace the contents of `CarScreen.kt`:

```kotlin
package com.example.nyasaplayer.auto.ui.navigation

enum class CarScreen(val route: String) : java.io.Serializable {
    Home("home"),
    Browse("browse"),
    Library("library"),
    Favourites("favourites"),
}
```

The design's navigation rail has four items; the enum had three.

- [ ] **Step 2: Create the location model**

Create `automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarUiLocation.kt`:

```kotlin
package com.example.nyasaplayer.auto.ui.navigation

/** Conditional overlays shown above a tab. Not navigation destinations. */
enum class CarOverlay { FullPlayer, Queue }

/** Full-screen sheets reached from the system bar rather than the rail. */
enum class CarSheet { Settings, Profile, Search }

/**
 * Where the user is, as a single value.
 *
 * Derived from state that lives in three different owners: local rememberSaveable
 * values in AutomotiveApp and the search query in AutomotiveContentViewModel.
 * Derived rather than authoritative, so existing screens keep working unchanged.
 */
data class CarUiLocation(
    val tab: CarScreen,
    val overlay: CarOverlay? = null,
    val drillDepth: Int = 0,
    val sheet: CarSheet? = null,
    val textEntryActive: Boolean = false,
) {
    /**
     * The root of the tab the user is already on.
     *
     * This is the eviction target. Chosen over evicting to Home, which moves the
     * user somewhere they did not choose, and over evicting to the previous
     * location, which can land on another restricted location and loop.
     */
    fun tabRoot(): CarUiLocation = CarUiLocation(tab = tab)
}
```

- [ ] **Step 3: Add the required `when` branch — this is mandatory, not conditional**

`AutomotiveApp.kt:247` has `when (currentScreen)` over `CarScreen`. Since Kotlin 1.7 a
non-exhaustive `when` **statement** over an enum is a compile error, not a warning, and this
project is on Kotlin 2.0.21. Adding the enum constant **will break the build** until this
branch exists.

In that `when`, after the `CarScreen.Library ->` branch, add exactly this — the argument
list is copied verbatim from the existing `CarScreen.Library ->` branch at
`AutomotiveApp.kt:294`, which is the only correct source for it:

```kotlin
                CarScreen.Favourites -> CarLibraryScreen(
                    favoriteArtists = contentState.favoriteArtists.take(maxItems),
                    albums = contentState.albums.take(maxItems),
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                    likedSongs = contentState.likedSongs.take(maxItems),
                    currentlyPlayingMediaId = currentlyPlayingMediaId,
                    isPlaying = isPlaying,
                    onShuffleLikedSongs = onShuffleLikedSongs,
                    onLikedSongClick = onLikedSongClick,
                    onSignOut = onSignOut,
                    userDisplayName = userDisplayName,
                )
```

Favourites routes to the same content as Library for now; the real Favourites screen is a
later slice. If the `CarScreen.Library ->` branch has changed since this plan was written,
copy from it rather than from the block above — it is the source of truth, not this plan.

- [ ] **Step 4: Build to verify it compiles**

```bash
./gradlew :automotive:assembleDebug
```

Expected: BUILD SUCCESSFUL. If it fails with "'when' expression must be exhaustive", the
branch above is missing or misplaced.

- [ ] **Step 5: Run Detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/ \
        automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt
git commit -m "feat: add Favourites destination and CarUiLocation model"
```

---

### Task 6: The restriction gate

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarRestrictionGate.kt`
- Test: `automotive/src/test/java/com/example/nyasaplayer/auto/ui/navigation/CarRestrictionGateTest.kt`

**Interfaces:**
- Consumes: `CarUiLocation`, `CarOverlay`, `CarSheet`, `CarScreen` (Task 5); `UxRestrictionState` (Task 3)
- Produces:
  - `sealed interface GateResult` with `GateResult.Allowed` and `GateResult.Denied(reason: String, evictTo: CarUiLocation)`
  - `fun gate(location: CarUiLocation, state: UxRestrictionState): GateResult`
  - Used by Task 10

- [ ] **Step 1: Write the failing test**

Create `automotive/src/test/java/com/example/nyasaplayer/auto/ui/navigation/CarRestrictionGateTest.kt`:

```kotlin
package com.example.nyasaplayer.auto.ui.navigation

import com.example.nyasaplayer.auto.viewmodel.UxRestrictionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarRestrictionGateTest {

    private val parked = UxRestrictionState()

    private val driving = UxRestrictionState(
        noTextEntry = true,
        noSetup = true,
        noFiltering = true,
        maxContentDepth = 1,
        maxCumulativeContentItems = 21,
        requiresDistractionOptimization = true,
    )

    private fun at(
        tab: CarScreen = CarScreen.Home,
        overlay: CarOverlay? = null,
        drillDepth: Int = 0,
        sheet: CarSheet? = null,
        textEntryActive: Boolean = false,
    ) = CarUiLocation(tab, overlay, drillDepth, sheet, textEntryActive)

    // --- parked permits everything ---

    @Test
    fun parked_allowsEveryLocation() {
        val locations = listOf(
            at(),
            at(sheet = CarSheet.Settings),
            at(sheet = CarSheet.Profile),
            at(sheet = CarSheet.Search, textEntryActive = true),
            at(drillDepth = 2),
            at(overlay = CarOverlay.FullPlayer),
            at(overlay = CarOverlay.Queue),
        )
        locations.forEach { location ->
            assertEquals("$location must be allowed while parked", GateResult.Allowed, gate(location, parked))
        }
    }

    // --- driving denies setup ---

    @Test
    fun driving_deniesSettings() {
        assertTrue(gate(at(sheet = CarSheet.Settings), driving) is GateResult.Denied)
    }

    @Test
    fun driving_deniesProfile() {
        assertTrue(gate(at(sheet = CarSheet.Profile), driving) is GateResult.Denied)
    }

    // --- driving denies text entry but not browsing search ---

    @Test
    fun driving_deniesSearchWithTextEntry() {
        assertTrue(gate(at(sheet = CarSheet.Search, textEntryActive = true), driving) is GateResult.Denied)
    }

    @Test
    fun driving_allowsSearchWithoutTextEntry() {
        assertEquals(GateResult.Allowed, gate(at(sheet = CarSheet.Search), driving))
    }

    // --- driving denies drilling past the cap ---

    @Test
    fun driving_deniesDrillDepthAboveCap() {
        assertTrue(gate(at(drillDepth = 2), driving) is GateResult.Denied)
    }

    @Test
    fun driving_allowsDrillDepthAtCap() {
        assertEquals(GateResult.Allowed, gate(at(drillDepth = 1), driving))
    }

    // --- playback control stays available while driving ---

    @Test
    fun driving_allowsTabRoots() {
        CarScreen.entries.forEach { tab ->
            assertEquals(GateResult.Allowed, gate(at(tab = tab), driving))
        }
    }

    @Test
    fun driving_allowsFullPlayer() {
        assertEquals(GateResult.Allowed, gate(at(overlay = CarOverlay.FullPlayer), driving))
    }

    @Test
    fun driving_allowsQueueLocation_mutationIsGatedElsewhere() {
        // The queue may be VIEWED while driving, so the location is allowed.
        // Remove/reorder/clear are actions, gated by the screen reading UxRestrictionState —
        // see the spec, "Location gating vs action gating". Not this function's job.
        assertEquals(GateResult.Allowed, gate(at(overlay = CarOverlay.Queue), driving))
    }

    // --- eviction ---

    @Test
    fun denial_evictsToCurrentTabRoot_notHome() {
        val result = gate(at(tab = CarScreen.Library, sheet = CarSheet.Settings), driving)
        val denied = result as GateResult.Denied
        assertEquals(CarUiLocation(CarScreen.Library), denied.evictTo)
    }

    @Test
    fun evictionTarget_isItselfAllowed_soEvictionTerminates() {
        val denied = gate(at(tab = CarScreen.Browse, drillDepth = 3), driving) as GateResult.Denied
        assertEquals(GateResult.Allowed, gate(denied.evictTo, driving))
    }

    @Test
    fun denial_carriesANonEmptyReason() {
        val denied = gate(at(sheet = CarSheet.Settings), driving) as GateResult.Denied
        assertTrue(denied.reason.isNotBlank())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :automotive:testDebugUnitTest --tests '*CarRestrictionGateTest*'
```

Expected: FAIL to compile — `gate` and `GateResult` unresolved.

- [ ] **Step 3: Write the implementation**

Create `automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarRestrictionGate.kt`:

```kotlin
package com.example.nyasaplayer.auto.ui.navigation

import com.example.nyasaplayer.auto.viewmodel.UxRestrictionState

/** Outcome of asking whether a location may be shown under the current restrictions. */
sealed interface GateResult {
    data object Allowed : GateResult

    /**
     * The location is not permitted. [reason] is shown to the driver; [evictTo] is
     * where to send them, and is always itself allowed.
     */
    data class Denied(val reason: String, val evictTo: CarUiLocation) : GateResult
}

private const val REASON_SETTINGS = "Settings can only be changed while the vehicle is parked."
private const val REASON_PROFILE = "Profiles can only be switched while the vehicle is parked."
private const val REASON_TEXT_ENTRY =
    "Typing is unavailable while driving. Use voice search instead."
private const val REASON_DEPTH =
    "Browsing this far into your library is limited while the vehicle is moving."

/**
 * Decides whether [location] may be shown under [state].
 *
 * Entry refusal alone is not sufficient: a vehicle can start moving at any moment, so
 * callers must re-evaluate the current location whenever restrictions change and act on
 * [GateResult.Denied.evictTo]. See Task 10 in the implementation plan.
 *
 * Playback transport, seeking, queue view/skip-to and tab switching are never denied.
 *
 * This function gates LOCATIONS, not ACTIONS. Queue remove/reorder/clear and download
 * deletion are parked-only, but they are actions inside a permitted location, so they are
 * not expressible here. The owning screen reads UxRestrictionState.isDistractionOptimized
 * directly — see CarQueueScreen, which already does exactly this. Do not add action cases
 * to this function.
 */
fun gate(location: CarUiLocation, state: UxRestrictionState): GateResult {
    if (!state.isDistractionOptimized) return GateResult.Allowed

    val reason = when {
        state.noSetup && location.sheet == CarSheet.Settings -> REASON_SETTINGS
        state.noSetup && location.sheet == CarSheet.Profile -> REASON_PROFILE
        state.noTextEntry && location.sheet == CarSheet.Search && location.textEntryActive ->
            REASON_TEXT_ENTRY
        location.drillDepth > state.maxContentDepth -> REASON_DEPTH
        else -> null
    }

    return if (reason == null) GateResult.Allowed else GateResult.Denied(reason, location.tabRoot())
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :automotive:testDebugUnitTest --tests '*CarRestrictionGateTest*'
```

Expected: PASS, 13 tests.

- [ ] **Step 5: Run Detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/navigation/CarRestrictionGate.kt \
        automotive/src/test/java/com/example/nyasaplayer/auto/ui/navigation/CarRestrictionGateTest.kt
git commit -m "feat: add restriction gate with entry refusal and eviction target"
```

---

### Task 7: Car surface and dimension tokens

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/theme/AutomotiveColors.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/theme/AutomotiveDimens.kt`

**Interfaces:**
- Consumes: `NyasaGold` family from Task 2
- Produces: `CarObsidian`, `CarChrome`, `CarGlass`, `CarRaised`, `CarTextSecondary`, `CarTextDisabled`, `CarAmbientBlue`, `CarAmbientPurple`; `CarSystemBarHeight`, `CarNavRailWidth`, `CarChipHeight`, `CarPillButtonHeight`, `CarListRowHeight`, `CarScreenMargin` — used by Tasks 9 and 11

- [ ] **Step 1: Add the car surface tokens**

At the top of `AutomotiveColors.kt`, replace the stale comment line that reads
`// NyasaPrimary (0xFFA855F7) / NyasaPrimaryDark (0xFF7C3AED) are in core:common — use those directly.`
with:

```kotlin
// Brand accent lives in :core:common as NyasaGold / NyasaOnGold. Use those directly.
// The surfaces below are car-only — mobile has no obsidian surface.

/** Base background, edge to edge on every screen. */
val CarObsidian = Color(0xFF0A0A0C)

/** System bar and navigation rail. */
val CarChrome = Color(0xFF111118)

/** Cards and the mini-player. */
val CarGlass = Color(0xFF181824)

/** Elevated cards, inputs, chips. */
val CarRaised = Color(0xFF1E1E2A)

/**
 * Metadata and secondary labels.
 *
 * Do not darken without re-measuring. This was #A0A0B0 and gave only 6.8:1 on cards —
 * AA, not AAA. The binding surface is [CarRaised] at 7.4:1, NOT [CarObsidian] at 8.8:1,
 * so measuring against the base gives a false pass.
 */
val CarTextSecondary = Color(0xFFACACBC)

/** Disabled labels. Exempt from contrast minimums. */
val CarTextDisabled = Color(0xFF555568)

/** Ambient background tints. Never used as a fill on an interactive element. */
val CarAmbientBlue = Color(0xFF1A3A5C)
val CarAmbientPurple = Color(0x4D643CB4)
```

Leave the existing `CarGradient*` values untouched — they are content artwork gradients, not brand.

- [ ] **Step 2: Add the dimension tokens**

Replace the contents of `AutomotiveDimens.kt`:

```kotlin
package com.example.nyasaplayer.auto.ui.theme

import androidx.compose.ui.unit.dp

// All values are dp. The design document is authored in CSS px on a 1920x1080 canvas;
// see the "Units" section of docs/aaos-DESIGN.md for the conversion rule.

// CTS-compliant minimum touch target (76dp >= 76dp requirement)
val CarTouchTargetSize = 76.dp

// Standard album art / avatar thumbnail used in lists
val CarListArtSize = 80.dp

// Standard card corner radius
val CarCardCornerRadius = 20.dp

// Mini player bar height. Kept at 112 rather than the design's 88: this value predates
// the design, exceeds its intent, and clears the touch target with room.
val CarMiniPlayerHeight = 112.dp

// Top system bar. 80 and not 48 because it carries app-tappable controls (search,
// settings, avatar) and a 48dp bar cannot contain a 76dp target.
val CarSystemBarHeight = 80.dp

// Left navigation rail
val CarNavRailWidth = 80.dp

// Filter chip height
val CarChipHeight = 76.dp

// Pill button height
val CarPillButtonHeight = 76.dp

// Track / content list row height
val CarListRowHeight = 80.dp

// Screen edge margin
val CarScreenMargin = 48.dp
```

- [ ] **Step 3: Build to verify it compiles**

```bash
./gradlew :automotive:assembleDebug
```

Expected: BUILD SUCCESSFUL. `CarCardCornerRadius` changed from 16 to 20; existing screens using it will simply render slightly rounder.

- [ ] **Step 4: Run Detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/theme/
git commit -m "feat: add car surface and dimension tokens"
```

---

### Task 8: The touch target primitive

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarPrimitives.kt`

**Interfaces:**
- Consumes: `CarTouchTargetSize` (Task 7)
- Produces: `fun Modifier.carTouchTarget(): Modifier` — used by Tasks 9 and 11

In the HTML prototype, nine controls silently fell below the 76 minimum — the worst was a like button at 22x27 — all introduced by ordinary styling rather than carelessness. Compose will regress the same way without one enforced primitive.

- [ ] **Step 1: Create the modifier**

Create `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarPrimitives.kt`:

```kotlin
package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import com.example.nyasaplayer.auto.ui.theme.CarTouchTargetSize

/**
 * Enforces the minimum touch target on any interactive element.
 *
 * The glyph keeps its visual size; this supplies the target around it. Apply to every
 * clickable whose drawn size is smaller than [CarTouchTargetSize] — icon buttons,
 * hearts, transport controls, chevrons.
 *
 * Do not remove it to tighten spacing. Every sub-minimum target in the HTML prototype
 * was introduced exactly that way.
 */
fun Modifier.carTouchTarget(): Modifier =
    this.defaultMinSize(minWidth = CarTouchTargetSize, minHeight = CarTouchTargetSize)
```

- [ ] **Step 2: Build to verify it compiles**

```bash
./gradlew :automotive:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run Detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarPrimitives.kt
git commit -m "feat: add carTouchTarget modifier enforcing the 76dp minimum"
```

---

### Task 9: The restriction dialog

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarRestrictionDialog.kt`

**Interfaces:**
- Consumes: `NyasaGold`, `NyasaOnGold` (Task 2); `CarGlass`, `CarTextSecondary` (Task 7); `carTouchTarget()` (Task 8); `CarPillButtonHeight`, `CarCardCornerRadius` (Task 7)
- Produces: `@Composable fun CarRestrictionDialog(reason: String, onDismiss: () -> Unit, modifier: Modifier = Modifier)` — used by Task 10

Denials must explain themselves. A silent no-op reads as a broken app.

- [ ] **Step 1: Create the dialog**

Create `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarRestrictionDialog.kt`:

```kotlin
package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarCardCornerRadius
import com.example.nyasaplayer.auto.ui.theme.CarGlass
import com.example.nyasaplayer.auto.ui.theme.CarPillButtonHeight
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.common.ui.theme.NyasaOnGold

private const val SCRIM_ALPHA = 0.74f
private val DialogWidth = 780.dp
private val DialogPadding = 44.dp
private const val TITLE_SIZE = 40
private const val BODY_SIZE = 22
private const val BUTTON_LABEL_SIZE = 20

/**
 * Shown when [gate] refuses a location, and when the driver is evicted from one.
 *
 * Always carries a reason. A silent refusal reads as a broken app.
 */
@Composable
fun CarRestrictionDialog(
    reason: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(DialogWidth)
                .background(CarGlass, RoundedCornerShape(CarCardCornerRadius))
                .padding(DialogPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Not available while driving",
                color = Color.White,
                fontSize = TITLE_SIZE.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = reason,
                color = CarTextSecondary,
                fontSize = BODY_SIZE.sp,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .carTouchTarget()
                    .height(CarPillButtonHeight)
                    .background(NyasaGold, RoundedCornerShape(CarPillButtonHeight / 2))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Got it",
                    color = NyasaOnGold,
                    fontSize = BUTTON_LABEL_SIZE.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

```bash
./gradlew :automotive:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run Detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL. If `MagicNumber` fires on the `.dp` / `.sp` literals inside the composable, hoist each to a private top-level `val` at the top of the file following the pattern already there.

- [ ] **Step 4: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarRestrictionDialog.kt
git commit -m "feat: add restriction dialog for refused and evicted locations"
```

---

### Task 10: Wire the gate and eviction into AutomotiveApp

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt`

**Interfaces:**
- Consumes: `CarUiLocation`, `CarOverlay`, `CarSheet` (Task 5); `gate()`, `GateResult` (Task 6); `CarRestrictionDialog` (Task 9); `restrictions` (Task 4)
- Produces: the running behaviour verified by the manual checklist below

This is the task where entry refusal and eviction become real. Everything before it is inert.

- [ ] **Step 1: Add the denial state**

Alongside the existing `rememberSaveable` declarations at `AutomotiveApp.kt:83-87`, add:

```kotlin
    var denialReason by rememberSaveable { mutableStateOf<String?>(null) }
```

- [ ] **Step 2: Derive the current location**

Immediately after those declarations, add:

```kotlin
    val location = CarUiLocation(
        tab = currentScreen,
        overlay = when {
            showFullPlayer -> CarOverlay.FullPlayer
            showQueue -> CarOverlay.Queue
            else -> null
        },
        drillDepth = if (selectedArtist != null) 1 else 0,
        sheet = null,
        textEntryActive = contentState.searchQuery.isNotEmpty(),
    )
```

`sheet` is `null` for now: Settings and Profile are later slices, and Search is not yet a distinct sheet in this app. The field exists so those slices have nothing to retrofit.

- [ ] **Step 3: Evict when restrictions change**

After the `location` declaration, add:

```kotlin
    LaunchedEffect(playerState.restrictions, location) {
        when (val result = gate(location, playerState.restrictions)) {
            is GateResult.Allowed -> Unit
            is GateResult.Denied -> {
                showFullPlayer = false
                showQueue = false
                selectedArtist = null
                currentScreen = result.evictTo.tab
                denialReason = result.reason
            }
        }
    }
```

Keying the effect on both the restrictions and the location means it fires when the vehicle starts moving *and* when the user navigates. Gating entry alone is not enough — a vehicle can start moving while the driver is already inside a restricted screen.

Add the import for `androidx.compose.runtime.LaunchedEffect` if it is not already present.

- [ ] **Step 4: Show the dialog**

At the end of the outer `Box` in `AutomotiveApp`, after the existing `CarErrorOverlay` block, add:

```kotlin
        val reason = denialReason
        if (reason != null) {
            CarRestrictionDialog(
                reason = reason,
                onDismiss = { denialReason = null },
            )
        }
```

- [ ] **Step 5: Build and install**

```bash
./gradlew :automotive:assembleDebug
adb install -r automotive/build/outputs/apk/debug/automotive-debug.apk
```

Expected: BUILD SUCCESSFUL, `Success`.

If the APK path differs because flavors are not yet added, use `find automotive/build/outputs/apk -name '*.apk'` to locate it.

- [ ] **Step 6: Run the unit tests**

```bash
./gradlew :automotive:testDebugUnitTest
```

Expected: PASS, 22 tests (9 from Task 3 + 13 from Task 6).

- [ ] **Step 7: Manual verification against the emulator**

Using the recipe from Task 1:

1. Launch the app while parked. Drill into an artist from Library. Expect: allowed.
2. With the artist detail open, inject the driving state. Expect: you are returned to the Library root and `CarRestrictionDialog` appears explaining why.
3. Dismiss the dialog. Attempt the same drill-down while still driving. Expect: refused, dialog appears again.
4. Return to parked. Expect: the drill-down works again.
5. While driving, confirm play/pause, skip, seek, queue view/skip-to and tab switching all still work. **These must not be blocked.** Queue remove/reorder/clear stays parked-only.

Record the outcome. If Task 1 concluded that no driving-state injection works on this image, state explicitly that steps 2–4 could not be verified, and that the restriction layer rests on unit tests alone.

- [ ] **Step 8: Run Detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt
git commit -m "feat: enforce driving restrictions with entry refusal and eviction"
```

---

### Task 11: Car component primitives

**Files:**
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarControls.kt`
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarTrackRow.kt`
- Create: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarEmptyState.kt`

**Interfaces:**
- Consumes: `NyasaGold`, `NyasaOnGold` (Task 2); car tokens (Task 7); `carTouchTarget()` (Task 8)
- Produces: `CarChip`, `CarPillButton`, `CarSectionHeader`, `CarTrackRow`, `CarEmptyState` — consumed by the screen slices that follow A1

**These are minimal primitives, not finished components.** They establish the token usage,
the touch-target discipline and the call signatures. Deliberately absent, and belonging to
the screen slices that consume them: real artwork loading in `CarTrackRow` (it draws a
placeholder box), text overflow and ellipsis handling, per-row like and overflow
affordances, and the decorative orb in `CarEmptyState`. Do not add them here — a screen
slice will know what shape they need to take.

Every composable here takes `modifier: Modifier = Modifier` as its first optional parameter. Detekt's `ModifierMissing` rule fails the build otherwise.

- [ ] **Step 1: Create the controls**

Create `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarControls.kt`:

```kotlin
package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarChipHeight
import com.example.nyasaplayer.auto.ui.theme.CarPillButtonHeight
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.common.ui.theme.NyasaOnGold

private val ChipPadding = 28.dp
private val ButtonPadding = 36.dp
private const val CHIP_LABEL_SIZE = 19
private const val BUTTON_LABEL_SIZE = 20
private const val SECTION_LABEL_SIZE = 22
private const val UNSELECTED_BORDER_ALPHA = 0.12f

/**
 * Filter chip. Selection is shown by gold fill and label colour only — never by
 * rendering the word "ACTIVE" or "SELECTED" as visible text.
 */
@Composable
fun CarChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .carTouchTarget()
            .height(CarChipHeight)
            .background(
                color = if (selected) NyasaGold else CarRaised,
                shape = RoundedCornerShape(CarChipHeight / 2),
            )
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else Color.White.copy(alpha = UNSELECTED_BORDER_ALPHA),
                shape = RoundedCornerShape(CarChipHeight / 2),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = ChipPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) NyasaOnGold else Color.White,
            fontSize = CHIP_LABEL_SIZE.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * Primary or secondary pill button.
 *
 * The gold variant uses [NyasaOnGold] for its label. Never white — white on gold
 * measures 2.29:1.
 */
@Composable
fun CarPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    Box(
        modifier = modifier
            .carTouchTarget()
            .height(CarPillButtonHeight)
            .background(
                color = if (filled) NyasaGold else Color.Transparent,
                shape = RoundedCornerShape(CarPillButtonHeight / 2),
            )
            .border(
                width = if (filled) 0.dp else 1.dp,
                color = if (filled) Color.Transparent else Color.White.copy(alpha = UNSELECTED_BORDER_ALPHA),
                shape = RoundedCornerShape(CarPillButtonHeight / 2),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = ButtonPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (filled) NyasaOnGold else Color.White,
            fontSize = BUTTON_LABEL_SIZE.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Section heading above a content row. */
@Composable
fun CarSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        color = Color.White,
        fontSize = SECTION_LABEL_SIZE.sp,
        fontWeight = FontWeight.Bold,
    )
}
```

- [ ] **Step 2: Create the track row**

Create `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarTrackRow.kt`:

```kotlin
package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarListRowHeight
import com.example.nyasaplayer.auto.ui.theme.CarRaised
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold

private val ArtSize = 52.dp
private val ArtRadius = 8.dp
private val PlayingBarWidth = 3.dp
private val RowSpacing = 16.dp
private const val TITLE_SIZE = 18
private const val ARTIST_SIZE = 15
private const val DURATION_SIZE = 16

/**
 * One track in a list.
 *
 * The currently playing row carries a gold bar on its left edge and a gold title.
 */
@Composable
fun CarTrackRow(
    title: String,
    artist: String,
    duration: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CarListRowHeight)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        Box(
            modifier = Modifier
                .width(PlayingBarWidth)
                .fillMaxHeight()
                .padding(vertical = 14.dp)
                .background(if (isPlaying) NyasaGold else Color.Transparent),
        )
        Box(
            modifier = Modifier
                .size(ArtSize)
                .clip(RoundedCornerShape(ArtRadius))
                .background(CarRaised),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isPlaying) NyasaGold else Color.White,
                fontSize = TITLE_SIZE.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = artist,
                color = CarTextSecondary,
                fontSize = ARTIST_SIZE.sp,
            )
        }
        Text(
            text = duration,
            color = CarTextSecondary,
            fontSize = DURATION_SIZE.sp,
        )
    }
}
```

- [ ] **Step 3: Create the empty state**

Create `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarEmptyState.kt`:

```kotlin
package com.example.nyasaplayer.auto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nyasaplayer.auto.ui.theme.CarTextSecondary

private val Spacing = 12.dp
private const val TITLE_SIZE = 40
private const val BODY_SIZE = 22

/**
 * Empty-state block: title, explanation, and an optional action.
 *
 * Pass [actionLabel] and [onAction] together or not at all.
 */
@Composable
fun CarEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = TITLE_SIZE.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            color = CarTextSecondary,
            fontSize = BODY_SIZE.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            CarPillButton(label = actionLabel, onClick = onAction)
        }
    }
}
```

- [ ] **Step 4: Build to verify it compiles**

```bash
./gradlew :automotive:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run Detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL. The most likely failures are `ModifierMissing` (every UI-emitting composable needs `modifier: Modifier = Modifier`) and `MagicNumber` (hoist any remaining literal to a named private val).

- [ ] **Step 6: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/
git commit -m "feat: add car chip, pill button, track row and empty state"
```

---

### Task 12: Re-theme the existing components

**Files:**
- Modify: `automotive/.../ui/components/CarTopBar.kt`
- Modify: `automotive/.../ui/components/CarMiniPlayer.kt`
- Modify: `automotive/.../ui/components/CarErrorOverlay.kt`
- Modify: `automotive/.../ui/screens/CarHomeScreen.kt`
- Modify: `automotive/.../ui/screens/CarBrowseScreen.kt`
- Modify: `automotive/.../ui/screens/CarLibraryScreen.kt`
- Modify: `automotive/.../ui/screens/CarAuthScreen.kt`
- Modify: `automotive/.../ui/screens/CarFullPlayerScreen.kt`
- Modify: `automotive/.../ui/screens/CarQueueScreen.kt`
- Modify: `automotive/.../ui/screens/CarArtistLikedSongsScreen.kt`

**Ten files, not three.** All seven screens use `NyasaPrimary`, `NyasaSurface2` or
`NyasaTextSecondary` today — verified by grep. Definition-of-done item 11 requires them
re-themed, and Step 6's check fails otherwise.

**Interfaces:**
- Consumes: `NyasaGold`, `NyasaGoldDim`, `NyasaOnGold` (Task 2); `CarChrome`, `CarGlass`, `CarTextSecondary`, `CarSystemBarHeight` (Task 7); `carTouchTarget()` (Task 8)
- Produces: **one signature change** — `CarMiniPlayer` gains
  `onQueueClick: () -> Unit = {}` (Step 3). Defaulted and added after the existing optional
  parameters, so `modifier` stays the first optional and both current call sites
  (`AutomotiveApp.kt:312` and `CarScreenPreviews.kt:189`) keep compiling untouched.
  Every other component keeps its existing signature.

**Re-theme, do not rewrite.** All three work today. This task changes colours, one dimension, and one touch-target grouping. If you find yourself restructuring layout, stop — that belongs to a later screen slice.

The substitution is mechanical. Everywhere a purple gradient `Brush.linearGradient(listOf(NyasaPrimary, NyasaPrimaryDark))` or `Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark))` is used as a fill, replace it with a **solid** `NyasaGold`. Gold is a light colour and does not read as a gradient the way purple does. Any label or icon sitting on that fill changes from `Color.White` to `NyasaOnGold` — white on gold is 2.29:1.

- [ ] **Step 1: Re-theme CarTopBar**

In `CarTopBar.kt`:

1. Delete `private val TopBarHeight = 76.dp` (line 48) and replace every use of `TopBarHeight` with `CarSystemBarHeight`.
2. `.background(NyasaSurface2)` (line 62) becomes `.background(CarChrome)`.
3. The logo fill at line 84, `.background(Brush.linearGradient(listOf(NyasaPrimary, NyasaPrimaryDark)))`, becomes `.background(NyasaGold)`.
4. The selected-tab fill at line 160, `Modifier.background(Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark)), shape)`, becomes `Modifier.background(NyasaGold, shape)`.
5. Any `Color.White` tint or text colour that sits **on** one of those two fills becomes `NyasaOnGold`. Leave `Color.White` where it sits on `CarChrome`.
6. Update imports: remove `NyasaPrimary`, `NyasaPrimaryDark`, `NyasaSurface2`; add `com.example.nyasaplayer.auto.ui.theme.CarChrome`, `com.example.nyasaplayer.auto.ui.theme.CarSystemBarHeight`, `com.example.nyasaplayer.core.common.ui.theme.NyasaGold`, `com.example.nyasaplayer.core.common.ui.theme.NyasaOnGold`. Remove the `Brush` import if it becomes unused.

- [ ] **Step 2: Re-theme CarErrorOverlay**

In `CarErrorOverlay.kt`:

1. `.background(NyasaSurface2)` (line 74) becomes `.background(CarGlass)`.
2. The retry button fill at line 163, `.background(Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark)))`, becomes `.background(NyasaGold)`.
3. The retry icon tint at line 172, `tint = Color.White`, becomes `tint = NyasaOnGold`, along with the retry label's colour.
4. Update imports as in Step 1, using `CarGlass` instead of `CarChrome`.

- [ ] **Step 3: Re-theme CarMiniPlayer and merge its touch target**

In `CarMiniPlayer.kt`:

1. `NyasaSurface2` becomes `CarGlass`, `NyasaTextSecondary` becomes `CarTextSecondary`.
2. The play button fill becomes solid `NyasaGold`; its icon tint becomes `NyasaOnGold`.
3. Leave `CarMiniPlayerHeight`, `CarListArtSize` and `CarTouchTargetSize` as they are — they are already correct.
4. **Add the missing queue control.** The design's mini-player ends with a queue button; the
   current signature has play/prev/next/like and `onExpand` but no queue. Add a parameter
   with a default so no existing call site breaks:

```kotlin
    onQueueClick: () -> Unit = {},
```

   Render it as the last control in the transport row, using the existing
   `com.example.nyasaplayer.core.common.ui.icons` queue icon if one exists — otherwise reuse
   the icon `CarFullPlayerScreen` already passes to its own queue action — wrapped in
   `.carTouchTarget()`. Then wire it at the call site in `AutomotiveApp.kt:312`:

```kotlin
                onQueueClick = { showQueue = true },
```

   `showQueue` already exists and already drives `CarQueueScreen`; the queue was simply
   unreachable from the mini-player.
5. **Merge the artwork and the title block into a single clickable.** They are currently two separate targets. Wrap both in one `Row` carrying the click and `.carTouchTarget()`:

```kotlin
Row(
    modifier = Modifier
        .carTouchTarget()
        .clickable(onClick = onExpandClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    // existing artwork composable
    // existing title / artist Column
}
```

Use whatever the existing expand callback is named in this file rather than inventing `onExpandClick`. In the HTML prototype these were 64x64 and 230x43 — two sub-minimum targets. Merged, they are one large one, and tapping anywhere on the "now playing" block opens the player.

- [ ] **Step 4: Re-theme the seven screens**

**The governing rule, because the table below cannot enumerate every call site:**

> `NyasaPrimary` is the **accent**; `NyasaPrimaryDark` is its darker partner. Gold on a dark
> surface is 8.7:1, so an accent used as a *tint*, *text colour* or *border* becomes
> `NyasaGold` with nothing else to change. An accent used as a **fill behind content** becomes
> `NyasaGold` **and** the content on it must become `NyasaOnGold` — white on gold is 2.29:1.
> Where an API requires a two-stop list or a `Brush`, keep the shape and use
> `NyasaGold` / `NyasaGoldDim`.

Every usage shape present in these files, enumerated by grep:

| Old | New | Note |
|---|---|---|
| `NyasaSurface2` | `CarGlass` | surface |
| `NyasaTextSecondary` | `CarTextSecondary` | text |
| `.background(Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark)))` | `.background(NyasaGold)` | fill — flip content to `NyasaOnGold` |
| `.background(Brush.linearGradient(listOf(NyasaPrimary, NyasaPrimaryDark)))` | `.background(NyasaGold)` | fill — flip content to `NyasaOnGold` |
| `Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark))` assigned to a `Brush` | `SolidColor(NyasaGold)` | the API wants a `Brush`; import `androidx.compose.ui.graphics.SolidColor` |
| `gradientColors = listOf(NyasaPrimary, NyasaPrimaryDark)` | `listOf(NyasaGold, NyasaGoldDim)` | the parameter needs two stops |
| `BrowseCategory(..., listOf(NyasaPrimary, NyasaPrimaryDark))` | `listOf(NyasaGold, NyasaGoldDim)` | data, not a modifier |
| `listOf(NyasaPrimaryDark.copy(alpha = 0.15f), Color.Transparent)` | `listOf(NyasaGoldDim.copy(alpha = 0.15f), Color.Transparent)` | keep the alpha |
| `NyasaPrimary.copy(alpha = X)` | `NyasaGold.copy(alpha = X)` | keep the alpha |
| `tint = NyasaPrimary` | `tint = NyasaGold` | accent on dark — no flip needed |
| `color = NyasaPrimary` | `color = NyasaGold` | accent on dark — no flip needed |
| `activeTrackColor = NyasaPrimary` | `activeTrackColor = NyasaGold` | accent on dark — no flip needed |
| `if (cond) NyasaPrimary else NyasaTextSecondary` | `if (cond) NyasaGold else CarTextSecondary` | both sides change |

Known locations for the less obvious ones: `gradientColors` at `CarHomeScreen.kt:155`,
`BrowseCategory` at `CarBrowseScreen.kt:105`, the `.copy(alpha)` chip accents at
`CarQueueScreen.kt:230-231`, the scrim list at `CarFullPlayerScreen.kt:90`, and
`activeTrackColor` at `CarFullPlayerScreen.kt:256`.

List the files to work through:

```bash
grep -rl 'NyasaPrimary\|NyasaSurface2\|NyasaTextSecondary' \
  automotive/src/main/java/com/example/nyasaplayer/auto/ui/screens/
```

This is mechanical. Do not restructure layout, rename parameters, or change behaviour — the
screens keep working exactly as they do now, in gold instead of purple.

- [ ] **Step 5: Build**

```bash
./gradlew :automotive:assembleDebug
```

Expected: BUILD SUCCESSFUL. Unused-import errors are the likely failure; remove what the compiler names.

- [ ] **Step 6: Verify no mobile tokens remain in the car module**

```bash
grep -rn 'NyasaPrimary\|NyasaSurface2\|NyasaTextSecondary' automotive/src/main/java/ || echo "clean"
```

Expected: `clean`. Anything still listed is a file Step 1-4 missed — go back and re-theme it.
Do not narrow the grep to make it pass.

- [ ] **Step 7: Install and confirm the screens still work**

```bash
adb install -r automotive/build/outputs/apk/debug/automotive-debug.apk
```

Open each of the seven existing screens: Home, Browse, Library, Auth, FullPlayer, Queue, ArtistLikedSongs. Each must render with gold accents and no purple, and remain navigable.

- [ ] **Step 8: Run Detekt and the tests**

```bash
./gradlew detekt
./gradlew :automotive:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; 22 tests pass.

- [ ] **Step 9: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/
git commit -m "refactor: re-theme car components to the gold design tokens"
```

---

### Task 13: Product flavors

**Files:**
- Modify: `automotive/build.gradle.kts`
- Modify: `automotive/src/main/AndroidManifest.xml`
- Create: `automotive/src/oem/AndroidManifest.xml`
- Create: `automotive/src/playstore/AndroidManifest.xml`

**Interfaces:**
- Consumes: nothing
- Produces: `oem` and `playstore` build variants

**This task changes every Gradle task name** for the module. After it, `:automotive:assembleDebug` becomes `:automotive:assembleOemDebug`, and `:automotive:testDebugUnitTest` becomes `:automotive:testOemDebugUnitTest`. It is deliberately last so earlier tasks use the simpler names.

A runtime feature flag cannot substitute for this. Play review inspects the shipped manifest, so an activity declared with a launcher intent filter is present whether or not a flag hides it at runtime.

Note: `PlaybackService` is **not** declared in this module's manifest — it comes from `:core:playback` via manifest merging. Both flavors therefore get it automatically, and the `playstore` manifest needs to add nothing.

- [ ] **Step 1: Declare the flavors**

In `automotive/build.gradle.kts`, inside the `android { }` block and immediately after the existing `buildTypes { }` block, add:

```kotlin
    flavorDimensions += "distribution"
    productFlavors {
        create("oem") {
            dimension = "distribution"
            isDefault = true
        }
        create("playstore") {
            dimension = "distribution"
        }
    }
```

- [ ] **Step 2: Move the activity into the oem manifest**

Create `automotive/src/oem/AndroidManifest.xml` with exactly this content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!--
        The custom launcher lives only in the oem flavor.

        Play's AAOS media category rejects apps that ship custom activities for playback
        or browse. The playstore flavor therefore declares no launcher activity and
        exposes only PlaybackService, which the OEM media template renders against.
        See docs/superpowers/specs/2026-08-02-aaos-foundation-restrictions-design.md 6
    -->
    <application>
        <activity
            android:name=".ui.AutomotiveActivity"
            android:exported="true"
            android:label="@string/auto_app_name"
            android:theme="@style/Theme.NyasaPlayer">

            <!--
                REQUIRED. Without this, AAOS blocks the activity once the vehicle is in
                motion - the driver cannot see it at all, and the entire restriction layer
                built in Tasks 3-10 never gets a chance to run.

                This declaration asserts the activity meets the driver-distraction
                guidelines: the 76dp targets of Task 8, the contrast of Task 2, and the
                gating of Tasks 6 and 10. It is only honest once those are in place, which
                is why it lands here and not earlier.

                Parked-only activities added in later phases - sign-in, PIN opt-in, profile
                switcher, settings - must NOT declare it. See AAOS_PRD.md gate OG-3.
            -->
            <meta-data
                android:name="distractionOptimized"
                android:value="true" />

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

Then **delete that same `<activity>` block from** `automotive/src/main/AndroidManifest.xml`, leaving its `<application>` element with only the `com.android.automotive` `<meta-data>` child.

Leave everything else in the main manifest untouched — the permissions, the `uses-feature` entries, and every `<application>` attribute are needed by both flavors.

- [ ] **Step 3: Create the playstore manifest**

Create `automotive/src/playstore/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!--
        Deliberately declares no launcher activity.

        This flavor is the Play-compliant variant: the OEM media template is the entire
        UI, driven by PlaybackService and MediaBrowseTree from src/main.
    -->
    <application />

</manifest>
```

- [ ] **Step 4: Build both flavors**

```bash
./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug
```

Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 5: Verify the launcher is declared distraction optimised**

```bash
aapt2 dump xmltree --file AndroidManifest.xml \
  automotive/build/outputs/apk/oem/debug/automotive-oem-debug.apk | grep -c "distractionOptimized"
```

Expected: non-zero.

This is not cosmetic. `distractionOptimized` appears nowhere in the repository today, and
without it AAOS refuses to show the activity while the vehicle is moving — so the restriction
work in Tasks 3-10 would never execute in the state it was written for.

- [ ] **Step 6: Verify the manifests actually differ**

```bash
aapt2 dump xmltree --file AndroidManifest.xml \
  automotive/build/outputs/apk/oem/debug/automotive-oem-debug.apk | grep -c "android.intent.category.LAUNCHER"
aapt2 dump xmltree --file AndroidManifest.xml \
  automotive/build/outputs/apk/playstore/debug/automotive-playstore-debug.apk | grep -c "android.intent.category.LAUNCHER"
```

Expected: a non-zero count for `oem`, and `0` for `playstore`.

If `aapt2` is not on your PATH, find it under `$ANDROID_HOME/build-tools/<version>/aapt2`.

This step is the whole point of the task. Do not skip it — a flavor that compiles but ships the same manifest achieves nothing.

- [ ] **Step 7: Run tests and Detekt for both flavors**

```bash
./gradlew :automotive:testOemDebugUnitTest :automotive:testPlaystoreDebugUnitTest
./gradlew detekt
./gradlew :automotive:lintOemDebug :automotive:lintPlaystoreDebug
```

Expected: 22 tests pass in each flavor; Detekt and Lint BUILD SUCCESSFUL for **both**
flavors. Linting only `oem` would leave the Play-facing variant unchecked, which is the one
variant whose manifest correctness actually matters for submission.

- [ ] **Step 8: Commit**

```bash
git add automotive/build.gradle.kts \
        automotive/src/main/AndroidManifest.xml \
        automotive/src/oem/AndroidManifest.xml \
        automotive/src/playstore/AndroidManifest.xml
git commit -m "build: split oem and playstore flavors by manifest"
```

---

## Final Verification

Run after Task 13. Every item maps to the spec's definition of done.

- [ ] **Full build and check**

```bash
./gradlew clean
./gradlew :automotive:assembleOemDebug :automotive:assemblePlaystoreDebug
./gradlew :core:common:testDebugUnitTest :automotive:testOemDebugUnitTest
./gradlew detekt
./gradlew :app:lintDebug :core:common:lintDebug :core:data:lintDebug \
  :automotive:lintOemDebug :automotive:lintPlaystoreDebug
```

Expected: all BUILD SUCCESSFUL. 3 tests in `:core:common`, 22 in `:automotive`.

- [ ] **Confirm the existing screens still work**

Install the `oem` variant and confirm all seven existing screens still render and navigate: Home, Browse, Library, Auth, FullPlayer, Queue, ArtistLikedSongs.

- [ ] **Confirm against the spec's definition of done**

1. Gold tokens in `:core:common`; car surfaces in `:automotive` — Tasks 2, 7
2. px→dp policy in `docs/aaos-DESIGN.md` — **already done in `333cc0c`**
3. Dimension tokens; `CarCardCornerRadius` = 20.dp — Task 7
4. `Modifier.carTouchTarget()` plus components — Tasks 8, 9, 11
5. `oem` / `playstore` flavors, both green; `oem` has `distractionOptimized=true` and `playstore` has no launcher — Task 13
6. `CarUxRestrictionsHandler` fixed; mapping split — Tasks 3, 4
7. `CarUiLocation`; `CarScreen.Favourites` — Task 5
8. `gate()` with refusal and eviction — Tasks 6, 10
9. JVM unit tests including the `NO_TEXT_MESSAGE` regression — Tasks 3, 6
10. Driving-state recipe documented, or its absence reported — Task 1
11. Existing 7 screens still build and run, re-themed — Task 12

# T25 — Crashlytics: a release crash reaches Firebase, a debug crash does not — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire Firebase Crashlytics into both apps through `:core:data`, collecting in release and not in debug, with an inventory of what is sent.

**Architecture:** The SDK is an `implementation` dependency of `:core:data`; the Crashlytics Gradle plugin is applied in each application module (it injects the build ID the SDK requires); a manifest flag in `:core:data`'s debug source set turns collection off for every debug variant. No Kotlin.

**Tech Stack:** Gradle version catalog, Firebase Crashlytics 19.4.4, Crashlytics Gradle plugin 3.0.8, AGP 8.8.0, google-services 4.4.2.

**Spec:** `docs/tickets/T25-crashlytics-in-core-data.md` (the ticket is the spec) and the decisions it cites in `docs/tickets/T24-crash-reporting.md` (D1–D8). Read both first; they are binding. Where this plan and an existing file disagree, the file wins — copy it and say so in your report.

## Global Constraints

- Versions, verbatim: `firebaseCrashlytics = "19.4.4"`, `firebaseCrashlyticsGradle = "3.0.8"` (both verified on Google Maven 2026-09-14; 19.4.4's POM asks `firebase-common` 21.0.0 exactly).
- The non-`-ktx` artifact `com.google.firebase:firebase-crashlytics`. No Firebase BoM; no other Firebase library moves (T24 D5).
- The SDK only in `:core:data` as `implementation`. `:app` and `:automotive` get the **plugin line only** — no dependency, no code, no manifest change (T24 D2, D3).
- Debug off via `core/data/src/debug/AndroidManifest.xml` `firebase_crashlytics_collection_enabled=false`. Nothing calls `setCrashlyticsCollectionEnabled` (T24 D4).
- No Kotlin in this ticket. No `setUserId`, no `FirebaseCrashlytics.log`, no custom keys (those are T26/T27).
- Out of scope: the `surface` key (T26), non-fatals (T27), BoM, minify, mapping upload, NDK.
- Commit messages: subject ≤72, body wrapped at 72 explaining why, **no AI attribution, no `Co-Authored-By`**. Never commit to `main`; branch `ek/t25-crashlytics`.

## Tools

`B=~/Library/Android/sdk/build-tools/36.0.0` — `$B/aapt2`, `$B/apksigner`. Debug keystore `~/.android/debug.keystore` (alias `androiddebugkey`, store and key password `android`).

---

### Task 1: Build wiring

**Files:**
- Modify: `gradle/libs.versions.toml` (`[versions]`, `[libraries]`, `[plugins]`)
- Modify: `build.gradle.kts` (root `plugins {}`), `app/build.gradle.kts` and `automotive/build.gradle.kts` (`plugins {}`), `core/data/build.gradle.kts` (dependencies)
- Create: `core/data/src/debug/AndroidManifest.xml`

**Interfaces:**
- Produces: catalog aliases `libs.firebase.crashlytics` and `libs.plugins.firebase.crashlytics`.

- [ ] **Step 1: Record the baseline before changing anything**

```bash
./gradlew :app:dependencyInsight --dependency com.google.firebase:firebase-common --configuration releaseRuntimeClasspath | grep -m3 "firebase-common"
./gradlew :app:processDebugMainManifest :app:processReleaseMainManifest \
  :automotive:processOemDebugMainManifest :automotive:processOemReleaseMainManifest \
  :automotive:processPlaystoreDebugMainManifest :automotive:processPlaystoreReleaseMainManifest -q
for f in $(find app/build automotive/build -path '*intermediates/merged_manifest*/AndroidManifest.xml'); do
  echo "$f: $(grep -c '<activity' $f) activities"; done
```

Save the output in your report. Expected: `firebase-common:21.0.0`.

- [ ] **Step 2: The catalog**

In `gradle/libs.versions.toml` add, beside the other Firebase versions:

```toml
firebaseCrashlytics = "19.4.4"
firebaseCrashlyticsGradle = "3.0.8"
```

under `[libraries]`, beside `firebase-database`:

```toml
firebase-crashlytics = { group = "com.google.firebase", name = "firebase-crashlytics", version.ref = "firebaseCrashlytics" }
```

under `[plugins]`, beside `google-gms-google-services`:

```toml
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebaseCrashlyticsGradle" }
```

- [ ] **Step 3: Plugins and dependency**

Root `build.gradle.kts`, in `plugins {}` after the google-services line:

```kotlin
    alias(libs.plugins.firebase.crashlytics) apply false
```

`app/build.gradle.kts` and `automotive/build.gradle.kts`, in `plugins {}` after `alias(libs.plugins.google.gms.google.services)`:

```kotlin
    // Injects the build ID Crashlytics refuses to start without; the SDK itself lives in :core:data (T24 D3).
    alias(libs.plugins.firebase.crashlytics)
```

`core/data/build.gradle.kts`, after `implementation(libs.firebase.database)`:

```kotlin
    implementation(libs.firebase.crashlytics)
```

- [ ] **Step 4: Debug off**

Create `core/data/src/debug/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!--
        Crashlytics collects in release only. Every debug variant of both apps consumes this
        library's debug variant, so this one flag covers debug, oemDebug and playstoreDebug.
        A manifest flag, not setCrashlyticsCollectionEnabled(): a runtime false only takes effect
        on the next launch and outlives a reinstall (T24 D4).
    -->
    <application>
        <meta-data
            android:name="firebase_crashlytics_collection_enabled"
            android:value="false" />
    </application>

</manifest>
```

- [ ] **Step 5: Build every variant**

Run: `./gradlew :app:assembleDebug :app:assembleRelease :automotive:assembleOemDebug :automotive:assembleOemRelease :automotive:assemblePlaystoreDebug :automotive:assemblePlaystoreRelease test detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Check the acceptance criteria that the build can prove**

```bash
# 1. Nothing moved underneath: expect firebase-common:21.0.0
./gradlew :app:dependencyInsight --dependency com.google.firebase:firebase-common --configuration releaseRuntimeClasspath | grep -m3 "firebase-common"

# 2. Every release APK carries a non-empty build ID
B=~/Library/Android/sdk/build-tools/36.0.0
for apk in app/build/outputs/apk/release/*.apk automotive/build/outputs/apk/*/release/*.apk; do
  echo "$apk: $($B/aapt2 dump resources $apk | grep -A1 'mapping_file_id' | tail -1)"; done

# 3. Debug variants have the flag false, release variants do not
for f in $(find app/build automotive/build -path '*intermediates/merged_manifest*/AndroidManifest.xml'); do
  echo "$f: $(grep -A1 'firebase_crashlytics_collection_enabled' $f | tr -d '\n ' )"; done

# 4. No new activity: compare with Step 1's counts
for f in $(find app/build automotive/build -path '*intermediates/merged_manifest*/AndroidManifest.xml'); do
  echo "$f: $(grep -c '<activity' $f) activities"; done
```

Pass: (1) 21.0.0; (2) each release APK shows a non-empty string; (3) `…value="false"` for every debug
manifest and nothing for every release manifest; (4) the same activity counts as Step 1. Any failure is
a stop — report it, do not work around it.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts automotive/build.gradle.kts \
        core/data/build.gradle.kts core/data/src/debug/AndroidManifest.xml
git commit -m "T25: Crashlytics in :core:data, off in debug" -m "The SDK is an implementation dependency of :core:data; each app applies
the plugin, which injects the build ID the SDK refuses to start without.
A debug-source-set manifest flag turns collection off for every debug
variant. Pinned to the BoM 33 line so firebase-common stays 21.0.0."
```

---

### Task 2: What is sent — `docs/CRASH_REPORTING.md`

**Files:**
- Create: `docs/CRASH_REPORTING.md`
- Modify: `docs/AAOS_COMPLIANCE.md` (one line under `## Distribution Tracks`)

- [ ] **Step 1: Read the sources, today**

Fetch and read, and record each URL with the date you read it:
- Firebase's "Privacy and Security in Firebase" page (the Crashlytics and Firebase Sessions data it
  lists, and retention).
- Firebase's Google Play data-disclosure page for Crashlytics.

Every fact in the inventory must come from those pages as read today — not from the ticket, not from
memory. If a page cannot be fetched, stop and report it rather than filling the gap.

- [ ] **Step 2: Write the inventory**

`docs/CRASH_REPORTING.md`, sections:
- **What is sent** in this configuration (Crashlytics defaults plus Firebase Sessions, which it pulls
  in), each item with the source URL.
- **What is never sent** — T24 D8's list verbatim in substance: no `setUserId`, no Firebase uid, no
  email, no song/queue/search text in keys, logs or exception messages; `FirebaseCrashlytics.log` is
  not used; the installation UUID identifies the install, not the driver.
- **Where collection is switched, and how to check it** — the debug manifest flag (file path); the
  `adb shell setprop log.tag.FirebaseCrashlytics DEBUG` + `adb logcat -s FirebaseCrashlytics` check.
- **Retention**, per the privacy page.
- **The audience assumption** (T24 D8's last paragraph): shipping beyond the owner and testers reopens
  consent and a Play Data safety declaration.
- A line that T26 and T27 extend this file.

- [ ] **Step 3: The compliance pointer**

Under `## Distribution Tracks` in `docs/AAOS_COMPLIANCE.md`, after the table, one line: crash reporting
and what it sends are inventoried in `docs/CRASH_REPORTING.md` (T24, T25).

- [ ] **Step 4: Commit**

```bash
git add docs/CRASH_REPORTING.md docs/AAOS_COMPLIANCE.md
git commit -m "T25: inventory what Crashlytics sends" -m "Taken from Firebase's privacy and Play disclosure pages as read today, so
the car's data story has a home in the compliance notes (T24 D8)."
```

---

### Task 3: Device verification (controller)

Needs the owner for two things: the Crashlytics dashboard for `nyasamusic-5ed31` (no tool here reads
it), and approval to uninstall the debug build on each emulator (it wipes that install's data,
including sign-in, which the owner re-enters).

- [ ] **Step 1: Car, release** — `AAOS_AOSP_33_userdebug`, one emulator. With approval, uninstall the
  debug build for user 10. Sign the `oem` release APK:
  `$B/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --out /tmp/…/oem-release-signed.apk automotive/build/outputs/apk/oem/release/*.apk`,
  install for user 10, `adb shell setprop log.tag.FirebaseCrashlytics DEBUG`, launch, then
  `adb shell am crash com.example.nyasaplayer`. Relaunch; `adb logcat -s FirebaseCrashlytics` should
  show the report being sent. If `am crash` does not reach the uncaught-exception handler as a Java
  exception, stop and report — do not commit a `throw`.
- [ ] **Step 2: Car, debug** — reinstall the debug build, crash it the same way, relaunch: logcat
  reports collection disabled; nothing new should reach the dashboard.
- [ ] **Step 3: Dashboard** — ask the owner to confirm the car release crash appears under
  `nyasamusic-5ed31`, and that no debug crash does.
- [ ] **Step 4: Phone** — the same release/debug pair on `Medium_Phone_API_35`, **if** its `/data` has
  room (it was 95% full on 2026-09-14 and refused installs). If not, record it as owed with the T28 /
  T29 phone session.
- [ ] **Step 5: Record** — `docs/T25_VERIFICATION.md`, in the shape of `docs/T29_VERIFICATION.md`: the
  Task 1 Step 6 outputs, each device step's result, the owner's dashboard confirmation, and anything
  not run and why. Update the ticket's Status. Commit
  `T25: verification record` with no attribution.

---

### Task 4: Review and PR

- [ ] `pr-review-toolkit:code-simplifier`, then `correctness-reviewer` and `quality-reviewer` in parallel, on `git diff main...HEAD`; verify each finding; re-run the Task 1 Step 5 gate.
- [ ] `gh pr create --base main`, title ≤72; body: what is wired where and why, the build-proven criteria, the device and dashboard results, anything owed. No AI attribution.

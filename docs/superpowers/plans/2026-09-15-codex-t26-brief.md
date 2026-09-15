# Codex review brief — T26 spec (2026-09-15)

**Target:** `docs/tickets/T26-crash-surface-key.md` (the ticket is the spec; no plan document — the
change is one new class and two `onCreate` lines), with the decisions in
`docs/tickets/T24-crash-reporting.md` (D1, D2, D6, D8) and T25 as merged (`f8fe351`). Review the
ticket against the code. Ranked findings with file:line. Do not edit files.

## Intended code
```kotlin
// core/data/src/main/java/com/example/nyasaplayer/core/data/crash/CrashReporter.kt
@Singleton
class CrashReporter @Inject constructor(@ApplicationContext private val context: Context) {
    fun start() {
        val surface = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) "car" else "mobile"
        FirebaseCrashlytics.getInstance().setCustomKey("surface", surface)
    }
}
```
`NyasaPlayerApplication.onCreate`: `crashReporter.start()` before `firebaseSyncManager.start()`.
`AutomotiveApplication.onCreate`: `crashReporter.start()` before, and outside, the
`if (authRepository.isAuthenticated) catalogSync.start()` gate.

## Decided — do not re-litigate
T24 D1–D8; `surface` values `car`/`mobile` from `FEATURE_AUTOMOTIVE`; set once from
`Application.onCreate`; no flavor key; no unit test (the ticket's Notes say why); Crashlytics is an
`implementation` dependency of `:core:data` (so the apps can't reference Crashlytics types).

## Least confident — look here first
1. The ticket's Notes claim `setCustomKey` is recorded into the session files under
   `files/.crashlytics.v3/` even when `firebase_crashlytics_collection_enabled=false` (debug), so a
   debug build can be checked with `run-as`. Is that true of firebase-crashlytics 19.4.4? Which file
   holds custom keys?
2. Is `FirebaseCrashlytics.getInstance()` safe at the top of `Application.onCreate` in both apps —
   is FirebaseApp initialised by then (FirebaseInitProvider), including when the car process is
   started by the system media center for `PlaybackService` with no activity?
3. Does anything construct `NyasaPlayerApplication` / `AutomotiveApplication` in a JVM or Robolectric
   test that would now reach `FirebaseCrashlytics.getInstance()` without a FirebaseApp? (The ticket
   says no; check `robolectric.properties` and the test sources.)
4. Hilt: `@Singleton` + `@Inject constructor` + `@ApplicationContext` in `:core:data` — does it follow
   the module's existing pattern, and does `@Inject lateinit var` field injection in the two
   `@HiltAndroidApp` classes run before the body of `onCreate` (after `super.onCreate()`)?
5. Anything in the ticket's acceptance criteria or Notes that the intended code can't satisfy, or
   that contradicts T24 or `docs/CRASH_REPORTING.md` as merged.

# Review brief for Codex — AAOS A1 implementation plan

**Review target:** `docs/superpowers/plans/2026-08-02-aaos-foundation-restrictions.md`
**Commit:** `69289b5` on branch `ek/aaos-design-prototype`
**Supporting spec:** `docs/superpowers/specs/2026-08-02-aaos-foundation-restrictions-design.md`

---

## What this is

You reviewed the **spec** for this work earlier and requested changes. All three findings
were addressed in `333cc0c`:

- The mapping function was split into a platform-typed wrapper and a pure function over raw
  values. Your conclusion was right; the stated reason was not. `CarUxRestrictions` does
  expose a public `Builder(boolean, int, long)`, so instances are constructible. The real
  blocker is that every stub method body in `android.car.jar` is
  `throw new RuntimeException("Stub!")`, confirmed by disassembly.
- A concrete `CarUiLocation` matrix was added, covering tab / overlay / drill depth / sheet /
  text entry, plus a defined eviction target.
- `docs/aaos-DESIGN.md` gained a "Units" section rather than having its 88px values
  overwritten — the HTML prototypes genuinely render 88 CSS px, so a bare edit would have
  made the document contradict the artifacts it documents. There were three stale references,
  not two; a fourth `88` is the nav-rail item height and correctly stays.

This is now the **implementation plan** derived from that spec. No implementation code has
been written yet. Reviewing before dispatch is the point.

## Project context

NyasaPlayer is an Android/Kotlin music app with an AAOS (Android Automotive OS) surface in
the `:automotive` module. A 20-screen design system was produced — `docs/aaos-DESIGN.md`,
`docs/aaos-screens.html`, and an interactive prototype at `docs/aaos-app.html`.

This plan implements **slice A1 only**: design tokens, component primitives, product flavors,
and the driving-restriction layer. **No new screens.** Screens come in later slices built on
this foundation.

## Decisions already taken

Challenge the reasoning if you think it is wrong, but these are settled rather than open:

- **The custom launcher is the product; Play Store distribution is deferred.** This resolves
  the conflict `docs/AAOS_UI_REDESIGN_PLAN.md` §1.1 left open — the app currently ships both
  an OEM media-template surface and a custom Compose launcher, and Play's AAOS media category
  rejects custom activities for playback or browse. A future `playstore` product flavor drops
  the launcher.
- **The Play switch is build-time, not a runtime flag.** Review inspects the shipped manifest,
  so a hidden-but-declared launcher activity is still declared.
- **Brand moves to champagne gold `#C9A84C`.** It enters `:core:common` under *new* token
  names rather than repointing `NyasaPrimary`, so the mobile migration stays a separate
  project with its own review.
- **The mapping function takes raw `Int` / `Boolean`.** See the stub-throwing note above.

## Environment facts — verified, not assumed

| | |
|---|---|
| Kotlin | 2.0.21 (`data object` and enum `.entries` are available; the codebase already uses `data object`) |
| AGP | 8.8.0 |
| compileSdk / minSdk | 35 / 29 |
| JVM target | 11 |
| Detekt | `maxIssues: 0`, scanning `*/src/main/java` only — test sources are not scanned |
| Test source sets | Neither `:core:common` nor `:automotive` has one today; the plan creates both |
| `android.car.jar` | `compileOnly`, so `CarUxRestrictions` is absent from the unit-test classpath entirely |
| Flag values | `javap -constants`: `NO_FILTERING 2`, `NO_KEYBOARD 8`, `NO_VIDEO 16`, `NO_SETUP 64`, `NO_TEXT_MESSAGE 128` |
| Existing bug being fixed | `CarUxRestrictionsHandler` maps `noTextEntry` from `NO_TEXT_MESSAGE`; `AutomotiveApp.kt:270` gates search on the derived `isDistractionOptimized`, so search is gated on the wrong signal today |

## Where I am least confident — highest-value targets

1. **Task 1, the adb VHAL commands.** I do not know the correct incantation for this system
   image (`Automotive_Distant_Display_with_Google_Play`, API 33), so the task lists three
   options and makes "no recipe found" an acceptable deliverable. If you know the right
   command, that is the single most useful correction you can make — without it the entire
   restriction layer ships on unit tests alone.

2. **Task 13, flavor mechanics.** Is `flavorDimensions += "distribution"` with
   `create("oem") { isDefault = true }` correct for AGP 8.8 Kotlin DSL? Will the manifest
   merger behave as claimed given a bare `<application />` in
   `src/playstore/AndroidManifest.xml` and a fully-attributed one in `src/main`? Is the
   `aapt2 dump xmltree ... | grep -c LAUNCHER` verification sound?

3. **Task 8, `defaultMinSize` for touch targets.** Compose provides
   `minimumInteractiveComponentSize()`. Is `defaultMinSize` the right primitive, given it
   yields to an explicit `.size()` later in the same modifier chain — which is exactly the
   regression the plan claims it prevents?

4. **Task 10, the eviction `LaunchedEffect`.** Keyed on `(restrictions, location)`, and its
   body mutates state that feeds `location`. I believe it terminates because the eviction
   target itself gates `Allowed`, but that reasoning deserves a second pair of eyes.

5. **Task 4, `check()` inside `connect()`.** Throwing on mirrored-flag drift on a real head
   unit — correct, or should it log and degrade rather than crash the app?

## Out of scope

- The design system's visual choices (colours, layout, the 20 screens)
- The mobile brand migration — a separate project with its own spec
- Anything in `:app`, `:core:data`, or `:core:playback`

## Verdict needed

Is this plan safe to hand to implementers task-by-task, or does something need fixing first?

Specifically: any task whose code will not compile, any step whose command is wrong, any
cross-task interface mismatch, and any spec requirement with no covering task.

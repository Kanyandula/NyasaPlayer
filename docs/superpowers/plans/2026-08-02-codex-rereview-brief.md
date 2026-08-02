# Confirming pass for Codex — AAOS A1 implementation plan

**Review target:** `docs/superpowers/plans/2026-08-02-aaos-foundation-restrictions.md`
**Now at commit:** `272c360` (was `69289b5` when you reviewed it)
**Prior brief:** `docs/superpowers/plans/2026-08-02-codex-review-brief.md`

You reviewed this plan and returned six findings. Five were accepted and patched; one was
declined with reasoning. This is a **scoped confirming pass** — verify the fixes landed and
did not break anything, rather than re-reviewing the whole plan.

---

## What changed, per finding

### 1. High — `CarScreen.Favourites` breaks the exhaustive `when`. ACCEPTED, FIXED.

You were right and my plan was wrong to hedge it. Confirmed the `when (currentScreen)` at
`AutomotiveApp.kt:247` is a `when` **statement** over an enum, and since Kotlin 1.7 those must
be exhaustive or compilation fails. This project is on Kotlin 2.0.21, so the build breaks.

Task 5 now has a **required** Step 3 that adds the branch, with the code to write and an
instruction to copy the argument list verbatim from the existing `CarScreen.Library ->`
branch. The old text said "if a `when` now fails" — that conditional framing is gone. Task 5's
commit step now also stages `AutomotiveApp.kt`.

**Verify:** the branch code is correct for this codebase, and Task 5's step numbering is intact.

### 2. High — Task 12's grep contradicts its own scope. ACCEPTED, FIXED.

Confirmed by grep: all seven screens use `NyasaPrimary`, `NyasaSurface2` or
`NyasaTextSecondary`, so the check could never have passed.

Rather than narrow the check to match the three-file scope, **Task 12 now covers all ten
files** — three components plus seven screens — which is what definition-of-done item 11
required anyway. A new Step 4 gives the substitution table for the screens. Step 6 now says
explicitly: "Do not narrow the grep to make it pass."

**Verify:** the file list is complete, and the substitution table matches what the screens
actually use.

### 3. Medium — mini-player lacks the queue CTA. ACCEPTED, FIXED.

Task 12 Step 3 now adds `onQueueClick: () -> Unit = {}` to `CarMiniPlayer` — defaulted so no
existing call site breaks — renders it as the last transport control wrapped in
`.carTouchTarget()`, and wires it at `AutomotiveApp.kt:312` to `{ showQueue = true }`.
`showQueue` and `CarQueueScreen` already existed; the queue was simply unreachable from the
mini-player.

**Verify:** defaulting the parameter is genuinely source-compatible with the existing call
site, and `showQueue = true` is the correct wiring.

### 4. Medium — primitives are not complete components. ACCEPTED, SCOPE STATED.

Task 11 now says plainly that these are minimal primitives and names what is deliberately
absent: real artwork loading in `CarTrackRow` (it draws a placeholder box), overflow and
ellipsis handling, per-row like and overflow affordances, and the orb in `CarEmptyState`. It
instructs implementers **not** to add them, because a consuming screen slice will know what
shape they need.

**Verify:** this is honest labelling rather than a scope dodge, given A1's stated goal.

### 5. Medium — `collectAsStateWithLifecycle`. DECLINED, RECORDED.

The one I pushed back on. `androidx.lifecycle-runtime-compose` is **not in
`gradle/libs.versions.toml`** — I checked. So this means adding a dependency *and* changing
collection behaviour (collection stops in the background) across the file all seven screens
render through. That is a behavioural change needing its own verification pass, not a
drive-by edit inside a foundation slice whose job is tokens and restrictions.

Recorded in a new **"Deliberately Deferred"** section at the top of the plan, alongside the
minimal-primitives note and the Favourites screen, so it is a decision on the record rather
than an omission.

**Tell me if you disagree.** If you think the lifecycle migration is load-bearing for A1
specifically — not just good practice — say so and I will fold it in as its own task rather
than a drive-by.

### 6. Medium — lint only covers the `oem` flavor. ACCEPTED, FIXED.

Both Task 13 Step 6 and the Final Verification block now run
`:automotive:lintOemDebug :automotive:lintPlaystoreDebug`, with a note that linting only
`oem` leaves the Play-facing variant — the one whose manifest correctness actually matters —
unchecked.

---

## What I need from this pass

1. **Did each accepted fix actually land correctly?** Particularly findings 1 and 2, where the
   fix involved adding code to the plan rather than editing prose.
2. **Did the patches break anything?** Step renumbering in Tasks 5 and 12, cross-task interface
   consistency, any newly-introduced contradiction.
3. **Do you accept the decline on finding 5**, or do you consider it load-bearing for A1?
4. **Anything still blocking dispatch.** The plan is about to be executed task-by-task by
   implementer subagents, so a wrong command or a non-compiling snippet costs a full round trip.

## Unchanged since your review

Everything else: the 13-task structure, the driving-state spike as Task 1, flavors last
because they rename every Gradle task, and the raw-value mapping signature. The environment
facts in the prior brief still hold — Kotlin 2.0.21, AGP 8.8.0, Detekt `maxIssues: 0`
scanning `*/src/main/java` only, `android.car.jar` `compileOnly`.

The uncertainty ranking from the prior brief also still stands, and none of it was addressed
by these patches: **Task 1's adb VHAL commands are still guesswork**, and **Task 8's
`defaultMinSize`** may be the wrong primitive versus `minimumInteractiveComponentSize()`. If
you have knowledge on either, that is still the highest-value thing you can add.

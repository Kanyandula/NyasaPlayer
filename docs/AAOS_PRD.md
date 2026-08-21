# PRD — Nyasa Music for Android Automotive OS

| | |
|---|---|
| **Product** | Nyasa Music — AAOS in-car experience |
| **Document owner** | Ephraim Kanyandula |
| **Status** | Draft for review |
| **Version** | 1.0 |
| **Date** | 2026-08-02 |
| **Repository** | `~/AndroidStudioProjects/NyasaPlayer`, module `:automotive` |
| **Notion** | https://app.notion.com/p/3b0728b1385d81a3b210f41de12117c5 |
| **Supersedes** | `docs/AAOS_UI_REDESIGN_PLAN.md` §1 (see §3.3) |

---

## 1. Executive summary

Nyasa Music ships an Android Automotive OS surface today, but it is in an unresolved state:
the app runs **two** competing UIs — an OEM media-template surface and a custom Compose
launcher — and the repository's own planning document flags this as an unresolved Play Store
risk rather than a decision.

This programme resolves that, and rebuilds the in-car experience against a designed,
measured, compliance-aware system. It delivers a **20-screen custom launcher** in a champagne-gold
visual identity, with driving restrictions enforced from the vehicle's own signals rather than
assumed.

The work is sequenced into **nine phases**. Phase A1 is foundation — tokens, touch-target
primitives, build variants, and the restriction layer. Phases A2–A8 deliver the AAOS screens.
Project B is tracked separately; the AAOS release does not wait for the mobile brand migration.

**Current state:** A1-A6 are merged on `main`. The phase table in §9 carries each slice's
status and links its dated verification record; carve-outs live in those records rather than
being restated here, which is what kept this paragraph stale through two slices.

---

## 2. Problem statement

### 2.1 What is wrong today

**The app has two UIs and no decision about which one it is.** `PlaybackService`
(`MediaLibraryService`) drives the OEM media template, while `AutomotiveActivity` hosts seven
custom Compose screens. Both are live. `docs/AAOS_UI_REDESIGN_PLAN.md` §1.1 records this
verbatim: *"This is a real Play Store risk, not just doc drift... Resolving it is a decision,
not a cleanup."*

**Driving restrictions are gated on the wrong signal.** `CarUxRestrictionsHandler` maps its
`noTextEntry` field from `UX_RESTRICTIONS_NO_TEXT_MESSAGE` — the *messaging* restriction,
which has nothing to do with keyboards. The correct flag is `UX_RESTRICTIONS_NO_KEYBOARD`.
`AutomotiveApp.kt:270` gates search on the derived value, so **search is currently restricted
by the wrong condition**. The handler also never reads `NO_SETUP`, `getMaxContentDepth()`, or
applies them.

**Interactive controls are below the minimum touch target.** Measured across the design
prototype, nine control classes fell under the 76dp minimum — worst was a like button at
22×27. The same classes exist in the shipping Compose code.

**Secondary text failed the contrast standard the design claimed.** `#A0A0B0` on card
surfaces measured **6.8:1** — AA, not the AAA the design document asserted.

**The custom screens have no design system.** They use the mobile purple accent, ad-hoc
dimensions, and layouts that predate any car-specific design work.

### 2.2 Why it matters

An AAOS media app that mis-gates its own distraction restrictions is not merely untidy — it
presents text entry and deep browsing to a driver in motion. Touch targets below the minimum
and contrast below standard compound the same problem. These are safety characteristics, not
polish.

---

## 3. Goals and non-goals

### 3.1 Goals

| # | Goal | Measure of success |
|---|---|---|
| G1 | Resolve the two-UI ambiguity with a recorded decision | `AAOS_UI_REDESIGN_PLAN.md` no longer contradicts the shipped architecture |
| G2 | Enforce driving restrictions from real vehicle signals | Every `CarUxRestrictions` default the app models is read from the platform and acted on |
| G3 | Every interactive control meets 76dp | Automated measurement returns zero controls below minimum |
| G4 | Every non-disabled text pair clears AAA | Measured on every surface the token lands on, not just one |
| G5 | Ship the 20-screen designed experience | All 20 screens implemented and matching the design system and screen contract |
| G6 | Keep a Play-compliant path open without blocking on it | `playstore` variant builds green **and** passes the merged-manifest gates in §8.2 |
| G7 | Publish brand tokens the mobile app can adopt | Gold tokens live in `:core:common` and are consumable by `:app`. **Migrating mobile is out of scope — see §3.2** |

### 3.2 Non-goals

- **Play Store submission in this programme.** The compliant variant is built and kept green,
  but submission is a later business decision.
- **Deleting the OEM media-template path.** It stays first-class — Assistant and voice search
  depend on it regardless of which UI the driver sees.
- **Redesigning the mobile app's layouts.** Project B changes brand colour and button
  treatment only.
- **Offline-first rearchitecture, new backend work, or catalogue changes.** Out of scope.
- **Migrating the mobile app to the gold brand (Project B).** A1 publishes the tokens; adopting
  them in `:app` is a separate piece of work with its own PRD, and the AAOS release does not
  wait on it. The interim state — car gold, phone purple — is accepted.

### 3.3 The architecture decision (G1)

**Decided:** the custom launcher is the product. Play Store distribution in the AAOS media
category is **not a hard requirement** for this release.

**Rationale.** Play's AAOS media category rejects apps that ship custom activities for playback
or browse, which is why the 2026-04-23 "Option B" decision chose the template path. That
decision was never executed — the custom screens still ship — and the 20-screen design
commits further to a custom experience. Rather than leave the contradiction open, the product
choice is the custom launcher, distributed via OEM partnership or direct install.

**The Play path is preserved as a build variant, not a runtime flag.** Play review inspects
the shipped manifest, so an activity declared with a launcher intent filter is present whether
or not a flag hides it at runtime. The `playstore` product flavor ships a manifest with no
launcher activity and only `PlaybackService`; the `oem` flavor adds the launcher.

This supersedes `docs/AAOS_UI_REDESIGN_PLAN.md` §1.

---

## 4. Users and context

**Primary user: the driver.** Interacts at arm's length, in a moving vehicle, with divided
attention. Frequently cannot legally or safely look at the screen. Voice is the preferred
input in motion.

**Secondary user: the passenger.** Same screen, no attention constraint, and the vehicle's
restriction signals do not distinguish them — so the app cannot either. Restrictions apply to
the surface, not the person.

**Context of use.** A 1920×1080 landscape head unit, dark cabin, glare, vibration, and a
vehicle that can transition from parked to moving **at any moment** — including while the user
is mid-interaction. That last property drives one of the more consequential requirements
(FR-2.5).

---

## 5. User stories

Each story is stated for both vehicle states, because the same surface behaves differently in
each and the pair is where most requirements actually come from. **P** = parked, **D** = driving.

### Authentication and profile

| ID | As a driver, I want to… | State | Behaviour |
|---|---|---|---|
| US-1 | sign in to my account | P | Full auth flow available |
| US-2 | sign in | D | Refused with explanation; audio from a previous session continues |
| US-3 | protect my profile with a PIN | P | PIN opt-in offered after sign-in |
| US-4 | switch to another profile | P | Profile switcher available |
| US-5 | switch profile | D | Refused — `NO_SETUP` |

### Finding music

| ID | As a driver, I want to… | State | Behaviour |
|---|---|---|---|
| US-6 | browse my library by playlist, album or artist | P | Full depth browsing |
| US-7 | browse my library | D | Tab roots only; drill-down refused past the depth cap, lists truncated to the item cap |
| US-8 | search by typing | P | Platform/system IME when `NO_KEYBOARD` is absent; no custom keyboard (D31) |
| US-9 | search | D | Text entry refused under `NO_KEYBOARD`; system/Assistant voice search offered as the primary path |
| US-10 | see what I recently played | P/D | Home surfaces it in both states |

### Playing music

| ID | As a driver, I want to… | State | Behaviour |
|---|---|---|---|
| US-11 | play, pause, skip and seek | P/D | Always available — never restricted |
| US-12 | see what is playing without leaving my screen | P/D | Persistent mini-player |
| US-13 | open the full player | P/D | Available in both states |
| US-14 | see what is coming next and edit it | P | Queue available; remove and clear permitted (reorder deferred, D26) |
| US-14b | see what is coming next | D | Queue viewable and skip-to permitted; **remove and clear are refused**, list truncated |
| US-15 | like the current track | P/D | Available in both states |

### Settings and edge cases

| ID | As a driver, I want to… | State | Behaviour |
|---|---|---|---|
| US-16 | change audio quality or download settings | P | Settings available |
| US-17 | change settings | D | Refused — `NO_SETUP` |
| US-18 | keep listening when the network drops | P/D | Downloaded content plays; a no-connection state explains the rest |
| US-19 | understand why a track will not play | P/D | Explicit error with a retry and a skip action |
| US-20 | **not be stranded on a screen when I start driving** | P→D | **The app evicts me to a permitted location and explains why (FR-2.5)** |

US-20 is the story the architecture is shaped around. Every other restriction can be satisfied
by gating an entry point; this one cannot.

---

## 6. Requirements

### 6.1 Functional — driving restrictions

| ID | Requirement | Priority |
|---|---|---|
| FR-1.1 | Restriction state is read from `CarUxRestrictionsManager` and exposed reactively | Must |
| FR-1.2 | `noTextEntry` derives from `UX_RESTRICTIONS_NO_KEYBOARD` | Must |
| FR-1.3 | `noSetup` derives from `UX_RESTRICTIONS_NO_SETUP` | Must |
| FR-1.4 | Content depth cap derives from `getMaxContentDepth()` | Must |
| FR-1.5 | Content item cap derives from `getMaxCumulativeContentItems()` | Must |
| FR-1.6 | Distraction-optimised state comes from `isRequiresDistractionOptimization()`, not re-derived from flags | Must |
| FR-1.7 | A failed connection to the Car service must be retryable, not permanent for the process lifetime | Must |

### 6.2 Functional — restriction enforcement

| ID | Requirement | Priority |
|---|---|---|
| FR-2.1 | While driving, Settings and profile switching are refused | Must |
| FR-2.2 | While driving, text entry is refused; voice search is offered instead | Must |
| FR-2.2a | Voice search is **system/Assistant driven**, routed through `PlaybackService.onSearch` / `onGetSearchResult`. The app does not record audio, requests no `RECORD_AUDIO` permission, and ships no in-app recording UI | Must |
| FR-2.3 | While driving, drill-down beyond the depth cap is refused | Must |
| FR-2.4 | While driving, content lists truncate to the item cap | Must |
| FR-2.5 | **Transitioning to driving while already inside a restricted location evicts the user from it** | Must |
| FR-2.6 | Every refusal shows an explanation; silent no-ops are prohibited | Must |
| FR-2.7 | Playback transport, seeking, queue view/skip-to and tab switching remain available while driving | Must |
| FR-2.8 | Decorative motion runs only while parked and freezes in motion | Must |
| FR-2.9 | The launcher activity declares `distractionOptimized="true"`; parked-only activities do not (§8.3) | Must |

> **FR-2.5 is the requirement most likely to be missed.** Gating entry is insufficient: a
> vehicle can start moving while the driver is already inside Settings or three levels deep in
> a library. The prototype demonstrated this behaviour and it is why the gate returns an
> eviction target rather than a boolean.

### 6.3 Functional — screens

Twenty screens. Seven exist today in pre-design layouts; thirteen do not exist.

**This table is the traceability matrix.** Every screen names its entry point, its primary
actions, the states it must handle, and what it does while driving. A screen is not complete
until every column is satisfied. Per-screen acceptance criteria live in that phase's spec;
this table is what those specs are written against, so nothing is silently dropped.

Data sources are existing repository components: `AutomotiveContentViewModel` (catalogue,
liked songs, search), `AutomotivePlayerViewModel` (playback, queue), `AutomotiveAuthViewModel`
(account).

| # | Screen | Entry point | Primary actions | States | While driving | Data | Phase |
|---|---|---|---|---|---|---|---|
| 1 | CarAuthScreen | App launch when signed out | Google / phone / email sign-in | loading, error | **Refused** — parked only | Auth VM | A7 |
| 2 | CarPinOptInScreen | After first sign-in | Enter PIN, Enable, Not now | partial entry, error | **Refused** — `NO_SETUP` | Auth VM | A7 |
| 3 | CarHomeScreen | Rail: Home (default) | Play a recent item, open a mix | loading, empty, error | Allowed; lists truncated | Content VM | A2 |
| 4 | CarBrowseScreen | Rail: Browse | Filter by chip, open a genre | loading, empty, error | Allowed; drill-down refused | Content VM | A3 |
| 5 | CarSearchScreen | System bar: search | Type via system IME when allowed, voice prompt, recent, browse-by shortcuts | idle, recent-empty, no query | Text entry refused; voice offered | Search VM | A6 |
| 6 | CarSearchResultsScreen | Submitting a search | Top song result, song rows, clear/back | loading, empty results, error | Results allowed; active text entry refused | Search VM | A6 |
| 7 | CarLibraryScreen | Rail: Library | Open playlist, album or artist | loading, empty, error | Allowed; drill-down refused | Content VM | A3 |
| 8 | CarFavouriteMusicScreen | Rail: Favourites | Play all, shuffle, play one, unlike | **empty → screen 17** | Allowed; truncated | Content VM | A4 |
| 9 | CarArtistLikedSongsScreen | Library or Favourites → artist | Play all, play one, unlike | empty, loading | **Refused** past depth cap | Content VM | A4 |
| 10 | CarPlaylistScreen | Library → playlist | Play, shuffle, play one | empty, loading | **Refused** past depth cap | Content VM | A3 |
| 11 | CarAlbumScreen | Library → album, or search result | Play, download, play one | empty, loading | **Refused** past depth cap | Content VM | A3 |
| 12 | CarFullPlayerScreen | Mini-player artwork/title | Play/pause, skip, seek, shuffle, repeat, like, queue | buffering, error → 19 | **Allowed** — playback control | Player VM | A5 |
| 13 | CarQueueScreen | Mini-player queue icon, or full player | **P:** skip to, remove, clear · **D:** skip to only | empty queue | Viewable; edit actions refused, list truncated | Player VM | A5 |
| 14 | CarSettingsScreen | System bar: settings | Toggle prefs, sign out | — | **Refused** — `NO_SETUP` | Auth VM | A7 |
| 15 | CarDownloadsScreen | Library → Downloads chip | **P:** remove one, remove all · **D:** view only | empty, in-progress | Viewable; delete actions refused | Content VM | A8 |
| 16 | CarNoConnectionScreen | Network loss | Retry, go to downloads | — | Allowed | NetworkMonitor | A8 |
| 17 | CarEmptyFavouritesScreen | Favourites with none liked | Browse music | — | Allowed | Content VM | A4 |
| 18 | CarLoadingScreen | Initial content load | none | — | Allowed | Content VM | A8 |
| 19 | CarPlaybackErrorOverlay | Playback failure | Try again, skip to next | — | **Allowed** — must be dismissible while driving | Player VM | A8 |
| 20 | CarProfileSwitcherScreen | System bar: avatar | Switch, add profile | — | **Refused** — `NO_SETUP` | Auth VM | A7 |

**Cross-cutting requirements that apply to every screen**, and are therefore not repeated per
row: the chrome contract (NFR-4), the 76dp touch target (NFR-1), contrast (NFR-2), text sizing
(NFR-3), and eviction on transition to driving (FR-2.5).

**Screens 16, 17, 18 and 19 are states, not destinations.** They replace or overlay a
destination rather than being navigated to, which is why they have no rail entry.

The per-screen UI, CTA, state, component-reuse and motion contract lives in
`docs/AAOS_SCREEN_CONTRACT.md`. This PRD owns product scope and compliance; the screen
contract owns implementation completeness.

### 6.4 Non-functional

| ID | Requirement | Target | Verification |
|---|---|---|---|
| NFR-1 | Minimum touch target | 76 × 76dp on every interactive control | Automated measurement |
| NFR-2 | Text contrast | ≥ 7:1 (AAA) for all non-disabled text, on every surface it lands on | Computed per pair |
| NFR-3 | Minimum text size | No text below **14sp**; no interactive label below **18sp**. Text is sized in `sp` so it honours the vehicle's font-scale setting; only touch targets and spacing use `dp` | Review |
| NFR-4 | Chrome consistency | System bar, nav rail and mini-player render identically on every screen | Single shared implementation |
| NFR-5 | Static analysis | Detekt `maxIssues: 0`; Android Lint clean, **both flavors** | CI |
| NFR-6 | Reduced motion | Decorative animation is disabled when the platform animator duration scale is `0` (`Settings.Global.ANIMATOR_DURATION_SCALE`), independently of driving state | Review |
| NFR-7 | Both variants build | `oem` and `playstore` compile, test and lint green; `oem` passes §8.3 and `playstore` passes §8.2 | CI |

> **NFR-2 has a subtlety worth preserving.** The binding surface is the *raised* surface
> `#1E1E2A` at 7.4:1, not the base at 8.8:1. Measuring the secondary token against the base
> gives a false pass. This is precisely how the original 6.8:1 failure went unnoticed.

### 6.5 Constraints

- **Platform:** AAOS, `minSdk 29`, `compileSdk 35`, Kotlin 2.0.21, AGP 8.8.0, JVM target 11.
- **`android.car.jar` is `compileOnly`** and its stub methods throw `RuntimeException("Stub!")`.
  Platform types therefore cannot appear in JVM unit tests, which dictates that restriction
  logic is written as pure functions over primitives.
- **All design figures are CSS px on a 1920×1080 canvas**, not dp. The conversion rule lives in
  `docs/aaos-DESIGN.md` §Units.
- **Dark theme only.**

---

## 7. Design

**Source of truth:** `docs/aaos-DESIGN.md` — tokens, chrome contract, component specs, and
measured contrast. `docs/AAOS_SCREEN_CONTRACT.md` turns that system into per-screen
implementation requirements.

**Reference implementations:**
- `docs/aaos-screens.html` — all 20 screens, static
- `docs/aaos-app.html` — navigable prototype with restrictions enforced

### 7.1 Visual identity

Champagne gold `#C9A84C` on obsidian `#0A0A0C`. Gold is reserved for what is active or
actionable — the selected nav item, primary CTAs, the play button, progress fill — and never
used for body text.

**Gold is a light colour.** Labels on a gold fill use `#0A0A0C` (8.7:1). White on gold is
**2.29:1** and unusable. The token `NyasaOnGold` exists specifically so this mistake cannot be
made by habit.

### 7.2 Chrome contract

Three regions render identically on every screen: an 80dp system bar, an 80dp navigation rail,
and the persistent mini-player. This is a contract rather than a guideline because the original
generated designs drifted — six screens produced six different system bars, and only two of six
carried the rail.

### 7.3 Motion

Ambient background gradients drift and follow the current album artwork **while parked**, and
freeze **while driving**. Nothing auto-scrolls or pulses in either state.

This refines rather than contradicts the original "no decorative motion" rule: it is motion
*while the vehicle is moving* that is restricted.

---

## 8. AAOS compliance gates

"The `playstore` variant builds green" is not evidence of compliance — it only proves the
code compiles. Compliance is a property of the **merged manifest** and of what the OEM host
can actually render. These gates make it checkable.

### 8.1 What the policy requires

For an app distributed in Play's AAOS **media** category:

- Browse and playback UI are **host-rendered** from the app's `MediaBrowserService` /
  `MediaLibraryService`. The app does not draw them.
- App activities are permitted only for **parked** setup, settings and sign-in flows
  (Google's car app quality rule **PE-1**).

The `oem` variant deliberately does not satisfy this — that is the decision in §3.3. The
`playstore` variant must, and these gates prove it rather than assuming it.

### 8.2 Merged-manifest gates — `playstore` variant

Run against the **merged** manifest extracted from the built APK, not the source manifest,
because merging pulls in declarations from `:core:playback` and every library.

The gates themselves are defined in `docs/AAOS_COMPLIANCE.md` (`MG-*`). In summary: no
launcher activity, no unexpected exported activities, the media service present and exported,
settings reachable via `APPLICATION_PREFERENCES`, the automotive feature declared, and the
descriptor declaring `<uses name="media" />`.

The first two are the ones that matter; the rest guard against regressions introduced by
manifest merging.

### 8.3 Merged-manifest gates — `oem` variant

**The `oem` variant has its own manifest requirement, and it is not optional.** AAOS blocks
any foreground activity that has not declared itself distraction optimised once the vehicle is
in motion. An activity without that declaration is not merely un-styled while driving — the
platform will not let the driver see it.

The custom launcher is the product and is intended to remain usable in motion, so it must
declare it. **No activity in this repository currently does.**

**`docs/AAOS_COMPLIANCE.md` is the normative owner of every gate identifier** — `OG-*` for the
`oem` variant, `MG-*` for `playstore`, `HR-*` for host-render smoke tests. This PRD does not
restate them, because two documents numbering the same gates differently is how a review ends
up citing an ID that means two things.

The `oem` gates that matter most, by name rather than number: the launcher must declare
`distractionOptimized`, every activity reachable while driving must declare it, and
parked-only activities must not. That last one is the mirror of the first and equally
load-bearing — declaring it on a sign-in flow asserts to the platform that the flow is safe
in motion, which is the opposite of what §6.2 requires.

**This declaration is an assertion, not a formality.** It states that the activity meets the
driver-distraction guidelines — the touch targets of NFR-1, the contrast of NFR-2, the
restriction behaviour of §6.2. It should be added when those hold, not before.

### 8.4 Host-render smoke tests — `playstore` variant

Manifest checks prove nothing renders that should not. These prove the host **can** render
what it should. Run on the automotive emulator against the OEM media template, launched
directly at `PlaybackService` rather than through any app launcher.

The tests are defined in `docs/AAOS_COMPLIANCE.md` as `HR-1` … `HR-8`: the app appears in the
media source picker, the host renders the browse root and one child level, playback starts,
metadata is correct, the queue populates and skip-to works, search returns results, Assistant
voice playback works, and custom actions reflect state.

`HR-4` and `HR-8` cannot be verified by screenshot — Now Playing renders on `FLAG_SECURE`
distant-display surfaces. Use `dumpsys media_session` as the oracle; it exposes playback state
and the resolved custom-action list. Recorded in `docs/AAOS_ARCHITECTURE.md`, established
during PR #13.

### 8.5 When these gates run

Both variants build on every change (NFR-7). The manifest gates (§8.2 and §8.3) are cheap and should
be automated from A1, when the flavors are created. The host-render tests (`HR-*`, §8.4) are manual and run
before any Play submission decision, not per-commit — the `playstore` variant is a preserved
option, not an actively shipped artifact.

---

## 9. Phasing

| Phase | Delivers | Depends on | Status |
|---|---|---|---|
| **A1** | Tokens, touch-target primitive, 5 components, `oem`/`playstore` flavors, restriction layer + gate + eviction, re-theme of 10 existing files | — | Merged — PR #14 |
| **A2** | Chrome contract, Home, ambient motion | A1 | Merged — PR #16 |
| **A3** | Browse, Library, Playlist, Album | A2 | Merged and device-verified — PRs #18-#20 |
| **A4** | Favourites, ArtistLikedSongs, EmptyFavourites | A2 | Merged and device-verified — PRs #21-#23 |
| **A5** | FullPlayer, Queue | A2 | Merged and device-verified — PR #24; retryable-error and restore follow-ups recorded |
| **A6** | Search, SearchResults | A2 + A6 design | Merged and device-verified — PR #27; `docs/AAOS_A6_VERIFICATION.md`; T5-T8 follow-ups recorded |
| **A7** | Settings, ProfileSwitcher, PinOptIn, Auth | A1 restrictions | Not started |
| **A8** | NoConnection, Loading, Downloads, PlaybackError | A2 | Not started |
| **Project B** | Mobile brand migration — **separate PRD, non-blocking** | A1 tokens | Not started |

**Why A1 first.** The restriction layer has a live bug and there is no touch-target discipline.
Building twenty screens on that foundation means rebuilding them.

**Why A2 second.** Every screen sits inside the chrome. Settling it late means re-laying-out
screens that were built against a moving target.

**Accepted duplication.** A1 re-themes seven existing screens that A2–A5 later re-lay-out, so
that theming is partly throwaway. It was kept deliberately: the substitution is mechanical, and
it keeps the app visually coherent at every commit rather than half-purple for five phases.

---

## 10. Risks

| Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|
| Launcher activity is not declared distraction optimised, so the platform blocks it while driving | The custom launcher — the product — is unusable in motion, and the entire restriction layer is moot | **Was certain until found** | Gate OG-1; added to A1's flavor task |
| No adb recipe exists to put the emulator into a driving state | Restriction layer ships unverified end to end | Medium | Spike is Task 1 of A1 so it fails early; a documented "no recipe" is an acceptable deliverable that triggers re-planning |
| Play policy changes, making the deferred compliant path urgent | Rework | Low | `playstore` flavor is built and kept green from A1 onward |
| Design figures are px, hardware is dp at unknown density | Controls smaller in practice than measured | Medium | Conversion rule recorded; requires validation on real hardware |
| Smallest rendered text is 15px | May be too small at arm's length | Medium | Flagged in the design doc as a floor to revisit, pending device testing |
| Mobile brand migration inverts every filled CTA | Wide visual regression surface in `:app` | High | Isolated as Project B with its own spec and review |
| Seven existing screens re-laid-out across four phases | Regressions between phases | Medium | Each phase independently testable; screens keep working throughout |

---

## 11. Open questions

| # | Question | Owner | Blocks |
|---|---|---|---|
| Q1 | Can the emulator be put into a driving state, and how? | A1 Task 1 | End-to-end verification of all restriction work |
| ~~Q2~~ | ~~How does text entry work on a head unit — on-screen keyboard when parked, voice-only when driving? The 20 screens do not solve this; the prototype only draws a disabled field.~~ **Resolved 2026-08-20:** A6 uses the platform/system IME when `UX_RESTRICTIONS_NO_KEYBOARD` is absent and replaces editing with a system/Assistant voice-search prompt when it is present. The app draws no custom keyboard, records no audio, and ships song-only submitted results; album/artist result cards are deferred by D33/T4. | — | Closed |
| Q3 | Is 15px acceptable for secondary text at arm's length, or should the floor rise? | Device testing | Type scale across all phases |
| ~~Q4~~ | ~~Does `AAOS_UI_REDESIGN_PLAN.md` get a superseded banner, or get deleted?~~ **Resolved 2026-08-02: bannered, not deleted.** §1.1 and §2 are load-bearing history — the two-surface inventory and the Play policy reasoning a future submission must still satisfy. | — | Closed |
| ~~Q5~~ | ~~Does Downloads belong in the custom launcher, given it is content browse rather than settings?~~ **Resolved 2026-08-03:** yes for the `oem` launcher. The screen is part of A8; delete/remove actions remain parked-only, and the `playstore` path can expose offline content through the media browse tree if needed later. | — | Closed |

---

## 12. Acceptance criteria

The programme is complete when:

1. All 20 screens are implemented and match `docs/aaos-DESIGN.md`.
2. Automated measurement returns **zero** interactive controls below 76dp.
3. Every non-disabled text/surface pair measures **≥ 7:1**.
4. Every restriction in §6.2 is enforced, including eviction (FR-2.5), and verified against a
   real driving-state transition — or Q1 is answered negatively and recorded.
5. `oem` and `playstore` variants both build, test and lint green; `oem` passes the
   distraction-optimised manifest gates (§8.3) and `playstore` passes the Play media gates
   (§8.2). Host-render smoke tests (`HR-*`, §8.4) are required only before a
   Play submission decision, not for this release.
6. Detekt reports zero issues.
7. ~~`docs/AAOS_UI_REDESIGN_PLAN.md` no longer contradicts the shipped architecture.~~ **Done** — superseded banner added 2026-08-02.

**Project B is explicitly not an acceptance criterion.** Migrating the mobile app to gold is
tracked separately and must not gate the AAOS release — it carries a wide visual regression
surface across `:app` that has nothing to do with the car experience.

---

## 13. Appendix

### 13.1 Measured reference data

| Pair | Ratio | Verdict |
|---|---|---|
| White on card `#181824` | 17.6:1 | AAA |
| Gold `#C9A84C` on obsidian | 8.7:1 | AAA |
| Dark `#0A0A0C` on gold | 8.7:1 | AAA |
| Secondary `#ACACBC` on obsidian | 8.8:1 | AAA |
| Secondary `#ACACBC` on card | 7.9:1 | AAA |
| Secondary `#ACACBC` on raised `#1E1E2A` | 7.4:1 | AAA — **binding surface** |
| *(former)* `#A0A0B0` on card | 6.8:1 | **AA only — the defect** |
| White on gold | 2.29:1 | Unusable — never do this |

**`CarUxRestrictions` flag values**, read from `android.car.jar`:
`NO_FILTERING 2` · `NO_KEYBOARD 8` · `NO_VIDEO 16` · `NO_SETUP 64` · `NO_TEXT_MESSAGE 128`

**Brand migration blast radius:** 32 files reference the purple brand. 29 use the token name
and cascade automatically; 4 hardcode the hex and need hand edits.

### 13.2 Related documents

| Document | Purpose |
|---|---|
| `docs/aaos-DESIGN.md` | Design system source of truth |
| `docs/AAOS_SCREEN_CONTRACT.md` | Per-screen UI, CTA, state, motion and component-reuse contract |
| `docs/AAOS_COMPLIANCE.md` | Variant-specific AAOS compliance gates and verification contract |
| `docs/aaos-screens.html` | 20 screens, static reference |
| `docs/aaos-app.html` | Interactive prototype |
| `docs/superpowers/specs/2026-08-02-aaos-foundation-restrictions-design.md` | A1 spec |
| `docs/superpowers/plans/2026-08-02-aaos-foundation-restrictions.md` | A1 implementation plan |
| `docs/AAOS_ARCHITECTURE.md` | Module and playback architecture |
| `docs/AAOS_UI_REDESIGN_PLAN.md` | **Superseded** by §3.3 — bannered, retained for its §1.1 and §2 |
| `docs/AAOS_DRIVING_STATE_TESTING.md` | To be created by A1 Task 1 |

### 13.3 Glossary

| Term | Meaning |
|---|---|
| **AAOS** | Android Automotive OS — Android running as the vehicle's head unit |
| **OEM media template** | The car manufacturer's own media UI, driven by an app's `MediaLibraryService` |
| **CarUxRestrictions** | Platform API reporting which interactions are restricted by driving state |
| **Distraction optimised** | A UI permitted while the vehicle is in motion |
| **Drill-down depth** | Levels below a tab root; the platform caps this while driving |
| **Eviction** | Removing a user from a screen that became restricted mid-session |
| **Chrome** | The persistent system bar, navigation rail and mini-player |
| **A1–A8** | Implementation phases |
| **Project B** | The mobile brand migration, independent of A1–A8 |

# NyasaPlayer AAOS — Custom-Flow Plan

> # ⛔ SUPERSEDED — 2026-08-02
>
> **Do not follow this document.** Its central decision has been reversed.
> The current sources of truth are **`docs/AAOS_PRD.md`**,
> **`docs/AAOS_SCREEN_CONTRACT.md`** and **`docs/AAOS_COMPLIANCE.md`**.
>
> §1 below commits to shipping AAOS as a Google-compliant media template app, with the
> custom Compose screens archived. **That is no longer the plan.** The decision recorded
> in `AAOS_PRD.md` §3.3 is the opposite: the **custom launcher is the product**, Play
> Store distribution in the AAOS media category is **not a hard requirement** for this
> release, and the compliant path is preserved as a `playstore` **build variant** rather
> than by deleting screens.
>
> This resolves the open question §1.1 raised — *"Resolving it is a decision, not a
> cleanup"*. The decision was taken on 2026-08-02: drop the media-template compliance
> goal for now, keep the template path first-class for Assistant and voice search, and
> build the 20-screen custom experience.
>
> **What in here is still accurate and worth reading:**
> - **§1.1 "What actually shipped"** — the two-surface inventory is correct and was the
>   evidence the new decision was made on.
> - **§2 "Why this path"** — the Play policy constraints are real. They are why the
>   `playstore` flavor exists at all. Only the conclusion drawn from them changed.
> - The archived screen inventory, as a record of prior work.
>
> **What is now wrong:**
> - §1's decision, and every instruction to archive or delete custom screens.
> - The claim that only Auth, Settings and Sign-Out may be custom.
> - §4.2's Settings cluster as the sole remaining custom UI — Settings is now one screen
>   of twenty, delivered in phase A7.
>
> Kept rather than deleted because §1.1 and §2 are load-bearing history: they document
> how the app reached a two-surface state, and the policy reasoning that a future Play
> submission would have to satisfy.

> Scope of what we build ourselves for AAOS, and what the OEM media template renders
> against `:core:playback`'s `MediaLibraryService`.
> Pair with `docs/AAOS_ARCHITECTURE.md`.

> **Status (2026-08-01): this plan is aspirational, not descriptive.** The Option-B
> migration below was decided but never completed. What is actually on
> `ek/aaos-option-b` today is *both* surfaces: the template path works end to end,
> **and** the pre-decision custom Compose screens are still shipping. The planned
> Settings cluster (§4.2) does not exist. Read §1.1 before trusting anything below it.
>
> *(Superseded 2026-08-02 — see the banner above. This status note remains accurate as a
> description of the state the new decision was made from.)*

---

## 1. Decision — Option B (Template Path)

On **2026-04-23** we committed to shipping NyasaPlayer AAOS as a Google-compliant
media template app: `<uses name="media" />` stays, and the OEM media template is the
UI for Home, Browse, Library, Now Playing, Queue, and Search. The only custom Compose
we ship on AAOS is the trio of parked-only flows that Google explicitly permits:

1. **Auth** — Google Sign-In via Credential Manager (existing `CarAuthScreen`).
2. **Settings** — Account / Audio Quality / About, reached via
   `android.intent.action.APPLICATION_PREFERENCES` (new cluster, this plan).
3. **Sign-Out confirmation** — modal dialog invoked from Account.

All prior Phase 1–5 custom-screen work (Home, Browse, Library, Queue, Full Player,
Album / Playlist / Artist detail, Downloads, Mini Player, etc.) was to be **archived as
design reference**. See `docs/stitch-screens/README.md` and the Archived Inventory
appendix below.

### 1.1 What actually shipped

The deletion half of Option B never happened. The custom screens predate the decision
(`Phase 5: Implement AAOS Screens from Figma`, 2026-03-09, through `CarQueueScreen`,
2026-04-23) and are still wired into `AutomotiveActivity` → `AutomotiveApp`. Meanwhile
the template path was finished on `ek/aaos-option-b` (browse tree, search, like button,
`onAddMediaItems`, emulator-verified). Both surfaces are live:

| Surface | Entry point | State |
| --- | --- | --- |
| OEM media template | `PlaybackService` (`MediaLibraryService`) + `MediaBrowseTree` | Working, emulator-verified |
| Custom launcher app | `AutomotiveActivity` → `AutomotiveApp` | Working, 7 screens (§4.1) |
| Settings cluster (§4.2) | — | **Not built** |

This is a real Play Store risk, not just doc drift: §2's first bullet is the reason
Option B was chosen, and the custom activities it warns about are still in the module.
Resolving it is a decision, not a cleanup — either delete the custom screens or drop
the media-template compliance goal.

## 2. Why this path

- Play Store AAOS rejects media apps that ship custom activities for playback or browse.
- Our `:core:playback` is already ~70% template-ready (`MediaLibraryService`, browse
  tree, search, queue wiring to `Player.setMediaItems` all in place).
- The template gives us OEM-styled Home / Browse / Now Playing / Queue / Search / error
  dialogs consistent with whatever car the app runs on, for free.
- What we lose — visual design personality on those screens — we exchange for a real
  distribution path and a dramatically smaller, more defensible codebase.

Trade-off analysis lives in `docs/AAOS_ARCHITECTURE.md §10`.

## 3. Visual language (Nyasa Dark) — for the custom screens only

Carry the existing `:core:common` theme unchanged so the Settings cluster is a 1:1
Compose port of the Stitch references.

| Token | Hex | Usage |
| --- | --- | --- |
| `NyasaBackground` | `#0D0D0D` | App background |
| `NyasaSurface2` | ~`#1A1A1A` | Cards, rows |
| `NyasaPrimary` | `#A855F7` | Primary accent, selection |
| `NyasaPrimaryDark` | `#7C3AED` | Gradient partner with `NyasaPrimary` |
| `NyasaTextSecondary` | `#A1A1AA` | Metadata, inactive labels |
| `NyasaTextTertiary` | ~`#71717A` | Kicker / caption text |

Typography (AAOS minima — applies to every custom screen we still ship):

- Headline (screen title): 30 sp bold
- Body-lg (list item title): 18 sp medium
- Body-md (metadata): 16 sp regular
- Touch target: **76 dp** minimum, 24 dp spacing between targets

Gradient convention: `Brush.horizontalGradient(listOf(NyasaPrimary, NyasaPrimaryDark))`
on primary CTAs only (Sign Out button).

Shape: 16 dp corner radius for cards/rows; 24 dp for modals; circle for icon buttons.

## 4. Screen inventory

### 4.1 Shipping today — `automotive/…/auto/ui/screens/`

Seven screens, all live. Only Home / Browse / Library are `CarScreen` enum nav
destinations; the rest are conditional branches in `AutomotiveApp`.

| # | Screen | Compose file | How it's reached |
| --- | --- | --- | --- |
| A.1 | Welcome / Sign-In | `CarAuthScreen.kt` | Gate shown until signed in |
| C.1 | Home | `CarHomeScreen.kt` | `CarScreen.Home` tab |
| C.2 | Browse | `CarBrowseScreen.kt` | `CarScreen.Browse` tab |
| C.3 | Library | `CarLibraryScreen.kt` | `CarScreen.Library` tab |
| C.4 | Artist liked songs | `CarArtistLikedSongsScreen.kt` | Drill-down from Library |
| C.5 | Full player | `CarFullPlayerScreen.kt` | Overlay |
| C.6 | Queue | `CarQueueScreen.kt` | Overlay |

Supporting: `CarTopBar`, `CarMiniPlayer`, `CarErrorOverlay` (`ui/components/`);
`AutomotiveAuthViewModel`, `AutomotiveContentViewModel`, `AutomotivePlayerViewModel`;
`CarUxRestrictionsHandler` for parked-vs-driving gating.

C.1–C.6 are exactly the screens Option B intended to delete. See §1.1.

### 4.2 Settings cluster — planned, **not built**

| # | Screen | Compose file | Notes |
| --- | --- | --- | --- |
| S.1 | Settings root | `CarSettingsScreen.kt` | Vertical list: Account / Audio Quality / About. 76 dp rows. |
| S.2 | Account | `CarAccountScreen.kt` | Avatar + name + email (from `AutomotiveAuthViewModel`); Sign-Out button opens dialog. |
| S.3 | Audio Quality | `CarAudioQualityScreen.kt` | Radio list: Low (96) / Normal (160) / High (320) / Very High (lossless). Persisted via new `AudioQualityPreference` DataStore in `:core:data`. |
| S.4 | About | `CarAboutScreen.kt` | Version (`BuildConfig`), build date, Terms / Privacy / Licences rows (static text). |
| S.5 | Sign-Out confirmation | `SignOutConfirmationDialog.kt` | Modal, two 76 dp buttons. Sign Out uses **accent gradient** (not red — sign-out is not destructive). Stitch `19-sign-out-confirmation.png`. |

### 4.3 What the template renders

Under Option B these were the surfaces we would *not* build. They are all working
template-side today — but §4.1 also still ships custom screens for the same content,
so "do not build" now reads as "already built twice":

- Home — from `MediaBrowseTree` root children.
- Browse — from `onGetChildren` for each category.
- Library — from `MediaBrowseTree` "Liked Songs" child (added in Commit 2).
- Now Playing — from `MediaMetadata` + registered `SessionCommand`s (including
  `CMD_TOGGLE_LIKE` added in Commit 2).
- Queue — from `Player.setMediaItems`.
- Search — from `onSearch` / `onGetSearchResult`.
- Error dialogs — from `LibraryResult.ofError(SessionError.…)`.

If one of those surfaces ever looks visibly wrong, the fix is in `:core:playback`
metadata / extras / errors, **not** a new custom Compose screen.

## 5. Workflow

1. ✅ Decide Option B (2026-04-23).
2. 🚧 Execute `/Users/admin/.claude/plans/let-s-go-with-b-piped-waffle.md` — four phased
   commits on `ek/aaos-ui-redesign`. **Partial**: the template-path work landed on
   `ek/aaos-option-b` (Liked Songs node, `CMD_TOGGLE_LIKE`, browse-tree connect/play
   fixes). The custom-screen removal and the Settings cluster did not.
3. ✅ Template verified on AAOS emulator: app appears in the media source picker,
   browse tree renders with template tabs, playback works, like button reflects
   per-track state. Discovery required `androidx.car.app.launchable` meta-data on the
   service plus the legacy `MediaBrowserService` action — see CLAUDE.md "AAOS rendering".
4. 🔲 **Decide the surface question in §1.1** — delete C.1–C.6 or abandon template
   compliance. Blocks everything below.
5. 🔲 Settings cluster (§4.2) + `SettingsActivity` / Sign-Out flow.
6. 🔲 File follow-up work (Downloads prefs, Crossfade, advanced Settings) as separate
   tickets — out of scope for this migration.

## 6. Open questions

- **Audio quality enforcement**: `:core:playback` currently picks the stream URL from
  `Song.resolvedAudioUrl` with no bitrate selection. Wiring the preference into the
  mapper is a follow-up (not blocking the Settings UI).
- **Terms / Privacy / Licences content**: is there canonical copy we should link to, or
  should the About screen be a static in-app text view? Default plan: in-app text.

---

## Archived inventory — design reference only

The tables below are the pre-decision Phase 1–5 custom-screen inventory generated in
Stitch. They are kept for visual reference and potential future brand moments (splash,
onboarding, mobile features). They are **not** implementation targets on AAOS.

**Stitch project**: `6899228466021446121` (`NyasaPlayer AAOS`)
**Design system**: `Nyasa Dark` — asset `13435337818663503252`
**Exported PNGs**: `docs/stitch-screens/`

### Archive — Phase 0 existing-screen redesign

| # | Stitch screen | Screen ID | Originally mapped to |
| --- | --- | --- | --- |
| 0.1 | Auth / Sign-in | `37341c693f114341b4ef9f3c88e1db6a` | `CarAuthScreen.kt` — **still active** |
| 0.2 | Home | `244bf29072e9472cacad387810f9a1fd` | Now: template-rendered from browse root |
| 0.3 | Browse — default | `ddf6864566e9496a8ac64082d489870e` | Now: template-rendered |
| 0.4 | Browse — searching | `0952d735f6d24f1493d8c97d741b2090` | Now: template-rendered via `onSearch` |
| 0.5 | Browse — driving mode | `99a01acbf10d4dccad1660cee0965b63` | Template handles driving restrictions |
| 0.6 | Library | `9d6b6cfe0222463bbbfd126a9a9bcf3b` | Now: template-rendered from Liked Songs child |
| 0.7 | Library — sign-out modal | `286557bc0f9d4e168e06a39170409600` | **Still active** as `SignOutConfirmationDialog` |
| 0.8 | Artist liked songs | `4bfaa6e8b5fa48a4a2bc61c363aed528` | Now: template-rendered |
| 0.9 | Full player | `d55966d448dd4a059f1e47269d2ca4d0` | Now: template-rendered from `MediaMetadata` |
| 0.10 | Full player — error overlay | `da4f0f05339e43e69e3fc1c15d5f707e` | Now: template error from `SessionError` |
| 0.11 | Mini player — with playback | `e3e987aaed09497ca33dda9ac3e7b85c` | Now: template-rendered |

### Archive — Phase 1–5

- Phase 1 (Queue, Voice Search): template-rendered from `setMediaItems` / `onSearch`.
- Phase 2 (Playlist / Album / Artist details, Full player with source chip):
  template-rendered from deeper browse-tree children.
- Phase 3 (Continue listening, Downloads, Mini player with up-next peek):
  Continue-listening surfaces in `MediaBrowseTree` "Recently Played"; Downloads is a
  future backend-gated feature not in scope.
- Phase 4 (Settings): **partially active** — S.1–S.5 above are the shipping subset.
- Phase 5 (Wavy M3 progress, podcast speed, sleep timer): design reference only.

See git history for the full detailed prompt list if needed for design reference.

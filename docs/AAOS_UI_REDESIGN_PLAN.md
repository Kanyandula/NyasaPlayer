# NyasaPlayer AAOS — Custom-Flow Plan

> Scope of what we actually build ourselves for AAOS. Everything else is rendered
> by the OEM media template against `:core:playback`'s `MediaLibraryService`.
> Pair with `docs/AAOS_ARCHITECTURE.md`.

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
Album / Playlist / Artist detail, Downloads, Mini Player, etc.) is **archived as design
reference**. See `docs/stitch-screens/README.md` and the Archived Inventory appendix
below.

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

## 4. Screen inventory (what we actually build)

### 4.1 Auth — already exists

| # | Screen | Compose file | Source of truth |
| --- | --- | --- | --- |
| A.1 | Welcome / Sign-In | `CarAuthScreen.kt` | Stitch `01-welcome-screen.png` |

### 4.2 Settings cluster — new (Commit 4 of the Option-B migration)

| # | Screen | Compose file | Notes |
| --- | --- | --- | --- |
| S.1 | Settings root | `CarSettingsScreen.kt` | Vertical list: Account / Audio Quality / About. 76 dp rows. |
| S.2 | Account | `CarAccountScreen.kt` | Avatar + name + email (from `AutomotiveAuthViewModel`); Sign-Out button opens dialog. |
| S.3 | Audio Quality | `CarAudioQualityScreen.kt` | Radio list: Low (96) / Normal (160) / High (320) / Very High (lossless). Persisted via new `AudioQualityPreference` DataStore in `:core:data`. |
| S.4 | About | `CarAboutScreen.kt` | Version (`BuildConfig`), build date, Terms / Privacy / Licences rows (static text). |
| S.5 | Sign-Out confirmation | `SignOutConfirmationDialog.kt` | Modal, two 76 dp buttons. Sign Out uses **accent gradient** (not red — sign-out is not destructive). Stitch `19-sign-out-confirmation.png`. |

### 4.3 What we do **not** build

Everything rendered by the OEM template:

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
   commits on `ek/aaos-ui-redesign`.
3. 🔲 Verify on AAOS emulator: launcher opens template; Settings gear opens
   `SettingsActivity`; Sign-Out returns to Auth.
4. 🔲 File any follow-up work (Downloads prefs, Crossfade, advanced Settings) as
   separate tickets — out of scope for this migration.

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

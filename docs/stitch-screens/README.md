# Stitch design exports — reference only

These 19 PNGs were generated in Stitch (`projects/6899228466021446121`) during the
2026-04 AAOS redesign pass. They are preserved as **design-intent references**, not as
implementation targets.

On 2026-04-23 we committed to **Option B — Template Path** for AAOS (see
`../AAOS_UI_REDESIGN_PLAN.md` §1). Under Option B the OEM media template renders
Home, Browse, Library, Now Playing, Queue, and Search from our `MediaLibraryService`
browse tree — we do not ship custom Compose screens for those flows.

**Still implementation targets:**
- `01-welcome-screen.png` — `CarAuthScreen`
- `19-sign-out-confirmation.png` — `SignOutConfirmationDialog`

**Design reference only (no Compose port):**
Everything else. These can inform visual language for the mobile app, future brand
moments (splash, onboarding), or a potential redesign if AAOS policy ever permits
fully custom media UIs.

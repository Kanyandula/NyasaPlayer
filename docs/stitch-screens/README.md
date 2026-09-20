# Stitch design exports — historical reference only

These 19 PNGs were generated in Stitch (`projects/6899228466021446121`) during the
2026-04 AAOS redesign pass. They are preserved as a **record of design intent from before
the current PRD**, not as implementation targets.

They predate `docs/aaos-DESIGN.md` and carry values the design system later overrode —
72px controls, `#A0A0B0` secondary text, and a chrome contract that had not been settled
(six screens produced six different system bars). Do not measure anything against them.

Current sources of truth, in resolution order:

1. `docs/AAOS_COMPLIANCE.md` — AAOS safety and variant compliance
2. `docs/AAOS_PRD.md` — product scope and phase acceptance
3. `docs/AAOS_SCREEN_CONTRACT.md` — per-screen and shared-component implementation detail
4. `docs/aaos-DESIGN.md` — tokens, chrome contract, measured contrast

## What shipped instead

The 2026-04-23 "Option B — template path" decision these exports were filed under was
**reversed on 2026-08-02** (`docs/AAOS_PRD.md` §3.3): the custom launcher is the product,
and 18 of the 20 designed screens ship in `:automotive`'s `oem` flavor. `01-welcome-screen.png`
became `CarAuthScreen` and `19-sign-out-confirmation.png` became `CarSignOutConfirmation`
(a component, not the `SignOutConfirmationDialog` these notes once named), but both were
re-laid-out against the gold design system rather than ported from these PNGs.

Kept because they document how the in-car visual language was arrived at, and they remain
useful input for mobile brand moments (splash, onboarding) under Project B.

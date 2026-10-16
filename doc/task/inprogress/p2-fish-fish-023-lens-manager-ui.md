# FISH-023 — Lens manager Material You, icons, motion, in-screen rename/delete

**Status:** In progress
**Started:** 2026-10-15
**Branch:** `dev`
**Parent:** FISH-022 (grouped lens manager, pushed)
**Plan:** approved in-session; phases P1 icons, P2 surface + DiffUtil, P3 motion, P4 haptics + 1-lens state, P5 rename/delete, P6 a11y/RTL/large font.

## Scope

Owner feedback: manager not fully Material You, no meaningful icons, no impressive animation. Out of scope: dynamic color/dark pass, drag reorder (spec keeps pager order), FAB. All motion gated by `LensPhysicsPolicy.shouldReduceLensMotion`. No new dependency.

## Progress

- [x] P1 icons: vector chevron + rotation, folder/apps/lens icons, check in Active chip. Verified TECNO KJ7 `115333744A005844`: manager widget 21/21. No ad visible; screenshot smoke confirmed icons/tints/chip.
- [ ] P2 surface + DiffUtil
- [ ] P3 motion
- [ ] P4 haptics + 1-lens state
- [ ] P5 rename/delete in manager
- [ ] P6 a11y / RTL / large font

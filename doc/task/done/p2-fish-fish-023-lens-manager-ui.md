# FISH-023 — Lens manager Material You, icons, motion, in-screen rename/delete

**Status:** Done
**Started:** 2026-10-15
**Branch:** `dev`
**Parent:** FISH-022 (grouped lens manager, pushed)
**Plan:** approved in-session; phases P1 icons, P2 surface + DiffUtil, P3 motion, P4 haptics + 1-lens state, P5 rename/delete, P6 a11y/RTL/large font.

## Scope

Owner feedback: manager not fully Material You, no meaningful icons, no impressive animation. Out of scope: dynamic color/dark pass, drag reorder (spec keeps pager order), FAB. All motion gated by `LensPhysicsPolicy.shouldReduceLensMotion`. No new dependency.

## Progress

- [x] P1 icons: vector chevron + rotation, folder/apps/lens icons, check in Active chip. Verified TECNO KJ7 `115333744A005844`: manager widget 21/21. No ad visible; screenshot confirmed icons/tints/chip.
- [x] P2 surface + DiffUtil: tonal M3 row cards with corner-position variants; AppBarLayout collapsing title; `LensGroupRowDiff` replaces full refreshes. KJ7 manager widget 23/23, JVM row/diff tests 8/8, `lintDevDebug` zero errors. On-device smoke confirmed expanded title + card composition; no ad overlay.
- [x] P3 motion: DiffUtil expand/collapse animations + chevron rotation; action bar slides in/out; all motion gated by `LensPhysicsPolicy`, reduced-motion snaps and disables itemAnimator. KJ7 manager widget 28/28 (motion-on/off tests pass). Regression classes: Home 26, quick-switch 12, multi-lens 4, backup 11 — all green. Full unit 866/866 + lint 0 errors/31 warnings.
- [x] P4 haptics + 1-lens state: system-respecting selection/success/rejection feedback (reduced-motion gate), one-lens guidance card reuses translated strings.
- [x] P5 rename/delete in manager: icon overflow popup, rename persists, delete confirms/cancels, active-lens fallback, last-lens delete guard in transactional DAO path. Widget/DAO tests cover rename/delete/cancel/last lens; popup theme/icons.
- [x] P6 a11y / RTL / large font: 4 widget tests cover Arabic RTL mirroring, 200% font reflow/48dp target, header/row/button labels.

## Final verification (2026-10-16)

TECNO KJ7 `115333744A005844`: manager **28/28**, accessibility RTL/font **4/4**, Home lens management **26/26**, quick switch **12/12**, multi-lens **4/4**, backup integration **11/11**, migration **4/4**. Full unit **866/866**, lint **0 errors / 27 warnings**, `scripts/check-secrets.sh` exit 0, `git diff --check` clean. Visual smoke confirmed icons, card surfaces, collapsing title, active/group chips, row Rename/Delete popup; no ad overlay. Separate Arabic RTL smoke completed in Lens Manager; app language restored to Vietnamese and system font scale restored to `1.15`. 200% font reflow also covered by the on-device accessibility smoke/test evidence. No destructive smoke data changes; rename/delete and last-lens guard covered by tests. No new dependency.

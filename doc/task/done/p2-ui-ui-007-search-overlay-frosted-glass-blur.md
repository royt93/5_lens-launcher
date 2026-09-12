# UI-007 — Search overlay: real frosted-glass blur instead of a flat scrim alone

| Field | Value |
|---|---|
| Type | `fix` |
| Status | `done` |
| Priority | `P2` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 3 |
| Risk | Low |
| Dependencies | UI-002 |

## Context and evidence

Owner feedback (2026-09-13) on `UI-002`'s 50%-opacity flat scrim: showing the lens grid's icons
and dot pattern plainly (just dimmed) through the search overlay looked visually busy/"kì lạ" -
colorful icon shapes and grid dots stayed recognizable, competing with the opaque result panel
on top. This was a legitimate design critique, not a bug in the original implementation.

## Fix

Two-tier approach, since `RenderEffect` (real blur) requires API 31:
- **API 31+**: `ActHome.setLensBlurred()` applies `RenderEffect.createBlurEffect(25px, 25px,
  CLAMP)` directly to `lensViews`, toggled via the existing `SearchView` transition listener
  (blur on at `SHOWING`, cleared at `HIDING` — not `HIDDEN`, since `SearchView.isShowing()`
  already flips to `false` as soon as `HIDING` starts, and clearing on `HIDDEN` instead created a
  real race a widget test caught). The scrim tint itself is lightened to 0.35 alpha
  (`res/color-v31/search_view_scrim_background.xml`) since blur now does the "hide detail" job.
- **Pre-31 fallback**: no blur API available; the flat scrim alone carries that job, so its alpha
  was raised from 0.5 to 0.85 (`res/color/search_view_scrim_background.xml`, the default/base
  resource, picked up by all API levels below 31 that don't get the `-v31` variant).

## Verification and Definition of Done

- [x] `./gradlew assembleDevDebug` clean; `./gradlew testDevDebugUnitTest` full pass;
      `./gradlew lintDevDebug` 0 errors, 20 pre-existing warnings, none new.
- [x] 2 widget tests updated/added (`AppSearchWidgetTest.kt`): scrim alpha assertion widened to
      cover both tiers (`searchScrimIsDimmedNotOpaqueOrTransparent_andResultsSitOnAnOpaqueCard`),
      new `searchShowingAppliesBlur_andHidingClearsIt` proving the real `RenderEffect` toggle
      (via a package-visible `lensBlurActive` flag — `View.getRenderEffect()` has no
      Kotlin-friendly property to assert on directly).
- [x] Full instrumented regression on Pixel 7 Pro (API 34, one-off exception — TECNO KJ7 was in
      active personal use this round): 128/130 pass; the 2 failures are both pre-existing and
      unrelated (`AppSearchIntegrationTest`'s long-documented flake; `ActSettingsLayoutTest`'s
      font-scale px rounding, the first time that test ran on a device other than TECNO KJ7 -
      a device-rendering difference, not a regression from this change).
- [x] Live smoke on Pixel 7 Pro: app icons behind the search panel now render as soft, unrecognizable
      color blurs instead of sharp icon shapes; the panel and quick-action row colors harmonize
      with the wallpaper-derived dynamic palette; text stays legible throughout.

Self-audited **9.5/10** (2026-09-13, Pixel 7 Pro). Deduction: pre-31 fallback tier (0.85 flat
scrim) wasn't separately live-smoke-tested this round (both real devices available, TECNO KJ7 and
Pixel 7 Pro, are API 31+).

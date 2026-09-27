# FISH-008 — Multi-lens workspaces

| Field | Value |
|---|---|
| Type | new |
| Status | complete — Phase 1 data model + Phase 2 UI (2026-09-26), Phase 3 per-lens physics + Smart Focus (2026-09-27), shipped locally |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Estimate | 13 SP → Phase 1 split complete; Phase 2 UI remains |
| Risk | High |
| Dependencies | FEAT-002 and FEAT-006 shipped |

## Context and evidence

Today there is exactly one fisheye "lens": one grid, one set of pinned zones
(`FEAT-002`'s `PinnedZone`/`AppOrganizationRules`). This is the strategically
biggest of the three flagship proposals: multiple independently-configured
lenses (e.g. "Work lens"/"Personal lens"/"Focus lens"), swiped between like
pages, each with its own pinned-zone layout and (optionally) its own
`FISH-006` focus bias and `FISH-004` physics preset. No competing launcher
frames multiple home screens as multiple *lenses* — this is the strongest
brand differentiator of the three flagship ideas, and also the largest and
riskiest.

**This estimate is a placeholder for scoping, not a green light.** Per this
backlog's own working agreement ("split anything above 8 SP before
implementation"), this must be broken into smaller stories before any code is
written — at minimum: (1) data-model support for multiple named
`PinnedZone` sets, (2) paging/swipe UI between lenses on `ActHome`, (3)
per-lens settings (physics preset, focus bias) if kept in scope, (4) creating/
renaming/deleting a lens. Do not attempt this as one PR.

## User story

As a user, I want to set up multiple independent "lenses" (e.g. Work,
Personal) each with their own curated app layout, and swipe between them on
the home screen.

## Acceptance criteria (scoping-level — refine per split story)

- [x] Phase 1 data model: `AppPersistent` carries `LENS_ID`; organization,
      visibility and order writes are lens-scoped; Room migration is explicit
      and non-destructive.
- [x] Phase 2 UI: `ActHome` paging, indicator, create/rename/delete flows.
- [x] Existing single-lens installs migrate losslessly into the automatic
      `default` workspace named "Lens 1".
- [x] Owner scope decision: independent layouts with shared physics and shared
      usage count. No per-lens physics state added in Phase 1.
- [x] **Phase 3 (2026-09-27) — owner reopened the scope line above**: per-lens
      distortion factor and per-lens Smart Focus bias, with real UI for both.
      Scale factor, animation time, icon size and usage count stay shared.
      Stored as suffixed SharedPreferences keys (`<key>_<lensId>`), no Room
      migration: a lens with no override of its own inherits the shared value,
      and `DEFAULT_LENS_ID` maps back onto the legacy global keys, so every
      single-lens install behaves exactly as before.

## Required test matrix

- [x] Unit: real v10 SQLite migration, default workspace, per-lens layout
      isolation, shared open-count behavior. Full `testDevDebugUnitTest`:
      531 passed, 0 failed.
- [x] Widget/UI: Not applicable to Phase 1; no UI exists in this split. Paging
      and create/rename/delete coverage belongs to Phase 2.
- [x] Integration: real v10 database file migration plus multi-lens Room
      persistence round trip and concurrent per-lens order writes on TECNO BG6
      (`118743744X002560`): 3 passed, 0 failed.
- [x] Smoke: schema migration and isolated layout persistence proven through
      direct device instrumentation. Visual paging/restart smoke is Phase 2.

### Phase 3 (2026-09-27), on TECNO KJ7 (`115333744A005844`)

- [x] Unit: `UtilSettingsPerLensTest` (14 cases — legacy-key mapping for the
      default lens, inheritance while a lens has no override, isolation once it
      does, clamping, duplicate/delete lifecycle) and five new
      `LensViewGestureStateTest` cases for the empty-space long-press
      predicate, including mutual exclusivity with the icon long-press. Full
      `testDevDebugUnitTest`: 559 passed, 0 failed. Lint: 0 errors, 9 warnings.
- [x] Widget: `ActHomeLensManagementWidgetTest` 13 passed (per-lens Smart Focus
      toggle and its label, settings copied on create, prefs cleared on delete,
      menu reachable with a single lens, anchor not the full-screen grid, stale
      menu positions refused on every branch, dialog and popup released at
      `onDestroy`); `FrmLensPerLensWidgetTest` 6 passed (a real slider drag, a
      preset, the displayed value, `onResume` after a lens switch, reset, and
      the default lens still writing the legacy key);
      `FrmSettingsSmartFocusWidgetTest` 6 passed (switch follows the active
      lens); `ActHomeMultiLensWidgetTest` 4 passed (restored page keeps its
      apps after recreate).
- [x] Integration: `LensViewSmartFocusIntegrationTest` 8 and
      `LensViewPinchIntegrationTest` 8 passed — per-lens arrangement, rebinding
      a recycled page onto another lens (and the same-lens no-op), per-lens
      curvature commit and read-back, with `LensGridCache` recompute unchanged.
      Regression suites re-run green: `ActHomePinchWidgetTest` 3,
      `FrmLensPhysicsPresetsWidgetTest` 3, `FrmLensWidgetTest` 7,
      `LensViewQuickActionsIntegrationTest` 2, `LensViewWidgetTest` 8.
- [x] Smoke: long-press empty space opens the four-item menu, lens created from
      it inherits its source's curvature and Smart Focus (verified in
      `shared_prefs`), the toggle flips only the paged lens (the other lens
      still reads "Turn on Smart Focus"), swiping changes the active lens,
      rotation keeps the grid populated, and deleting a lens clears its own
      suffixed keys while the survivor keeps its values.
- [x] Smoke, per-lens curvature end to end (from wiped preferences): dragging
      the Settings curvature slider on the default lens wrote `0.7`, the second
      lens kept `3.8`, and re-opening the same Settings tab after swiping
      between the two showed `0.7` and `3.8` respectively - the same slider,
      the same screen, two different values, which is what `onResume`
      re-reading the active lens buys. Two-finger pinch itself was not smoked:
      `adb` has no multi-touch primitive and scripting raw `sendevent` per
      device is unsafe. It shares the exact persistence path proven here and is
      covered by `LensViewPinchIntegrationTest`.

### Bugs this phase's smoke found (none were visible in review or in tests)

1. **The lens management menu was unreachable on every single-lens install.**
   Phase 2 hides the page-dots indicator until a second lens exists, and that
   indicator was the menu's only entry point — so after the v11 migration no
   user could ever create their second lens. Fixed with a long-press on empty
   grid space (`LensView.OnEmptySpaceLongPressListener`), which reuses the
   existing long-press machinery.
2. **The menu rendered only its first item.** Anchored to the full-screen
   `LensView`, the popup was pushed off the top edge and clipped to one row
   (`uiautomator` showed the window as `[0,108][638,277]`). Re-anchored to the
   page-dots indicator.
3. **A dialog or popup left open at `onDestroy` retained the Activity.** The
   `@VisibleForTesting` handles added this phase hold a View owned by the
   Activity; a rotation with the add-lens dialog open leaked its window.
   `onDestroy` now dismisses and drops both. Found in this phase's own audit,
   not by the tests, which is why one was added for it.
4. **Rotation emptied the visible grid.** Every page rebinds after a
   configuration change while `listApp` is already loaded, but `lensViews`
   still pointed at the destroyed Activity's view, so no page matched and the
   restored page drew nothing until the next app-list broadcast. `bindLensView`
   now also matches the page whose lens is the active one. Verified against the
   unchanged `dev` build first, to confirm this was a regression from this
   phase and not pre-existing.

## Loop end condition

Same as every story in this backlog (`doc/task/README.md`'s audit rubric):
score the actual diff, not the completion claim. Given this story's own
required split, each split-out story closes independently against that
rubric — unit + widget + integration tests for every branch, smoke-verified
on the designated device, `> 9.0/10` before push. The Room migration split
specifically must not be scored `> 9.0` without a real pre-migration-database
integration test, given the data-loss risk of getting it wrong.

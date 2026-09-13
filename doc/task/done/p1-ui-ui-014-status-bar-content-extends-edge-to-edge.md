# UI-014 — Status bar harmony via real edge-to-edge content (not just Window paint)

| Field | Value |
|---|---|
| Type | `fix` |
| Status | `done` |
| Priority | `P1` |
| Evidence | `confirmed` |
| Epic | Material You revamp |
| Estimate | 2 |
| Risk | Low |
| Dependencies | UI-013 |

## Context

Owner reported the search screen's status bar looked transparent/not seamless with the search
panel's dynamic scrim color. Picked via `AskUserQuestion`: **combine both** - let real content
extend edge-to-edge under the status bar (the same fix already proven for the nav bar in UI-013)
AND keep `Window.setStatusBarColor`/`setNavigationBarColor` as a fallback for OS
versions/nav-bar modes where a real paintable bar surface still exists.

## Change

`ActHome.onCreate` no longer applies `UIUtils.setupEdgeToEdge2` (inset padding) to `rootLayout` at
all - `rootLayout` and its direct children (`lensViews`, `searchCoordinator`/`searchView`) now all
extend genuinely edge-to-edge on every side by default. Two targeted, narrower inset handlers
replace it:

- `lensViews` (the home grid) gets its own top padding via `setupEdgeToEdge2`, so icons still
  clear the status bar in the plain home-screen state, matching the pre-existing behavior.
- `searchBar` (the collapsed pill) gets a new `applyStatusBarInsetAsTopMargin` helper that adds
  the status bar inset to its *margin* (not padding) - `setupEdgeToEdge2`'s `setPadding()` would
  have inset the pill's own internal icon/hint text instead of moving the whole pill down, since
  it's a shaped `SearchBar` widget, not a plain container like `rootLayout`.

`searchView` (the expanded search panel) intentionally gets no inset handling at all now - its own
scrim background genuinely reaches the true top edge, the same fix already shipped for the bottom
edge in UI-013. `setSearchSystemBarsHarmonized()`'s `Window.setStatusBarColor`/
`setNavigationBarColor` calls are unchanged and kept as the belt-and-suspenders fallback per the
owner's chosen option.

## Verification and Definition of Done

- [x] `./gradlew assembleDevDebug` clean; `./gradlew testDevDebugUnitTest` full pass;
      `./gradlew lintDevDebug` 0 errors, 20 pre-existing warnings, none new.
- [x] Full instrumented regression on Pixel 7 Pro: 141/143 pass; the 2 failures are the same
      already-disclosed pre-existing flakes (font-scale rounding from `UI-007`/`UI-008`; an
      order-dependent IME-action test flake first seen in `UI-013`, reproduced once more here,
      unrelated to this change - both files/features this story never touches).
- [x] Live smoke on Pixel 7 Pro: collapsed pill still sits correctly below the status bar/clock
      (margin fix confirmed, no overlap); expanded search panel's scrim now reaches the literal
      top edge with zero visible seam, confirmed via screenshot.

Self-audited **9.5/10** (2026-09-13, Pixel 7 Pro).

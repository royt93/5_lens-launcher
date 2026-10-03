# UI-025 — Unified popup menus

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed (owner report + code audit) |
| Epic | Home-screen usability |
| Estimate | 3-5 SP |
| Risk | Medium |
| Dependencies | UI-022, FISH-008, FISH-016 |

## Owner decision (2026-10-02)

Owner saw a square-cornered popup next to rounded dialogs. Audit: `ActHome.showLensManagementMenu()` builds `new PopupMenu(this, anchor)` and skips `PopupMenuTheme`; every other popup already uses it. Owner chose one story covering both rounding and anchoring the icon quick-actions menu next to the pressed icon (it opened in the screen centre; see the `ponytail:` note in `LensView.showQuickActionsMenu`).

Design: `docs/superpowers/specs/2026-10-02-ui-025-unified-popup-menus-design.md`. Plan: `docs/superpowers/plans/2026-10-02-ui-025-unified-popup-menus.md`.

## Acceptance criteria

- [x] The lens-management menu uses `PopupMenuTheme` (16 dp rounded background).
- [x] A JVM test fails if any `PopupMenu(` in `app/src/main` is built from an un-themed context.
- [x] Long-pressing an icon opens its quick-actions menu anchored at that icon's cell, for different icons.
- [x] The anchor view is removed on dismiss and on detach; never more than one exists.
- [x] Unattached or non-FrameLayout parents fall back to the old path without crashing.
- [x] Unit, widget, integration, lint, full instrumented, TECNO KJ7 smoke, independent review, audit > 9.0.

## Scope added by the owner during the story (2026-10-03)

- Every popup item carries an icon (owner chose "icon for every item" over "text only"): 13 new Material vectors, a shared `@color/popup_menu_icon_tint` selector that dims disabled items, `autoMirrored` move arrows, favorite icon follows favorite state. Guarded by `PopupMenuIconConventionTest` (JVM) and `lensMenu_everyItemHasAnIcon` (device) because the lens menu is built in code.
- The lens menu opens at the pressed point (owner chose "anchor at the long-press point"): it used to anchor on the page-dots indicator, which is GONE on a single lens, so it stuck to the top-left corner. `OnEmptySpaceLongPressListener` now receives `(x, y)`.
- Search-bar recent icon tinted `colorOnSurfaceVariant` (hard-white vector was near-invisible on the light bar).

## Evidence

Devices: Pixel 7 Pro `2B051FDH3006MU` (owner decision, used while TECNO KJ7 was not attached) for development, smoke and the first full run; then **TECNO KJ7 `115333744A005844`**, the policy device, for the final full gate once it was attached.

- JVM 679/679, lint 0 errors / 8 warnings.
- Full instrumented on **KJ7: 455/455 OK**; on Pixel earlier: 455/455 OK.
- `DndQuickActionIntegrationTest` is guarded by `assumeTrue(system filter == ALL)`. On the Pixel (manual DND on) that guard skips the 2 observing tests. On KJ7 DND is off (`zen_mode=0`, filter ALL), so the guard does not trigger and all 3 run (class run alone: OK, 3 tests). That they ran is inferred from the device state, not from a SKIPPED/passed breakdown, which the runner output does not print.
- RED first for every behaviour change; mutation checks (anchor `bounds.bottom` -> `0`, and removing the detach cleanup) both failed the right test.
- Real bug found on device: removing the anchor synchronously inside `onDetachedFromWindow` crashed the parent's detach loop (`NullPointerException` in `ViewGroup.dispatchDetachedFromWindow`). Fixed by deferring the removal while detaching; the deferral now lives in one shared helper (`PointAnchor`) with its own JVM test.
- Smoke on Pixel, `mCurrentFocus` checked every step, no ad over the UI: icon menu opens beside the icon on top/middle/bottom rows (flips above near the bottom edge); lens menu opens at the press with 8 icons; rotate to landscape with the menu open, BACK, rotate back, 0 `FATAL EXCEPTION`, rotation settings restored; pull-down search in Clean on and off, BACK closes it; Clean toggled on and off from the lens menu; pan; `clean_lens_mode` removed afterwards. **Smoke was not repeated by hand on KJ7**; KJ7 evidence is the automated suite only.

## Review (`/code-review high`, 8 findings)

Fixed, each with a RED test first: reopening the lens menu removed the new menu's anchor; favorite icon did not follow state; move arrows did not mirror in RTL; disabled items kept a full-contrast icon; tint fallback colour `0` was transparent (changed to a visible fallback).

Also fixed after the review: the 1x1 anchor logic duplicated in `LensView` and `ActHome` is now one `PointAnchor` helper (6 JVM tests); the `ActHome` copy had no detach-time deferral.

**Not fixed:**
- Icon-menu anchor ignores pager translation. Not reproduced: `LensView` uses no `translationX`/scroll. Only a theoretical case mid-settle.
- Fully-qualified names inline in `ActHome`: style only.

## Disclosed, not verified

- The anchor child count was not measured on a device (`uiautomator dump` failed on the Pixel); it rests on `quickActionsMenu_neverLeavesMoreThanOneAnchor`, `lensMenu_removesItsAnchorWhenDismissed` and the new `PointAnchorTest`.
- Pinch was not tried by hand (`adb` cannot inject two fingers reliably); it rests on `LensViewPinchIntegrationTest`.
- Horizontal lens paging was not tried: the test devices have one lens.
- No automated rotation test for the anchor; rotation was a smoke on the Pixel only.
- Manual smoke was done on the Pixel, not the policy device KJ7.
- `deleteLens_alsoClearsThatLensOwnSettings` failed once in a full run and passed alone and in later class and full runs; the fixed sleep became a condition wait. Root cause of the single failure not proven.

## Audit

Self-audit **9.3 / 10**. Deductions: manual smoke only on the Pixel, not KJ7; anchor count, pinch and paging not verified by hand; 2 review findings left open (one not reproduced, one style). Raised from 9.1 because the full suite is now green on the policy device and the duplicated anchor code is gone. Not pushed.

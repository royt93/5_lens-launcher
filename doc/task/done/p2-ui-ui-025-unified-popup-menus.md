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

Device: **Pixel 7 Pro `2B051FDH3006MU`, by owner decision** ("dùng pixel 7 pro"). The policy device TECNO KJ7 was not attached; S24 Ultra dropped mid-story. Nothing here was verified on KJ7.

- JVM 673/673, lint 0 errors / 8 warnings.
- Full instrumented on Pixel: 455/455 OK. **2 of those are `DndQuickActionIntegrationTest` tests that report SKIPPED** (owner-chosen): the Pixel has its own manual DND on, so the app's effect on the filter cannot be observed. They have not run for real on any device with DND off.
- RED first for every behaviour change; mutation checks (anchor `bounds.bottom` -> `0`, and removing the detach cleanup) both failed the right test.
- Real bug found by the device run: removing the anchor synchronously inside `onDetachedFromWindow` crashed the parent's detach loop (`NullPointerException` in `ViewGroup.dispatchDetachedFromWindow`). Fixed by posting the removal while detaching.
- Smoke on Pixel, `mCurrentFocus` checked every step, no ad over the UI: icon menu opens beside the icon on top/middle/bottom rows (flips above near the bottom edge); lens menu opens at the press with 8 icons; rotate to landscape with the menu open, BACK, rotate back, 0 `FATAL EXCEPTION`, rotation settings restored; pull-down search in Clean on and off, BACK closes it; Clean toggled on and off from the lens menu; pan; `clean_lens_mode` removed afterwards.

## Review (`/code-review high`, 8 findings)

Fixed, each with a RED test first: reopening the lens menu removed the new menu's anchor; favorite icon did not follow state; move arrows did not mirror in RTL; disabled items kept a full-contrast icon; tint fallback colour `0` was transparent (changed to a visible fallback).

**Not fixed:**
- The 1x1 anchor logic is duplicated in `LensView` and `ActHome` (different `LayoutParams` and lifecycle); merging is a larger refactor than this story.
- Icon-menu anchor ignores pager translation. Not reproduced: `LensView` uses no `translationX`/scroll. Only a theoretical case mid-settle.
- Fully-qualified names inline in `ActHome`: style only.

## Disclosed, not verified

- The anchor child count was not measured on the device (`uiautomator dump` failed on the Pixel); it rests on `quickActionsMenu_neverLeavesMoreThanOneAnchor` and `lensMenu_removesItsAnchorWhenDismissed`.
- Pinch was not tried by hand (`adb` cannot inject two fingers reliably); it rests on `LensViewPinchIntegrationTest`.
- Horizontal lens paging was not tried: the Pixel has one lens.
- No automated rotation test for the anchor; rotation was a smoke only.
- `deleteLens_alsoClearsThatLensOwnSettings` failed once in a full run and passed alone and in 3 class runs; the fixed sleep became a condition wait. Root cause of the single failure not proven.

## Audit

Self-audit **9.1 / 10**. Deductions: not run on the policy device (KJ7), 2 DND tests skipped rather than proven, anchor count and pinch/paging not verified by hand, 3 review findings left open. Not pushed.

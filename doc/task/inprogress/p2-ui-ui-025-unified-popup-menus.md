# UI-025 — Unified popup menus

| Field | Value |
|---|---|
| Type | fix |
| Status | inprogress |
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

- [ ] The lens-management menu uses `PopupMenuTheme` (16 dp rounded background).
- [ ] A JVM test fails if any `PopupMenu(` in `app/src/main` is built from an un-themed context.
- [ ] Long-pressing an icon opens its quick-actions menu anchored at that icon's cell, for different icons.
- [ ] The anchor view is removed on dismiss and on detach; never more than one exists.
- [ ] Unattached or non-FrameLayout parents fall back to the old path without crashing.
- [ ] Unit, widget, integration, lint, full instrumented, TECNO KJ7 smoke, independent review, audit > 9.0.

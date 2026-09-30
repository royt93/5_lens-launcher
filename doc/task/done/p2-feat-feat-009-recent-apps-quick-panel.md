# FEAT-009 — Recently used apps quick panel

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Recent apps |
| Estimate | 5 SP (delivered scope grew per owner requests during brainstorming — two entry points, context menu, settings toggle — see design spec) |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Owner-approved 4-story delivery loop item, picked after FISH-015 (`doc/task/README.md`).
`SearchHistoryStore` already tracked the 8 most-recent launches for the search overlay's
`recentHeader`, but there was no one-tap path from the home screen straight to "apps I just
used" without opening search first.

## User story

As a user, I want to reopen a recently-used app in one tap from the home screen, without typing
into search first.

## What shipped

Full `brainstorming` → `writing-plans` → `executing-plans` pipeline —
`docs/superpowers/specs/2026-09-30-feat-009-recent-apps-quick-panel-design.md` /
`docs/superpowers/plans/2026-09-30-feat-009-recent-apps-quick-panel.md`.

- `SearchHistoryStore.MAX_RECENT` raised 8 → 16 (shared by the search overlay's `recentHeader`
  and the new panel — one source of truth), plus a new `removeKey(componentKey)` for single-entry
  removal.
- `RecentAppsPanelResolver` (new, pure, `search/`) maps stored component keys back to live `App`
  objects against the current `RAppsSingleton` snapshot, silently dropping keys for
  uninstalled/updated apps.
- `RecentAppsPanelFragment` (new `BottomSheetDialogFragment`) — reuses `SearchResultAdapter`
  as-is (icon loading, shortcuts, `DiffUtil`, long-press menu) via one new optional constructor
  callback (`onRemoveFromRecent`) instead of writing a new adapter.
- Two entry points: a new icon on the always-visible home `SearchBar` (toggleable in Settings,
  `KEY_RECENT_APPS_QUICK_PANEL_ENABLED`, default on) and a new item in the existing
  lens-management long-press menu (always present, untoggleable).
- Long-press a row → App info / Remove from recent only (not the search overlay's
  Pin/Unpin/Uninstall — see root-caused fix below).
- Recent list stays global across all lenses (`FISH-008`), matching `App.openCount`'s existing
  "by design" scope — proven at runtime by a dedicated cross-lens integration test, not just by
  key-naming convention.

## Root-caused bug found during manual device smoke (post-implementation)

Manual smoke on TECNO KJ7 caught a real spec mismatch Espresso/unit tests couldn't: the panel's
long-press menu carried over the search overlay's full 5-item set (Pin start/Pin end/Unpin/
Uninstall + App info) instead of the agreed 2 (App info + Remove from recent), because
`showActionMenu()` reused `menu_search_result.xml` unconditionally. `superpowers:systematic-debugging`
traced the root cause to the blanket adapter-reuse decision from earlier in the same task, and
fixed it by extending the exact conditional-visibility pattern the file already used for
`menuItemUnpin` (gated on the same `onRemoveFromRecent != null` flag that already distinguishes
the two call sites). 2 new tests pin the exact visible set for each context (search overlay
unchanged, panel exactly 2 items); this is why manual on-device verification is required, not
optional, even when every automated test layer is green.

## Test evidence

630/630 unit tests, 396/396 instrumented tests (widget + integration), 0 failures, on TECNO KJ7
(`115333744A005844`, Android 14). Lint 0 errors / 8 warnings (unchanged). A full-suite rerun
also caught and fixed a `TEST-003`-class flake in the new cross-lens integration test itself
(`RApplication`'s real background scan overwriting the fake single-item snapshot between setup
and consumption) — fixed with the same re-set-immediately-before-consumption pattern this
codebase already established for that bug class; 3x clean reruns in isolation plus one clean
full-suite rerun confirm it.

Live-verified end-to-end on TECNO KJ7: SearchBar icon → panel opens → real recent apps shown in
MRU order → tap launches and dismisses → long-press shows exactly App info + Remove from recent
→ Remove updates the list live without closing the sheet → Settings toggle hides/shows the icon
→ lens-menu entry still opens the panel with the icon hidden → an app launched while a second
lens is active shows up in the panel opened from the first lens (global scope confirmed live,
not just by test).

Self-audited **9.5/10** (one full point held back for the mid-flight context-menu spec mismatch
and the two build/install ordering slips during implementation — both caught before commit, none
shipped, but real process friction worth remembering for the next story).

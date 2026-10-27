# FISH-038 — Move an app to another lens (design)

Date: 2026-10-27. Story: `doc/task/todo/p3-fish-fish-038-drag-apps-between-lenses.md`. Device: TECNO KJ7 `115333744A005844` only.

## Scope decision (owner, 2026-10-27)

The story is 8 SP, High risk and depends on ARCH-002 (not done). The owner chose to ship **a "Move to lens…" menu action** now. Real drag-and-drop is **out of scope** and becomes a follow-up story (`FISH-038b`) after ARCH-002. The story's "alternative a11y path" criterion is met by this menu. FISH-038 therefore closes as a partial delivery; the drag acceptance criteria ("clear drag feedback", "does not break pan/pinch/edge-swipe") stay open in `FISH-038b`.

## Evidence check (code, 2026-10-27)

- A lens is a display scope, not a copy: per-lens `LensAppScope` (`ALL`/`SELECTED`) + selection set + hidden set, all in SharedPreferences keyed `lens_app_scope[_id]`, `lens_app_selection[_id]`, `lens_hidden_app_ids[_id]`. No Room change is needed. **Confirmed.**
- `LensAppScopeEditor.add/remove/apply` is the single writer for scope; `LensView.hideFromLens` writes the hidden set directly. **Confirmed.**
- `LensView.showQuickActionsMenu` (long-press) already offers Hide and Remove-from-lens using `menu_search_result`. A drag gesture would collide with pan/pinch/edge-swipe and ViewPager2. **Confirmed.**
- Story evidence `FrmApps.kt:339` for `LensAppScopeEditor` use: correct (`applyBulkLensScope`). Lens list loads through `LensWorkspace.loadAll`.

## Decisions (owner)

1. Source lens in `ALL` scope: **hide the app from the source lens** (keeps the source `ALL`; reuses the hidden set, recoverable).
2. Undo: **yes**, single-level Snackbar Undo.

## Design

- **Pure `LensMovePlan`** (no Android types): input = app id, source lens state, target lens state (`scope`, `selection`, `hidden`); output = the new `scope`, `selection`, `hidden` for both lenses.
  - Target: remove id from `hidden`; if `SELECTED`, add id to `selection`; if `ALL`, leave scope and selection alone.
  - Source: if `SELECTED`, remove id from `selection` (an empty result becomes `ALL`, as `LensAppScopeEditor.apply` already does, never a blank lens); if `ALL`, add id to `hidden`.
  - Moving to the same lens, or an unknown target, is a no-op (`null`).
- **Menu:** new item `menuItemMoveToLens` in `menu_search_result.xml`, visible only from the lens grid when there are 2+ lenses. It opens a single-choice dialog of the other lenses; the grid never shows itself as a target.
- **Apply:** `LensMoveApplier` writes both lenses through `LensAppScopeEditor` / `saveLensHiddenAppIds`, then `AppEventManager.notifyLensScopeChanged` for both lens ids.
- **Undo:** snapshot of exactly the six keys (scope, selection, hidden of each lens) with `UtilSettings.snapshotKeys/restoreKeys` (UI-034). `LensView` is not an Activity, so it reports the move to a new `OnAppMovedToLensListener`; `ActHome` shows the Snackbar and owns the one pending snapshot, clearing it in `onDestroy`.
- **Strings:** menu title, dialog title, Snackbar text, Undo (reuse `reset_undo`) in `values` + 16 locale folders.
- **Out of scope:** drag-and-drop, moving several apps at once, moving folders/pinned zones, changing Room rows.

## Tests

- Pure JVM `LensMovePlanTest`: every source/target `ALL`/`SELECTED` combination, empty-selection safeguard, already-present in target, hidden in target is cleared, same-lens no-op, unknown target.
- Robolectric `LensMoveApplierTest`: both lenses written, six-key snapshot restores exactly (including absent keys), unrelated keys untouched.
- Widget on KJ7: open the long-press menu, choose a target lens, assert both lenses' app lists; Undo restores both; menu item hidden with a single lens.
- Mutation check per branch (`--rerun`, verify the mutation compiles).

## Limitations to record

No drag-and-drop yet (`FISH-038b`). Undo is lost when `ActHome` is destroyed. Moving from an `ALL` lens uses the hidden set, so "Restore hidden apps" on that lens will bring the app back there.

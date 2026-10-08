# FISH-021 — Per-lens app scope, freeze order, quick lens switch

## Context

Multi-lens workspaces (FISH-008) page between lenses, but every page binds the same `listApp` (`ActHome.bindLensView` and `onPageSelected` both call `view.setApps(listApp)`), so a "Work" lens shows the same apps as "Personal". Owner picked three directions on 2026-10-08 and asked for them in one delivery: per-lens app set, freezing app positions in a lens, and faster lens switching. Order inside the delivery: scope → pickers → freeze → quick switch, each task tested on its own.

## Scope

- Each lens has a scope: `ALL` (default) or `SELECTED`. A lens with no stored value is `ALL`, so existing installs and single-lens setups are unchanged.
- In `SELECTED`, the lens grid shows only the apps the user picked for it.
- Three entry points choose the apps: the Apps tab, a checklist dialog from the lens long-press menu, and removing an app from the grid itself.
- A lens can freeze its app positions.
- A lens can be reached by tapping the lens name, and by dynamic launcher shortcuts.
- Out of scope: search (stays over all apps), new gestures on the grid (would collide with pinch FISH-009 and pull-down search FISH-016), `LayoutBackup` export/import of the selection, Room schema changes.

## Design

### 1. Scope storage — `util/UtilSettings.kt`

Keys follow FISH-018's `_<lensId>` suffix, but **must not inherit**: `lensKey()` maps the `default` lens to the unsuffixed key, so a non-default lens falling back to it would copy the default lens's selection.

- `getLensAppScope(lensId): LensAppScope` — missing/invalid → `ALL`, for the `default` lens too (its keys are the unsuffixed ones, but they start unset).
- `saveLensAppScope(lensId, scope)`.
- `getLensAppSelection(lensId): Set<String>` — `StringSet` of `AppPersistent.generateIdentifier(pkg, name)`; returns a copy (never the live `SharedPreferences` set).
- `saveLensAppSelection(lensId, Set<String>)`.
- `duplicateLensSettings` copies scope, selection and frozen flag; `deleteLensSettings` removes all three keys.

New `util/LensAppScope.kt`: `enum class LensAppScope { ALL, SELECTED }` plus a pure `filter(apps, scope, selection)`. No Android types, so it is JVM-testable.

### 2. Applying the scope — `ActHome`

`ActHome` holds one `listApp` for all pages. Replace the two `view.setApps(listApp)` call sites with `view.setApps(LensAppScope.filter(listApp, scope(view.lensId), selection(view.lensId)))`, so a prefetched neighbour page also gets its own list. Search keeps using the unfiltered `listApp`. An app installed later appears only in `ALL` lenses; a selected app that was uninstalled simply stops matching.

### 3. Pickers

- **Apps tab (`FrmApps`)**: a lens selector for "editing lens" (defaults to the active lens, like `FrmLens`); multi-select actions gain "Add to lens / Remove from lens" using the existing `ActionMode`.
- **Lens menu dialog**: a checklist dialog with search, opened from the long-press menu, for the active lens.
- **From the grid**: an icon long-press menu item "Remove from this lens" (only when scope is `SELECTED`).
- All three call one `LensAppScopeEditor` (add/remove ids, switch scope), which writes `UtilSettings` and then `AppEventManager` notifies `ActHome` to refilter. Switching to `SELECTED` with an empty selection keeps the scope `ALL` and prompts the picker, so a lens can never become blank by accident.

### 4. Freeze positions

The sorter already orders by `pinnedZone`, `favorite`, then `orderNumber` (`orderNumber < 0` goes last), and `AppPersistent.setOrders` already writes `orderNumber` per `lensId` under a mutex with a revision guard.

- **Freeze**: capture the order the user currently sees (`LensView` post-Smart-Focus list, restricted to the lens's visible apps), write it with `setOrders`, set `lens_frozen_<lensId>`. While frozen, `LensView.applySmartFocusArrangement` skips arranging for that lens.
- **Unfreeze**: new DAO query `UPDATE APP_PERSISTENT SET ORDER_NUMBER = -1 WHERE LENS_ID = :lensId`, clear the flag, behind a confirm dialog because it also discards any manual drag order.
- New apps append at the end; pinned zones and favorites still sort ahead.
- Entry: lens long-press menu, next to Smart Focus.

### 5. Quick switch

- Tapping `tvLensName` opens a list of lenses; selecting one scrolls `lensPager`.
- Dynamic shortcuts via `ShortcutManagerCompat` for the first N lenses, where N = max per activity minus the 2 static shortcuts (read at runtime, never hardcoded). Each carries `EXTRA_TARGET_LENS_ID`. `ActHome` is `singleTask` and `onNewIntent` already exists, so the extra is consumed there, validated against `currentLenses`, and removed after use (same pattern as `EXTRA_AUTO_EXPORT_LENS`). Shortcuts are refreshed on add/rename/delete/reorder.

## Data flow

Pick apps → `LensAppScopeEditor` → `UtilSettings` → `AppEventManager` → `ActHome` refilter → `LensView.setApps`. Freeze → `setOrders` → `TaskSortApps`/`switchLens` reload → sorter. Shortcut tap → `onNewIntent` → validate id → `lensPager.setCurrentItem`.

## Edge cases

- Unknown/deleted `lensId` in a shortcut or stored value → ignored, falls back to active lens / `ALL`.
- Lens deleted → its scope, selection, frozen flag and shortcut are removed; a later lens with the same id starts clean.
- Duplicating a lens materializes the source's effective scope, selection and frozen flag.
- Selection stored by identifier, not position, so it survives sorting and app updates.
- Large selections (hundreds of apps) are written as one `StringSet` per change; acceptable at this size, revisit if it shows up in a profile.
- Freeze while a scoped lens hides apps: only visible apps get an `orderNumber`; hidden ones keep theirs.

## Risks to verify before coding

1. Dynamic shortcuts attach to the app's launcher activity (`ActSettings`, `LAUNCHER`), while the target is `ActHome` (`HOME`). Confirm on the OPPO that the shortcut shows and routes.
2. `onPageSelected` currently sets the unfiltered `listApp` before `switchLens` reloads; confirm a prefetched neighbour does not flash the wrong list once filtering is added.
3. `setOrders` and `AppPersistent.defaults()` create rows with `appVisible = true`; this design does not use `appVisible` for scope for that reason.

## Tests

- **Unit (JVM)**: `LensAppScope.filter` (ALL, SELECTED, empty selection, stale ids); `UtilSettings` scope/selection/frozen get/save/duplicate/delete, default-lens key isolation, no inheritance by non-default lenses, copy-not-live-set; `LensAppScopeEditor` (never leaves a blank lens); unfreeze DAO query (Robolectric/in-memory Room).
- **Widget/UI (instrumented)**: lens shows only selected apps; swiping between an `ALL` and a `SELECTED` lens; picker dialog search and tick; "Remove from this lens"; freeze keeps order across a Smart Focus toggle and an app-list refresh; name tap switches lens; rotation keeps scope.
- **Integration (instrumented)**: scope survives process death; dynamic shortcut tap routes to the right page via `onNewIntent`; a new app install does not enter a `SELECTED` lens.
- **Device**: OPPO CPH1989 `FUJZIFIR7DQCNRWW` only. Full suite must stay green; a failing test needs a baseline comparison before it is called pre-existing.
- Named constants for all scope/key/limit values, no `late`/`!!`, no leaked listeners or dialogs (`onDestroy` dismisses the checklist dialog and the lens list).

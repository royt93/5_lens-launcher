# UI-034 — Reset preview and undo (design)

Date: 2026-10-27. Story: `doc/task/todo/p3-ui-ui-034-reset-undo-preview.md`. Device: TECNO KJ7 `115333744A005844` only.

## Evidence check (code, 2026-10-27)

- Menu `menuItemResetDefaultSettings` -> `SettingsMenuDispatcher` -> `ActSettings.resetTabDefaults(tab)` -> `onDefaultsReset()` of the open tab -> Snackbar `snackbar_reset_successful`. No confirmation, no undo: confirmed.
- Lens tab resets 5 values (`FrmLens.resetToDefault`), Settings tab about 29 (`FrmSettings.resetToDefault`), Apps tab only the sort type (`FrmApps.onDefaultsReset`).
- Per-lens keys carry a `_<lensId>` suffix (icon size, distortion, smart focus bias).
- Reset has side effects beyond prefs: `AppEventManager.notifyAppsUpdated`, `sendNightModeBroadcast`, `iconPackManager.invalidate` (inside `saveIconPackPackageName`), `sendEditAppsBroadcast`. Undo must re-run them.

## Decisions (owner, 2026-10-27)

1. The dialog lists only entries whose current value differs from the default (`label: current -> default`). If nothing differs it says "already default" and resets nothing.
2. Undo snapshots exactly the keys the reset touches, remembering absent keys so undo removes them again. It never snapshots the whole SharedPreferences file.
3. Snapshot lives in memory in `ActSettings`, single level. Rotation or leaving the Activity loses it (accepted by the story).

## Design

- `ResetEntry(prefKey, label, defaultValue)` list per tab is the single source for both the reset and the dialog, so they cannot drift. A test asserts every key `resetToDefault` writes is in the list.
- Pure `ResetPlan.build(current: Map<String, Any?>, entries)` returns the changed rows plus a `PrefsSnapshot(keys -> value or ABSENT)`. No Android types, JVM-testable.
- `UtilSettings` gets `snapshotKeys(keys)` and `restoreKeys(snapshot): Boolean` (synchronous `commit()`, like `restoreWorkspacePreferences`).
- Flow: menu -> dialog (Reset / Cancel) -> apply defaults + side effects -> Snackbar with **Undo** -> undo restores the snapshot, re-runs side effects, calls `assignValues()`.
- Labels reuse existing row-title strings. New strings only: dialog title/body, Undo, "already default". Translated in all 21 `values-*` folders.
- Out of scope: surviving rotation, multi-level undo, resetting other tabs' data.

## Tests

- Pure JVM: only changed rows listed, nothing-changed case, absent key remembered, per-lens suffix key.
- Robolectric: reset then undo restores every old value including absent keys; unrelated keys (entitlement, search history, language) untouched.
- Drift test: keys written by each `resetToDefault` equal the entry list.
- Widget on KJ7: dialog shows changed rows, Cancel changes nothing, Reset then Undo restores values.
- Mutation check per branch (`--rerun`).

## Limitations to record

Undo is lost on rotation or Activity exit. Apps tab dialog has one row (sort type).

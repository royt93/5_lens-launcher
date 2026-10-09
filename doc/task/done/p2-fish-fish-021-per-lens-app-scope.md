# FISH-021 — Per-lens app scope, freeze order, quick lens switch

| Field | Value |
|---|---|
| Type | feature |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Risk | Medium |
| Estimate | 13 |
| Dependencies | FISH-008 (multi-lens workspaces), FISH-018 (per-lens settings pattern) |

Spec: `docs/superpowers/specs/2026-10-08-lens-app-scope-design.md`. Plan: `docs/superpowers/plans/2026-10-08-lens-app-scope.md`.

---

## Owner decision (2026-10-08)

Three directions picked together and delivered in one batch: a per-lens app set, freezing app positions in a lens, and faster lens switching. Per-lens app set offers both modes (every app / chosen apps) and three ways to choose; storage stays in `SharedPreferences` (no Room migration).

## Acceptance criteria

- [x] A lens has a scope, `ALL` (default, existing lenses unchanged) or `SELECTED`; keys are `lens_app_scope_<lensId>`, `lens_app_selection_<lensId>`, `lens_frozen_<lensId>` and never fall back to another lens's key (`UtilSettingsLensScopeTest`).
- [x] The lens grid shows only the chosen apps; search still covers every app.
- [x] Three ways to choose apps, all through one writer (`LensAppScopeEditor`) that cannot leave a lens blank: the lens-menu checklist, the Apps tab "Add to lens / Remove from lens", and "Remove from this lens" on the grid.
- [x] Freeze / unfreeze app positions per lens; unfreezing asks first because it also discards any drag order.
- [x] Tapping the lens name opens a lens switcher; dynamic launcher shortcuts open a lens directly (count limited to the per-activity maximum minus the 2 static shortcuts; usage reported).
- [x] Duplicating a lens copies its scope, selection and frozen flag; deleting one removes them.
- [x] New strings translated in all 16 locales (`AllStringsTranslationTest`).
- [x] Unit, widget and integration tests for every case below; lint; full instrumentation; device smoke.
- [x] Audit > 9.0 — **9.1/10, a narrow pass** (was 8.9 before the follow-up tests), see `doc/task/AUDIT_ROUND_2026-10-09.md`; the weakest dimension (device smoke) did not improve.

## Bugs found while building it

- **App checklist dropped apps on long lists (found by device smoke, not by tests)**: the dialog read each row's tick back from its `ListView`, which only knows rows already bound. With 67 apps, unticking 7 rows and pressing OK saved 2 apps instead of 60. Fixed by keeping the choice in a `Set` updated by the dialog's own click callback. The earlier dialog tests used 2 apps and could not see it; `ActHomeLensAppsDialogLongListTest` (60 apps) was red before the fix and is green after.
- Commit `02e3f91` shipped its tests without the `ActHome` code (lost while restoring the file for a baseline comparison), so HEAD did not compile its own test until `51c39c6`.
- Test-isolation leaks, all in this feature's tests or neighbours they exposed: `ActHomeLensManagementWidgetTest` left the default lens renamed; `ActHomeMultiLensWidgetTest` left `active_lens_id` pointing at a deleted lens; `ActHomeLensFreezeWidgetTest.clearRows` cleaned a lens that never has data; `switchLens()` commits the cached real PackageManager snapshot over seeded apps.

## Verification

- JVM: 770/770.
- Instrumented, full suite on TECNO KJ7: 558/558 (run 4, after the last test change). Earlier runs on the same device: 547 (2 red, root-caused above), 549/549, 552/552.
- Lint: 0 errors, 7 warnings (5x `IconLauncherShape`, `IconLocation`, `IconMissingDensityFolder`, all pre-existing launcher-asset findings).

| Layer | Where |
|---|---|
| Unit | `LensAppScopeTest` (8), `LensAppScopeEditorTest` (11), `UtilSettingsLensScopeTest` (9), `AppEventManagerTest`, `LensViewRemoveFromLensTest` (3), `LensViewFreezeTest` (4), `LensShortcutsTest` (8) |
| Widget | `ActHomeLensScopeWidgetTest` (7), `ActHomeLensAppsDialogWidgetTest` (3), `ActHomeLensAppsDialogLongListTest` (3), `ActHomeLensFreezeWidgetTest` (8), `ActHomeQuickLensSwitchWidgetTest` (15), `SearchResultAdapterWidgetTest` |
| Integration | `FrmAppsLensScopeIntegrationTest`, `LensViewRemoveFromLensIntegrationTest` (6), `AppPersistentClearOrderTest` (3), `LensRemoveFromLensAccessibilityIntegrationTest` (4) |

## Device smoke (TECNO KJ7, 2026-10-09, fresh install data)

Screenshots read, not assumed:
- 10-entry lens menu shows "Choose apps for this lens" and "Freeze app positions".
- Checklist with 67 apps, 7 unticked, OK: the lens saved 61 apps (the 60 untouched plus the one left ticked) and the grid drew all 61. This is the exact action that lost apps before the fix.
- Freeze: `lens_frozen=true`, the DB held 61 rows with order numbers 0..60; the order and the grid were identical after force-stop and relaunch.
- Add lens, tap the lens name: "Switch lens" lists both lenses with the current one selected; picking "Lens 1" paged back and set `active_lens_id=default`.
- Landscape rotation: same 61 apps, same order, `SELECTED` and frozen kept.
- Shortcuts: `dumpsys shortcut` lists `lens_default` and the second lens's shortcut next to the static ones.

## Not covered / known limits

- **Shortcuts on the launcher UI**: registered with the system, but the TECNO launcher (`com.transsion.hilauncher`) menu for this app could not be opened by script, so they were not seen on screen.
- Freezing a lens filtered to `SELECTED` stores order numbers only for the visible apps (by design; hidden ones keep theirs); covered by `ActHomeLensFreezeWidgetTest`. The TalkBack path for "Remove from this lens" is covered by `LensRemoveFromLensAccessibilityIntegrationTest`.
- The choice is not in `LayoutBackup`, so it is not exported/imported (out of scope).
- Only one device (API 34) and one run of the smoke; no second-device confirmation.

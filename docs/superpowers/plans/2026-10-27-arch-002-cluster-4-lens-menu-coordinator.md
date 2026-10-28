# ARCH-002 cluster 4 Implementation Plan (LensMenuCoordinator)

> **For agentic workers:** Use superpowers:executing-plans inline (owner chose inline execution for all clusters).

**Goal:** Move the lens management menu, its anchor, every lens dialog, freeze, Smart Focus toggle and lens image export out of `ActHome` (HEAD `de6bf8a2`, 1,550 lines) with no behavior change, taking `ActHome` below 1,500 lines.

**Architecture:** Java `ui/home/LensMenuCoordinator` plus nested `Host`, same shape as clusters 1-3. Four fields are read by name from tests, so their **storage stays on `ActHome`** and the coordinator reads/writes them through the host (same rule as `lensViews`/`lensTabMediator`): `lensManagementMenu`, `lensDialog`, `lensShareLauncher`, `lensManagementLaunchIntentForTest`. `lensMenuPressAnchor` is only reached through `getLensMenuAnchorForTest()`, so it moves and `ActHome` keeps that accessor as a delegate.

**Tech Stack:** Java, Material dialogs, AndroidX PopupMenu, JUnit4, AndroidJUnitRunner.

## Constraints

- Pure move: no string, layout, id, preference or Room change; dialog order, reveal animation and menu ids unchanged. `MENU_ID_*` constants stay on `ActHome` with the same values (tests use them).
- These keep name, signature and visibility on `ActHome` and delegate: `onLensMenuItemSelected`, `lensSmartFocusMenuLabelRes`, `lensFreezeMenuLabelRes`, `openLensManagement()`, `showLensAppsDialog`, `exportActiveLensImage`, `getLensMenuAnchorForTest`, `lensMenuAnchor`, the four storage fields above. Tests read them: `lensManagementMenu` 14 lines, `lensDialog` 29, `lensShareLauncher` 8, `onLensMenuItemSelected` 32, `showLensAppsDialog` 4, `exportActiveLensImage` 3.
- `lensViews` is read through `host.activeLensView()` (cluster 1 owner). `listApp` through the host. No second writer of `lensViews`.
- Device: Pixel 7 Pro `2B051FDH3006MU` only (owner request). Never KJ7, never `connected*`. Backup `/tmp/b-arch002-pixel/before.tar` (665,600 bytes, taken 2026-10-28), restore and compare member hashes at the end.
- **Thermal rule (found in cluster 3):** `LensManagementActivityWidgetTest` fails when the Pixel thermal status is >= 2 (app disables motion). Before running device classes wait for `dumpsys thermalservice` status <= 1, and re-run any thermal-looking failure on the baseline APK at the same temperature before blaming the code.
- Run `./gradlew testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, then `lintDevDebug` separately. Mutations must compile; force `--rerun-tasks` after mutating. Leave the deleted `.idea` file untouched.

## Task 1 - characterize pure decisions

Extract to static methods on `LensMenuCoordinator` and test on the JVM first (red because the class does not exist):

- `enum MenuAction { CREATE, RENAME, DELETE, SMART_FOCUS, SHARE, RECENT, SEARCH, CLEAN, LENS_APPS, FREEZE, HIDDEN, GROUP, NONE }` and `static MenuAction routeMenuItem(int itemId)`: 1 CREATE, 2 RENAME, 3 DELETE, 4 SMART_FOCUS, 5 SHARE, 6 RECENT, 7 SEARCH, 8 CLEAN, 9 LENS_APPS, 10 FREEZE, 12 HIDDEN, 11 GROUP, anything else NONE. Test every id 0..13 and negatives, and that the values equal `ActHome.MENU_ID_*`.
- `static int smartFocusLabelRes(boolean on)` -> `R.string.lens_smart_focus_disable` / `lens_smart_focus_enable`; `static int freezeLabelRes(boolean frozen)` -> `lens_unfreeze_positions` / `lens_freeze_positions`.
- `static boolean containsLens(List<LensWorkspace>, String)`.
- `static boolean deleteEnabled(int position, int size, String id)` = `LensViewHolder.isValidPosition(position,size) && LensWorkspace.canDelete(id,size)`.

Mutation-check each branch (compile-checked). Then make `ActHome` call them before moving anything and run `ActHomeLensManagementWidgetTest`, `ActHomeLensFreezeWidgetTest`, `ActHomeLensDialogLifecycleWidgetTest` on the Pixel.

## Task 2 - move the block

Move verbatim, redirecting `this`/fields only: `lensMenuAnchor`, `anchorAtPressedPoint`, `dismissLensManagementMenu`, `removeLensMenuPressAnchor`, `addLensMenuItem`, `showLensManagementMenu`, the body of `onLensMenuItemSelected`, `toggleCleanLensMode`, `openLensManagement` (both), `toggleFreezeForLens`, `reloadLensAfterOrderChange`, `toggleSmartFocusForLens`, `exportActiveLensImage`, `clearPendingAutoExportLensAndNotifyFailure`, `createLensDialog`, `renameLensDialog`, `LensNameCallback`, `dismissLensDialog`, `showLensAppsDialog`, `showHiddenLensAppsDialog`, `showLensNameDialog`, `confirmDeleteLensDialog`, `lensMenuPressAnchor`.

Host (kept to what the block calls back into): `activity()`, `settings()` (ActHome `utilSettings`), `apps()` (`listApp`), `lenses()` (cluster 1 holder), `activeLensView()`, `getMenu()/setMenu(PopupMenu)`, `getDialog()/setDialog(AlertDialog)`, `shareLauncher()`, `setLaunchIntent(Intent)`, `showRecentAppsPanel()`, `openSearch()`, `refreshSearchBarVisibility()`, `invalidateActiveLens()`.

`ActHome` keeps one-line delegates for every name in Constraints and calls `lensMenu.onDestroy()` in `onDestroy` replacing the dialog/menu/anchor teardown (dismiss dialog, dismiss and null menu, remove anchor, in that order).

## Task 3 - gate, device, commit

- [ ] unit + build + lint green, `git diff --check`, secrets scan.
- [ ] Wait for thermal <= 1, install, run all 36 `ActHome*` classes plus `MoveAppToLensWidgetTest`, `LensManagementActivityWidgetTest`, `FrmLensWidgetTest`, `LauncherIntegrationTest`, `com.mckimquyen.util.PolaroidExportHelperWidgetTest`, `ActHomeSearchControllerLifecycleWidgetTest` (recreate `/tmp/run_acthome_pixel.py` first; `/tmp` was cleared overnight).
- [ ] Restore app data; compare members; locale `[]`, font `1.0`, Contacts denied.
- [ ] Commit cluster only (no `.idea`), record before/after lines. Do not push until cluster 5 decision and audit.

## Self-review

Every moved member has an owner; the four test-visible fields keep storage on ActHome; pure decisions get tests before the move; thermal rule written down; no new dependency or resource.

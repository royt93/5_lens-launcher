# ARCH-002 — Split ActHome Implementation Plan (master plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Plan structure (owner decision, 2026-10-27):** this is the **master plan**: gates, order, and the contract every cluster follows. Each cluster gets its **own detailed plan**, written immediately before that cluster is executed, from the `ActHome.java` that exists at that moment (line numbers and member names shift after every extraction, so a plan written for all five up front would be wrong by cluster 2). The cluster plans live at `docs/superpowers/plans/2026-10-27-arch-002-cluster-<n>-<name>.md` and each must be committed before its code.

**Goal:** Reduce `ui/ActHome.java` (2.454 lines) below 1.500 lines by moving five clusters into focused collaborators with no behavior change, giving `lensViews` a single owner, so FISH-038b can be built on a small `ActHome`.

**Architecture:** Same shape as ARCH-001 (`ui/settings`): each collaborator lives in `com.mckimquyen.ui.home`, receives what it needs through a small host interface that `ActHome` implements, owns its own state and exposes `onDestroy()`. `ActHome` keeps lifecycle, view wiring and thin delegates for every member that tests reference by name.

**Tech Stack:** Java (ActHome) + Kotlin (collaborators), AndroidX, Material 3, JUnit4, Robolectric, AndroidJUnitRunner on the locked device.

## Cập nhật thiết bị (2026-10-27)

Owner yêu cầu: "dùng pixel thay thế đi". Từ thời điểm này, khóa **Pixel 7 Pro `2B051FDH3006MU`** cho phần còn lại của phiên; mọi hướng dẫn KJ7 bên dưới là lịch sử, phải thay bằng Pixel khi thực thi. Pixel Android 17/API 37; backup riêng `/tmp/b-arch002-pixel/before.tar` (665.600 byte) và `before.apk`, trạng thái locale `[]`, font `1.0`, Contacts denied. Chạy lại baseline 35 lớp trên APK HEAD trước refactor để tách khác biệt thiết bị khỏi hồi quy. KJ7 đã ngắt kết nối trước khôi phục: backup `/tmp/b-arch002/state/before.tar` còn nguyên, chưa khôi phục; không tự gửi lệnh tới KJ7 sau khi đổi khóa.

## Global Constraints

- **Pure refactor.** No visible change, no new feature, no string/layout/Room/prefs change, no renamed test-visible member.
- Members that tests reference (`onLensMenuItemSelected`, `refreshLensList`, `updateLensNavigationChrome`, `openSearchFromHome`, `updateModeVisibility`, `refreshLensScope`, `showPinchCurvatureSnackbar`, `showLensAppsDialog`, `exportActiveLensImage`, `handleTargetLensIntent`, `lensViews`, `progressBarHome`, `lensShareLauncher`, `lensManagementMenu`, `lensDialog`, `MENU_ID_*`, the `*ForTest` accessors, `reportFullyDrawnCallCount`) **keep their name, signature and visibility**; they delegate. Before moving anything, regenerate the authoritative list with `grep -rhoE "activity\.[a-zA-Z]+\(|it\.[a-zA-Z]+\(|[a-zA-Z]+ForTest" app/src/androidTest app/src/test` and keep every name that appears.
- **One cluster = one commit** (plus its detailed plan commit). Each commit builds and passes `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`.
- **Regression gate after every cluster:** run all 35 `ActHome*` androidTest classes (145 `@Test`) on TECNO KJ7 `115333744A005844`, one class at a time with `timeout`, using `/tmp/run_acthome.sh` (recreate it from the listing below if `/tmp` was cleared). Zero failures allowed; a red class stops the work and is fixed before the next cluster. Also run the non-prefixed classes that touch the moved code (listed in each cluster plan).
- Never run a Gradle `connected*` task; only `adb -s 115333744A005844`. The screen must be awake and unlocked (`dumpsys power` `mWakefulness=Awake`, `dumpsys window` `isKeyguardShowing=false`); if not, ask the user to unlock and wait for "done".
- Back up `databases/` + `shared_prefs/` before the first install of each round (tar must be non-empty) and restore + `diff -rq` at the end of the round; verify locale and font scale unchanged.
- If a screenshot shows an ad covering the UI, stop and wait for the user's `done`.
- Verify every mutation compiles before counting it, and read command output fully; do not chain many commands whose output is hidden by one early failure. After a mutation run, force a clean rebuild (`--rerun-tasks`) before installing on the device.
- Do not stage `.idea/` or unrelated files.

## Regression runner

```bash
ls app/src/androidTest/java/com/mckimquyen/ui/ActHome*.kt | sed 's#.*/##; s#\.kt##; s#^#com.mckimquyen.ui.#' > /tmp/acthome_classes.txt
cat > /tmp/run_acthome.sh <<'EOS'
#!/bin/bash
K=115333744A005844; pass=0; fail=0; failed=""
while read -r c; do
  out=$(timeout 420 adb -s $K shell am instrument --user 0 -w -e class "$c" com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner </dev/null 2>&1 | grep -E "^OK \(|^FAILURES|^Tests run" | head -2 | tr '\n' ' ')
  echo "$c :: $out"
  case "$out" in OK*) pass=$((pass+1));; *) fail=$((fail+1)); failed="$failed $c";; esac
done < /tmp/acthome_classes.txt
echo "TOTAL classes ok=$pass failed=$fail $failed"
EOS
chmod +x /tmp/run_acthome.sh
```

`</dev/null` is required: without it `adb shell` swallows the loop's stdin and only the first class runs (found in Task 0). Expected: `TOTAL classes ok=35 failed=0`; if the total is not 35, the run is invalid. A single class can take over a minute; the run is long, so use a background Bash call and read the output file, not a fixed sleep.

---

## Task 0: Baseline (before touching ActHome)

**Files:** none changed; creates `/tmp/b-arch002/state/before.tar` and `doc/task/inprogress/p3-arch-arch-002-split-act-home-god-class.md` (moved, status updated).

- [ ] **Step 1: Move the story** `doc/task/todo/p3-arch-arch-002-split-act-home-god-class.md` → `doc/task/inprogress/`, set `Status | inprogress`. Record in it that the story's line numbers are stale (ActHome is 2.454 lines) and that `CORE-004` and `UI-028` are done.
- [ ] **Step 2: Baseline measurements.** Record `wc -l ActHome.java`, field count (`grep -cE "^    (private|public|protected|static|final)[^(]*;"`), `@VisibleForTesting` count, `lensViews` references, and the unit test total from the last gate (1.257).
- [ ] **Step 3: Device baseline.** Confirm the screen is awake and unlocked, back up app data, install the current `devDebug` and `devDebugAndroidTest`, then run `/tmp/run_acthome.sh` on the **unchanged** code. Expected `ok=35 failed=0`. Any class that is red or flaky here is recorded as a pre-existing baseline problem in the story **before** any refactor; it must not be blamed on a cluster later. If a class is flaky, run it three times and record the pass rate.
- [ ] **Step 4: Commit** the story move: `git commit -m "chore(arch): start ARCH-002 split of ActHome"`.

## Cluster order and contract

Every cluster follows the same ten steps; the detailed cluster plan fills in real names and line ranges.

1. Read the exact block to move and every member it touches in `ActHome.java`; list fields it owns, fields it only reads, and calls back into `ActHome`.
2. Write the host interface (`HomeXHost`) with only what the cluster needs, named after the behavior, not after the fields.
3. **Characterization tests first** for every pure decision in the block that is not already pinned: extract it to a pure function in the new class, write tests that fail to compile, then pass against the extracted function, and confirm the old `ActHome` call site still gives the same result (run the existing `ActHome*` class that covers it).
4. Move the code verbatim (no rewrites, no cleanups) into the new class; `ActHome` implements the host interface.
5. Replace the block in `ActHome` with a delegate that keeps the original member name and signature.
6. Move owned fields into the collaborator; give it `onDestroy()` that nulls every view/callback/listener it registered, and call it from `ActHome.onDestroy()`.
7. Run `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`; fix until green.
8. Mutation-check the extracted pure logic only (each mutation must compile; restore after; force a clean rebuild before any device install).
9. Install on KJ7 and run all 35 `ActHome*` classes plus the cluster's extra classes. Zero failures.
10. Commit the cluster with its tests; record before/after `ActHome.java` line count in the commit body.

### Cluster 1 — `LensViewHolder` (single owner of `lensViews`)

Moves: `lensViews`, `lensPager`, `lensPagerAdapter`, `lensTabMediator`, `lensPageIndicator`, `tvLensName`, `currentLenses`, `lensPageChangeCallback`, `refreshLensList`, `bindLensView` (both overloads), `restorePendingPinchIfAny`, `maybeShowResurrectSnackbar`, `lensViewAt`, `onLensesChanged`, `updateLensNavigationChrome`, `pendingLensMoveUndo` and its Snackbar (FISH-038), and every `lensViews` read in the Activity (`ActHome` lines ~186, 224, 520, 691–710, 783–797, 1082, 1145–1167, 1179, 2125, 2214–2223, 2276, 2390). After this cluster `lensViews` has exactly one writer. Extra device classes to run: `MoveAppToLensWidgetTest`, `LensManagementActivityWidgetTest`, `FrmLensWidgetTest`, `LauncherIntegrationTest`. Keep `ActHome.lensViews` as a package-private read-through accessor so tests that read `activity.lensViews` still compile.

### Cluster 2 — `HomeQuickActionDelegate`

Moves: `REQUEST_CODE_CAMERA/WIFI_SSID/CONTACTS`, `quickActionRow`, `quickActionDivider`, `tvQuickActionLabel`, `tvQuickActionValue`, `flashlightOn`, `updateQuickAction`, `toggleFlashlight`, `performFlashlightToggle`, `requestWifiSsidPermission`, `openDndAccessSettings`, `performDndToggle`, the contact row fields, `contactSearchController`, `updateContactAction`, `renderContactSearchState`, `requestContactsPermission`, `startContactIntent`, `onRequestPermissionsResult` (ActHome keeps the override and delegates). Extra device classes: `DndQuickActionIntegrationTest`, `ScanQuickActionIntegrationTest`, `ContactSearchIntegrationTest`, `AppSearchWidgetTest`.

### Cluster 3 — `HomeSearchController` + `T9KeypadBinder`

Moves: `setupSearch`, `setupT9Keypad`, `prefillOnTap`, `updateSearchFilterChipChecked`, `updateSearchCustomization`, `isT9NumericQuery`, `updateSearchResults`, `updateWebSearchFallback` (both), `sortedByLabel`, `setSearchSystemBarsHarmonized`, `launchSearchResult`, `hideSearch`, `openSearchFromHome`, search view fields and `searchBackCallback`/predictive-back preview, `searchHistoryStore`, `searchResultAdapter`, `searchEnterDownHandled`, `searchLaunchCount` (keep `getSearchResultAdapterForTest`, `isSearchEnterDownHandledForTest`, `getSearchLaunchCountForTest` as delegates). Pure logic extracted and unit-tested: the results-state decision (`isEmptyQuery`, `showAllAppsFallback`, `hasResults`, which headers show), `isT9NumericQuery`, `sortedByLabel`, current-lens-id-for-position. Extra device classes: `AppSearchWidgetTest`, `SearchResultCountHeaderWidgetTest`, `PinyinSearchWidgetTest`, `RecentInstallSearchWidgetTest`, `AppSearchIntegrationTest`, `SearchHistoryStoreTest`.

### Cluster 4 — `LensMenuCoordinator`

Moves: `showLensManagementMenu`, `onLensMenuItemSelected`, anchors (`lensMenuAnchor`, `anchorAtPressedPoint`, `removeLensMenuPressAnchor`, `dismissLensManagementMenu`), `lensManagementMenu`, `lensDialog` and the dialog family (`createLensDialog`, `renameLensDialog`, `showLensAppsDialog`, `showHiddenLensAppsDialog`, `showLensNameDialog`, `confirmDeleteLensDialog`, `dismissLensDialog`), `toggleFreezeForLens`, `toggleSmartFocusForLens`, `toggleCleanLensMode`, `reloadLensAfterOrderChange`, `openLensManagement`, `exportActiveLensImage`, `lensShareLauncher`, the `MENU_ID_*` constants (keep as `static final` on `ActHome` with the same values, delegating). Extra device classes: `LensManagementActivityWidgetTest` and `com.mckimquyen.util.PolaroidExportHelperWidgetTest` (note its package is `util`, not `ui`); the lens-group tests already run inside the 35 `ActHome*` classes.

### Cluster 5 — insets/chrome + `HomeAppListController`

Moves: `applyHomeColumnInsets`, `onConfigurationChanged` body, `updateColor`, `setBackground`, `updateModeVisibility`, `updateSearchBarVisibility`, `updateRecentAppsPanelIconVisibility`, `setupTransparentSystemBarsForLollipop`, `checkA11ySuggestion`, `showPinchCurvatureSnackbar`; and `assignApps`, `scopedApps`, `isSameAppList`, `refreshLensScope`, `updateHiddenRecoveryButton`, `listApp`, `homeAppAdapter`, `rvHomeAppList`. Pure logic extracted and unit-tested: `isSameAppList`, scope filtering call, content-max-width margin selection (already in `UtilCalculator`). Extra device classes: `ActHomeMarginStabilityWidgetTest` (already in the 35), `MalformedColorPrefsWidgetTest`, `BaseActivityRefreshRateWidgetTest`, `RecentAppsPanelWidgetTest`.

## Final task: Gate, audit, story close

- [ ] Full gate once more: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun`; record unit totals, lint counts, `git diff --check`, `bash scripts/check-secrets.sh`.
- [ ] Final device pass: clean rebuild (`--rerun-tasks`), install, run `/tmp/run_acthome.sh` and the extra classes of all five clusters; restore app data byte for byte and verify `diff -rq`, locale and font scale.
- [ ] Measure and report: final `ActHome.java` line count (target < 1.500; report the real number, do not round up), `lensViews` writers (must be 1), per-collaborator line counts, unit tests added, and the 35-class device result.
- [ ] Audit `doc/task/AUDIT_ARCH-002_<date>.md`: confirm no visible change (diff of `res/` is empty), no test-visible member renamed, every collaborator has `onDestroy`, host interfaces are minimal, and list any pre-existing red class from Task 0. Score must be > 9.0 to push.
- [ ] Tick the story, move it to `done/`, update `doc/task/README.md` and `doc/feature.md` (one entry, exactly one `## 🟡 In progress` heading). If the 1.500-line target is not reached, say so in the story, keep ARCH-002 open as "partial" and write the remaining cluster as a new story instead of closing.
- [ ] Commit docs; push `origin/dev` only if the score is > 9.0. Never stage `.idea/`.
- [ ] Then start FISH-038b (brainstorm → spec → plan) against the smaller `ActHome`.

## Self-review

- Spec coverage: five clusters in the spec's order, characterization-first rule, 35-class device gate after each cluster, device restore, audit and story close are all present. The spec's insets cluster and `assignApps` block are merged into cluster 5 as in the spec.
- Per-cluster code blocks are intentionally **not** in this master plan: they are produced in each cluster plan from the then-current file. This is a deliberate, owner-approved structure, not a placeholder; the master plan never asks an implementer to guess names, it requires them to read the file and write the cluster plan first.
- Type and member names listed above were read from `ActHome.java` on 2026-10-27 (method index and field list); the cluster plans must re-verify them against the file they actually move.

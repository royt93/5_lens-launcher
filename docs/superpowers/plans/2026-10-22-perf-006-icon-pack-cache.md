# PERF-006 Icon-Pack Reload Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reuse icon-pack discovery/appfilter state across individual icon reloads and invalidate it on pack/package changes.

**Architecture:** `RApplication` owns one `UtilIconPackManager`; `UtilApp.loadSingleAppIcon` obtains the shared manager and requests `forceReload=false`. A single invalidation method clears the manager cache when the selected pack changes or active icon-pack package changes/removes; the next reload refreshes once. Keep full app-refresh behavior and no-pack/default icon fallback unchanged.

**Tech Stack:** Kotlin, Java, Android `Application`/`PackageManager`/broadcast receiver, JUnit4, Robolectric, Mockito, Android instrumentation, Gradle.

## Global Constraints

- One application-scope manager, not a per-reload manager and not a map of old managers.
- Single-icon reload uses `forceReload=false`; full refresh may keep its current force-reload semantics.
- Invalidate when selected icon pack changes and active icon-pack package updates/removes; next reload performs lazy discovery.
- Preserve behavior for no pack, failed package query, missing `appfilter.xml`, and no custom icon.
- Dependency CORE-005 is complete and pushed in `9f808a9a`; active icon pack resolves by persisted packageName, never display label.
- `TaskUpdateApps.loadSnapshot()` currently receives a legacy label argument, but `UtilApp.getAppShells()` already resolves through package identity after CORE-005; don't reintroduce label-based lookup.
- Locked device for remaining session: Pixel 7 Pro `2B051FDH3006MU`, Android 17. Only `adb -s 2B051FDH3006MU`; no `connected*` tasks and no other device commands.
- Keep branch `dev`; no worktree. Preserve user prefs/settings/DB/default launcher/locale; stop screenshot testing if ad covers UI, wait for owner confirmation.
- Final checks: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`, `git diff --check`, `scripts/check-secrets.sh`; never stage `.idea`.

---

## File Map

- Modify `app/src/main/java/com/mckimquyen/app/RApplication.java`: initialize one manager and expose stable application accessor; invalidate on exact selected pack change/package event.
- Modify `app/src/main/java/com/mckimquyen/util/UtilIconPackManager.java`: shared cache, explicit invalidation, and deterministic query-count seam if none exists.
- Modify `app/src/main/java/com/mckimquyen/util/UtilApp.kt`: single-icon reload uses the application-owned manager and `forceReload=false`.
- Modify exact icon-pack preference writer (identify during exploration); avoid invalidating on unrelated settings.
- Extend `app/src/test/java/com/mckimquyen/util/UtilIconPackManagerTest.kt` and `UtilAppIconReloadTest.kt` for reuse, invalidation, and fallback behavior.
- Add/extend receiver test only if existing test patterns cover real package action routing; otherwise test a small package/action predicate and record instrumentation applicability.
- Reuse `app/src/androidTest/java/com/mckimquyen/views/LensViewIconReloadWidgetTest.kt` for visible icon regression.
- Update story/backlog/feature and create audit at close.

### Task 1: RED — manager discovery cache contract

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/util/UtilIconPackManagerTest.kt`
- Read-only: `UtilAppIconReloadTest.kt`, `UtilIconPackManager.java`, `UtilApp.kt`, `RApplication.java`, `RApplicationSeamIntegrationTest.kt`

**Interfaces:**
- `getAvailableIconPacksWithIcons(forceReload: Boolean, application: Application): ArrayList<IconPack>` is existing API under test.
- `PackageManager.queryIntentActivities(Intent, flags)` is discovery query. `icon_pack_launchers` resource supplies action count.
- CORE-005 is complete: reload uses current package identity; this story tests manager reuse/invalidation only.

- [ ] **Step 1: Move story todo → inprogress and reconfirm evidence.** Search every `UtilIconPackManager()` construction and every `getAvailableIconPacksWithIcons` call. Confirm no active `KEY_ICON_PACK_LABEL_NAME` consumer remains outside migration/display.
- [ ] **Step 2: Inspect test seam.** Read full `UtilIconPackManagerTest`, `UtilAppIconReloadTest`, and Mockito config. Existing tests use Robolectric sdk 28. Determine whether `UtilIconPackManager.loadIconPacks()` can use Robolectric’s real PackageManager with a test package catalog; do not mock `Application.getPackageManager()` with an unavailable Mockito inline feature.
- [ ] **Step 3: Write RED against a testable single-icon reload helper.** Add a package-private/internal `UtilApp` helper accepting an explicit `UtilIconPackManager`, selected packageName, and `Application`, returning selected `IconPack?`; it must call `manager.getAvailableIconPacksWithIcons(false, application)` and select via CORE-005 package identity. First write a caller-contract test that expects exactly one query batch after calling the helper for five app-icon reloads. Against baseline, the helper is absent (`Unresolved reference`) or repeated calls query repeatedly; establish RED before wiring production ownership.
- [ ] **Step 4: Write invalidation test.** With same manager, invoke planned `invalidate()`, call helper once and assert exactly one additional query batch; call helper again and assert no increase.
- [ ] **Step 5: Run RED:** `./gradlew testDevDebugUnitTest --tests '*UtilIconPackManagerTest'`. Expected failure must be query-count mismatch or compile RED for missing `invalidate`, not test initialization failure.

### Task 2: GREEN — application-owned manager and reuse

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/app/RApplication.java`
- Modify: `app/src/main/java/com/mckimquyen/util/UtilIconPackManager.java`
- Modify: `app/src/main/java/com/mckimquyen/util/UtilApp.kt`
- Tests from Task 1.

**Interfaces:**
- `RApplication.iconPackManager: UtilIconPackManager` (Java getter `getIconPackManager()`) returns one process-lifetime manager.
- `UtilIconPackManager.invalidate()` resets discovery cache only; next `getAvailableIconPacksWithIcons(false, application)` refreshes once.
- `UtilApp.loadSingleAppIcon(Application, String, Int)` signature remains unchanged for `LensView` and `SearchResultAdapter` callers.

- [ ] **Step 1: Add failing ownership assertion** in `UtilAppIconReloadTest`: repeated calls with the same `RApplication` retrieve identical manager instance. Assert through public accessor, not reflection.
- [ ] **Step 2: Initialize a single manager field on `RApplication`** before `updateApps()` can begin; expose a getter. Do not create a companion/global singleton.
- [ ] **Step 3: Add explicit cache invalidation** to `UtilIconPackManager`; synchronize only state check/reset/load enough to prevent duplicate simultaneous scans. Do not lock through package query or XML IO unless test proves race otherwise; use local-loaded-list then synchronized publish if practical.
- [ ] **Step 4: Route only `loadSingleAppIcon` through `application.iconPackManager`** and pass `forceReload=false`; leave full initial app scan call sites untouched.
- [ ] **Step 5: Run focused unit tests GREEN** and assert one query batch for 5 reloads, a second batch after invalidation, then reuse again.
- [ ] **Step 6: Verify no-pack and no-match fallback**: use existing test cases or add assertions that the returned bitmap remains the default when no selected pack or no custom mapping exists. Expected same default image reference/pixel behavior as current code.

### Task 3: RED/GREEN — invalidate only on relevant changes

**Files:**
- Modify exact pack preference writer found in Task 1, likely `FrmSettings`/`UtilSettings` call site.
- Modify `RApplication.java` or `BroadcastReceivers.AppsUpdatedReceiver.java` only if package broadcast handling must be routed to cache invalidation.
- Tests: `UtilIconPackManagerTest.kt` plus the nearest existing preference/package receiver test.

**Interfaces:**
- A single helper on `RApplication` may expose `invalidateIconPackCache()`; it delegates to manager and is called only for active pack selection change or the currently selected package’s update/removal.
- A pure predicate test may be named `shouldInvalidateIconPackCache(action: String, changedPackage: String?, selectedPackage: String?): Boolean` only if current receiver lacks a testable decision seam; no helper otherwise.

- [ ] **Step 1: Locate active icon pack identity.** `KEY_ICON_PACK_LABEL_NAME` stores display label; inspect how `UtilIconPackManager` maps label to package, and identify reliable selected package identity before invalidating package updates. Do not infer package identity from label if app already exposes package ID.
- [ ] **Step 2: Write selection-change RED test.** Selecting pack A then B invalidates once; saving B again does not invalidate. Test at the existing setting-owner boundary.
- [ ] **Step 3: Write package-event RED tests.** `ACTION_PACKAGE_REPLACED` for selected package invalidates; final `ACTION_PACKAGE_REMOVED` for selected package invalidates; unrelated package event does not. For `ACTION_PACKAGE_REMOVED` with `EXTRA_REPLACING=true`, do not invalidate there; `ACTION_PACKAGE_REPLACED` covers completed update, preventing duplicate scan. If broadcasts are not testable in existing unit harness, create a focused pure decision test and one instrumentation test only if receiver integration is essential.
- [ ] **Step 4: Implement cache invalidation at the narrowest existing write/receiver sites.** Keep package receiver registration/unregistration unchanged. Do not invalidate for generic app edit/sort events.
- [ ] **Step 5: Run RED/GREEN tests** and assert after each event exact query count: one invalidation, one reload batch, then reuse. Verify same-pack save and unrelated-package event perform zero extra queries.

### Task 4: Pixel integration, profiling, and close

**Files:**
- Modify story, README, feature tracker.
- Create `doc/task/AUDIT_PERF-006_2026-10-22.md`.
- Existing visual test: `LensViewIconReloadWidgetTest`.

- [ ] **Step 1: Capture baseline on Pixel Ultra** using only `adb -s 2B051FDH3006MU`; record model/build/Android 17 and app state. Use deterministic query count as primary metric; elapsed time is secondary. Do not write package identity/token values into public docs.
- [ ] **Step 2: Run existing icon reload widget test and active-pack-switch integration** on Pixel via `adb -s 2B051FDH3006MU shell am instrument`. Do not run Gradle `connected*` tasks. If no visible UI changes, mark separate widget test `Not applicable`; run existing visual regression.
- [ ] **Step 3: Measure fixed workload** of `ICON_RELOAD_COUNT=5` sequential icon reloads with same app fixture on baseline and candidate. Record query batches and elapsed milliseconds; repeated trials should use the same package set and ensure bitmap-cache misses rather than cache hits. Do not claim speedup if timing distributions overlap.
- [ ] **Step 4: Run final full checks:** `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`; expected BUILD SUCCESSFUL, unit failures/errors zero, lint no new errors.
- [ ] **Step 5: Run `git diff --check` and `scripts/check-secrets.sh`;** review synchronization and package event lifecycle; ensure no new receiver leak or duplicate query race.
- [ ] **Step 6: Compare Pixel state** to baseline: prefs/settings/DB/default launcher/locale. Restore test-owned state; report SDK-owned changes only, never alter SDK token values. Ad overlay during screenshot testing means stop and wait for owner confirmation.
- [ ] **Step 7: Complete story and audit** with RED/GREEN, query count, timing, device model/OS/build/time, all test layers or explicit Not applicable, residual limitations, and score. Move story to done only after acceptance criteria pass and audit >9.0.
- [ ] **Step 8: Commit and push** only after final verification and audit gate; stage intended files only, never `.idea`; commit trailer `Co-Authored-By: Claude Code <noreply@anthropic.com>`. Push `origin dev`; on 403, report exact failure and stop without credential changes.

## Self-review

- Application-owned single manager and `forceReload=false`: Task 2.
- Invalidation on active pack selection and package update/removal, with replacing-removal deduplication: Task 3.
- Fallback behavior and query count ≤1 across N reloads: Tasks 1–2.
- Pixel measurement, unit/integration/widget applicability, state preservation, no `connected*`, R4, secret scan, audit gate, `.idea` exclusion: Task 4.
- No new dependency, historical manager map, or unrelated rendering refactor.

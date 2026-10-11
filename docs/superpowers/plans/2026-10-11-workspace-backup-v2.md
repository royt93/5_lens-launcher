# Multi-Lens Workspace Backup v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade layout backup to export and restore complete multi-lens workspaces, including per-lens app layout and FISH-021 scope/selection/frozen state, while retaining schema-v1 import support.

**Architecture:** Extend `LayoutBackup` with typed schema-v2 lens records and strict validation. Create a pure import plan, then apply DB replacement in one Room transaction while preserving non-layout app fields. Snapshot prior DB/preferences and compensate if SharedPreferences commit fails; process-death atomicity across stores remains impossible and must be documented/reconciled. Keep existing SAF I/O and Java callback UI patterns.

**Tech Stack:** Kotlin, Java, Android `org.json`, Room v12, SharedPreferences, coroutines, Android SAF, JUnit4/Robolectric, Android instrumentation tests.

## Global Constraints

- No ads, IAP, consent, signing, Play release, or unrelated feature work.
- Schema v1 imports remain supported and apply rows to `default`, leaving lens list and FISH-021 scope/selection/frozen preferences unchanged.
- Schema v2 import replaces complete workspace, restoring lens IDs unchanged after explicit confirmation.
- Preserve `openCount`, `paletteColor`, and user data outside layout.
- Preserve app rows and selection IDs for apps not currently installed.
- Reject invalid/unsupported input before writes; DB replacement is transactional and no success is reported on failure.
- Room and SharedPreferences cannot share a process-death transaction; snapshot both, commit DB then prefs, compensate on failure, and reconcile active lens at startup.
- Stable app identity: `AppPersistent.generateIdentifier(packageName, componentName)`.
- No new dependency; retain JSON + SAF.
- No `late`/force unwrap; validate imported data; dismiss dialogs/listeners with Activity lifecycle.
- Device smoke must use device locked for session; no unscoped `connected*` task if it fans out.

---

## File map

- Modify `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt`: v1/v2 DTOs, serializer/parser.
- Modify `app/src/main/java/com/mckimquyen/model/LensWorkspaceDao.kt` and `AppPersistentDao.kt`: transactional workspace/layout replacement.
- Modify `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`: snapshot/replace only per-lens app scope, selection, frozen and active-lens keys.
- Modify `app/src/main/java/com/mckimquyen/util/LayoutImportApplier.kt`: immutable v1/v2 import plans.
- Modify `app/src/main/java/com/mckimquyen/util/LayoutBackupIo.kt`: all-lens snapshot, persistence orchestration and compensation.
- Modify `app/src/main/java/com/mckimquyen/ui/ActSettings.java`: preview/confirm/cancel, result handling, lens/shortcut refresh.
- Tests: existing `LayoutBackupTest.kt`, `LensWorkspaceDaoTest.kt`, `UtilSettingsLensScopeTest.kt`, `LayoutImportApplierTest.kt`, `LayoutBackupIoIntegrationTest.kt`, `ActSettingsLayoutBackupWidgetTest.java`; add focused plan/integration tests where needed. Locate exact current names before editing.
- After all checks, update FEAT-006 story, `doc/feature.md`, `doc/task/README.md` with follow-up evidence; keep historical record intact.

## Task 1: Define schema-v2 model and strict parser — COMPLETE

Completed in commits `19b6a6f..f00b40b`; parser tests include v1 compatibility, v2 round-trip, malformed/missing/wrong-type fields, duplicate IDs/orders, invalid scope, strict entries and uninstalled selection retention. See `.superpowers/sdd/task-1-report.md`.

## Task 2: Build pure workspace import plan

**Files:**
- Modify `app/src/main/java/com/mckimquyen/util/LayoutImportApplier.kt`
- Create `app/src/test/java/com/mckimquyen/util/LayoutWorkspaceImportPlanTest.kt`

**Interfaces:**
- `LayoutImportApplier.WorkspacePlan` contains validated schema version and immutable lens/app layout records.
- `planV1(backup)` targets `LensWorkspace.DEFAULT_LENS_ID` only.
- `planV2(backup)` retains all lens IDs/order/settings/app entries including absent-package identities.
- `previewCounts(plan)` returns deterministic lens count and total/per-lens entry counts.

- [ ] Step 1: Write tests for v1 default target; v2 two-lens/settings/entries preservation; absent app retention; deterministic preview counts; parse failures cannot produce plan.
- [ ] Step 2: Run `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LayoutWorkspaceImportPlanTest`; confirm RED.
- [ ] Step 3: Implement pure immutable mapping; no Android Context, PackageManager, Room, SharedPreferences or I/O.
- [ ] Step 4: Run focused class; expect all PASS.
- [ ] Step 5: Commit `feat(backup): plan full workspace restore`.

## Task 3: Add atomic Room workspace/layout replacement — COMPLETE

**Files:** `AppPersistentDao.kt`, `LensWorkspaceDao.kt`; tests in `LensWorkspaceDaoTest.kt`.

**Interface:** one `@Transaction` replacing all workspace rows and all per-lens app rows from validated records; preserve non-layout fields by stable identifier before deletion.

- [x] Step 1: Add in-memory Room tests seeded with two lenses, stale rows, open counts, palette colors, notification counts; assert replace semantics and preserved non-layout values.
- [x] Step 2: Run focused DAO test; confirm RED.
- [x] Step 3: Implement transaction: snapshot old rows; replace workspace/app rows; insert imported layout with original IDs and backed-up fields; preserve `openCount`, `paletteColor`, `notificationCount` and fields outside backup according to their existing global/per-lens semantics. For identities absent from backup, keep one default-lens parked row with layout reset and `appVisible=false`; do not make them visible. Any exception rolls back.
- [x] Step 4: Test v2 replacement, hidden parking rows for absent app identities, preserved stats, and DB transaction rollback on a genuine unique-index constraint failure. V1 import bypasses this v2-only API; v1 isolation is covered at orchestration in Task 6.
- [x] Step 5: Run `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LensWorkspaceDaoTest`; 6 tests, 0 failures; Room schema remains v12 (file format only).
- [x] Step 6: Commit `feat(backup): replace workspace transactionally` (`8a31e4d`; review follow-ups `dc02daa`, `0a52ee0`).

## Task 4: Apply per-lens preferences and recover active lens — COMPLETE

**Files:** `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`; active-lens reconciliation at the central lens-bind boundary in `app/src/main/java/com/mckimquyen/ui/ActHome.java`; tests `app/src/test/java/com/mckimquyen/util/UtilSettingsLensScopeTest.kt` and a focused `ActHome` test.

**Interfaces:** preference snapshot captures exact present/absent values for app scope, selection, frozen, and active lens. Replacement uses synchronous `SharedPreferences.Editor.commit()` and returns success; restore replays snapshot. No other prefs cleared.

- [x] Step 1: Add failing tests for snapshot, replacement, preserving unrelated per-lens/global settings, active-lens fallback, and v1 no-op.
- [x] Step 2: Run focused tests; confirm RED (`UtilSettings` workspace APIs missing).
- [x] Step 3: Add exact preference snapshot/replace/restore methods; remove only scope/selection/frozen key families, then write imported lens keys and active ID. Return false if commit fails.
- [x] Step 4: Add startup reconciliation: if active ID absent from current workspace, set `default` without touching other per-lens settings.
- [x] Step 5: Run `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.UtilSettingsLensScopeTest`; 13 tests pass. Instrumented `ActHomeQuickLensSwitchWidgetTest` compiles.
- [x] Step 6: Implemented in commits `fd15df7` (snapshot/replace/restore) and `71014b5` (active lens repair).

## Task 5: Wire export/import persistence orchestration and UI — IN PROGRESS

**Files:** `LayoutBackupIo.kt`, `ActSettings.java`; tests existing backup IO/widget tests.

**Interfaces:** export reads all workspace/lens settings and emits v2. Import returns typed result `Applied`, `Malformed`, `UnsupportedSchema`, `Failed`, `RecoveryRequired`, with preview counts and schema version. `WorkspaceBackupStore` seam allows deterministic DB/prefs failure injection without production flags.

- [ ] Step 1: Write failing export snapshot tests for two lenses, app layouts, scope/selection/frozen.
- [ ] Step 2: Write failing orchestration tests proving DB then prefs commit, DB restoration after preference failure, recovery-required after failed rollback, and no success on any failure.
- [ ] Step 3: Run targeted tests; confirm RED.
- [x] Step 4: Implement export snapshot collection off main thread and SAF write with failure propagation; v2 includes activeLensId.
- [ ] Step 5: Implement v2 import: snapshot DB/prefs → Room transaction → synchronous prefs commit → refresh shortcuts → success. On prefs failure, restore DB/prefs; rollback failure emits recovery-required + diagnostic log. Process death between stores documented; startup reconciles active lens. V1 routes existing semantics unchanged.
- [ ] Step 6: UI preview v2 shows lens and entry counts + destructive replace warning; Cancel performs no writes; confirm invokes persistence; v1 copy says it applies to default lens only. Dismiss dialog on Activity destroy.
- [ ] Step 7: Run `./gradlew testDevDebugUnitTest --tests '*LayoutBackup*' --tests '*LayoutImport*'`; expect PASS.
- [ ] Step 8: Commit `feat(backup): export and restore lens workspaces`.

## Task 6: Widget and integration regression coverage

**Files:** focused backup UI widget test and `LayoutWorkspaceBackupIntegrationTest.kt`.

- [ ] Step 1: Widget cancel test asserts workspace/layout/preferences unchanged.
- [ ] Step 2: Widget confirm test asserts preview warning/counts and refreshed lens UI.
- [ ] Step 3: Real Room + SharedPreferences integration test exports, mutates and imports 2+ lenses; asserts lens order, app layout, scope/selection/frozen, active lens, shortcut refresh, preserved open/palette/notification values, absent app ID retention.
- [ ] Step 4: Test v1 imports to default only; other lens/settings unchanged.
- [ ] Step 5: Test preference failure restores DB; test rollback failure returns recovery-required.
- [ ] Step 6: List devices with `adb devices`; if >1, ask user and lock one for session. Use only `adb -s <serial> shell am instrument`; do not run unscoped `connected*`. If screenshot shows ad, stop and wait for user confirmation.
- [ ] Step 7: Commit `test(backup): cover multi-lens restore flow`.

## Task 7: Full verification, smoke and docs

- [ ] Step 1: `./gradlew testDevDebugUnitTest`; expect zero failures; diagnose failures before reporting.
- [ ] Step 2: `./gradlew lintDevDebug` and `scripts/check-secrets.sh`; expect no new lint errors/warnings over baseline and clean scan of changed content.
- [ ] Step 3: Run targeted and applicable full instrumentation only on locked device via scoped `adb -s` command; record exact results/device/model/OS/build SHA.
- [ ] Step 4: Manual smoke export → mutate workspace → import; verify two or more lenses, names/order, active lens, app selection, freeze, app order; repeat import; verify cancel path; inspect logcat for crashes.
- [ ] Step 5: Update FEAT-006 follow-up story, `doc/feature.md`, `doc/task/README.md` with schema, compatibility, test/smoke evidence and limitations; do not rewrite historical evidence.
- [ ] Step 6: `git diff --check && git status --short && git diff --stat`; inspect callbacks/dialog lifecycle and transaction/compensation.
- [ ] Step 7: Whole-branch review; self-score using repo rubric. Do not push unless explicitly requested and required score/checks pass.

## Self-review

- Tasks map every spec requirement: schema parser (Task 1); plan (2); DB transaction (3); settings/reconciliation/compensation (4); orchestration/UI (5); coverage (6); verification/docs (7).
- No Room schema migration: this changes JSON file format only.
- `openCount`, `paletteColor`, and global `notificationCount` require preservation across lens rows; test same-identifier cross-lens cases.
- Room and preferences cannot be process-death atomic; compensation handles reported preference write failure, startup reconciliation handles active-lens mismatch after interrupted import. Do not claim perfect cross-store atomicity.

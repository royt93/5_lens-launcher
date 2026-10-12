# Workspace Backup v2 Final Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to execute this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Audit every branch change, add tests for verified behavior gaps, prove export/import on TECNO BG6, and push this branch only if final score exceeds 9/10 and every gate passes.

**Architecture:** Inventory/review the full diff first; validate findings; add focused tests using RED/GREEN; then run full checks and TECNO-only SAF smoke. Keep existing parser, transactional Room, preference compensation, UI and shortcut architecture unless audit verifies a defect. Complete docs and score using repository rubric before conditional push.

**Tech Stack:** Kotlin, Java, Android `org.json`, Room v12, SharedPreferences, coroutines, Android SAF, JUnit4/Robolectric, Android instrumentation, `adb`.

## Global Constraints

- Work only in `.claude/worktrees/agent-a41230bc3ec76454b`.
- TECNO BG6 `118743744X002560` is sole permitted device; Pixel `2B051FDH3006MU` is forbidden.
- Every device command uses `adb -s 118743744X002560`; never run Gradle install/connected tasks.
- If screenshot reveals an ad, stop immediately and wait for owner confirmation.
- No unrelated features, ads, IAP, consent, signing, release, dependency, Room-schema or migration work.
- Preserve existing worktree edits; never reset or checkout another branch.
- Push current branch only if final score is strictly `>9.0/10`, tests/checks/secret scan and TECNO manual smoke pass, review has no blocker, and worktree is clean. Never force-push.

---

## Task 1: Inventory and baseline branch

**Files:** no production changes; record evidence in audit report at Task 8.

- [ ] Confirm repo/branch/base/status with `pwd`, `git rev-parse --show-toplevel`, `git branch --show-current`, `git merge-base HEAD origin/dev`, `git status --short`. Expected worktree branch `worktree-agent-a41230bc3ec76454b`, base `855869a15f99d496fd2cb042440c0139c7521f62`.
- [ ] Inventory all 45 paths via `git diff --name-status origin/dev...HEAD` and `git diff --stat origin/dev...HEAD`; classify production, tests, resources, docs, local scratch. Do not stage ignored `.superpowers/sdd` scratch.
- [ ] Run baseline `./gradlew testDevDebugUnitTest`, `./gradlew lintDevDebug`, `scripts/check-secrets.sh`, `git diff --check`; capture exact output.
- [ ] Build finding table: path/line, concrete state/input, expected/actual behavior, proving test or evidence. No code edits until finding verified.

## Task 2: Parser, v1 compatibility and planner audit

**Files:** `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt`, `app/src/main/java/com/mckimquyen/util/LayoutImportApplier.kt`; tests `app/src/test/java/com/mckimquyen/model/LayoutBackupTest.kt`, `app/src/test/java/com/mckimquyen/util/LayoutWorkspaceImportPlanTest.kt`.

**Interfaces:** `LayoutBackup.parse(String)`, `LayoutImportApplier.planV1(LayoutBackup)`, `planV2(LayoutBackup)`, `previewCounts(WorkspacePlan)`.

- [ ] Compare tests against v1 round-trip/leniency; v2 required field/type checks, active lens, IDs/orders/scope/selection/entries, duplicates, malformed JSON, unsupported version and additive fields.
- [ ] Add a focused failing test only for uncovered distinct behavior. Example: parse `{"schemaVersion":99,"exportedAt":0,"entries":[]}` and assert `UnsupportedSchemaVersion(99)`; reuse existing equivalent test rather than duplicate.
- [ ] Run affected focused test; confirm RED from missing behavior, not fixture/setup error.
- [ ] If and only if defect confirmed, implement smallest parser/planner change. v1 contract stays intact; v2 remains strict; invalid input must not reach store.
- [ ] Run `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LayoutBackupTest --tests com.mckimquyen.util.LayoutWorkspaceImportPlanTest`; expect zero failures.
- [ ] Commit only changed parser/planner/test files with message `test(backup): close verified parser audit gaps` and required `Co-Authored-By` trailer.

## Task 3: Export and SAF I/O audit

**Files:** `app/src/main/java/com/mckimquyen/util/LayoutBackupIo.kt`, `LayoutBackupSnapshotFactory.kt`; tests `app/src/test/java/com/mckimquyen/util/LayoutBackupExportSnapshotTest.kt`, `app/src/androidTest/java/com/mckimquyen/util/LayoutBackupIoIntegrationTest.kt`.

**Interfaces:** `exportAsync(Context, Uri, (Boolean) -> Unit)`, `importAsync(Context, Uri, (LayoutBackupParseResult) -> Unit)`, `LayoutBackupSnapshotFactory.create(...)`.

- [ ] Trace Room/prefs reads → snapshot → SAF stream; verify ordering, all lenses, active ID, stream closure, Boolean failure, main-thread callback and no state mutation.
- [ ] Check coverage for output stream null, write exception, input stream null/read exception, callback thread, and `CancellationException`. Add tests only for uncovered branches; use existing seams/std-lib, no dependency.
- [ ] Run focused tests to confirm RED for each real gap; repair invalid test setup before any production edit.
- [ ] Fix verified failure handling minimally: close streams with `use`, return failure/malformed on IO errors, rethrow coroutine cancellation.
- [ ] Run `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LayoutBackupExportSnapshotTest` and `./gradlew compileDevDebugAndroidTestKotlin`.
- [ ] Commit only relevant I/O/snapshot/test files with attribution trailer.

## Task 4: Room atomicity and stats audit

**Files:** `app/src/main/java/com/mckimquyen/model/LensWorkspaceDao.kt`, `AppPersistentDao.kt`, `util/WorkspaceBackupRoomStore.kt`; tests `app/src/test/java/com/mckimquyen/model/LensWorkspaceDaoTest.kt`, `app/src/androidTest/java/com/mckimquyen/util/WorkspaceBackupImportIntegrationTest.kt`.

- [ ] Matrix: matching identity same lens; source only another lens; absent local identity parked hidden; uninstalled identity retained; v1 non-imported default row retained; duplicate invalid insert rollback; prior snapshot preserved.
- [ ] Compare every case with existing DAO AND adapter integration tests; DAO-only coverage does not prove store behavior.
- [ ] Add failing test for uncovered behavior; assert lens order, exact layout, visibility, counters, and unchanged other lenses.
- [ ] If proven defect, keep replacement operations inside `@Transaction`, preserve global counters by identifier, never let v1 modify other lens rows; keep schema v12.
- [ ] Run `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LensWorkspaceDaoTest`; run device integration later only with explicit TECNO serial.
- [ ] Commit only verified fix/tests, attribution included.

## Task 5: Preferences, compensation, cancellation and refresh audit

**Files:** `UtilSettings.kt`, `WorkspaceBackupImportCoordinator.kt`, `WorkspaceBackupRoomStore.kt`, `AppEventManager.kt`, `ActHome.java`, `LensShortcuts.kt`; tests `UtilSettingsLensScopeTest.kt`, `WorkspaceBackupImportCoordinatorTest.kt`, `WorkspaceBackupImportIntegrationTest.kt`.

- [ ] Enumerate branches: malformed/unsupported before store; v1 success/error/cancel; v2 DB/prefs snapshot failure; DB replacement error; prefs failure with successful rollback; DB rollback failure; prefs rollback false/throws; refresh failure; cancellation before mutation and after DB replacement.
- [ ] Add missing store-fake tests asserting operation order, no prefs/refresh after DB failure, both snapshots restored after prefs failure, recovery-required on compensation failure, refresh failure not success, and cancellation never swallowed. Confirm RED.
- [ ] Through integration test, prove unrelated curvature/icon-size/Smart Focus/global preferences survive; v1 does not alter lens/prefs; shortcut IDs update after successful import and not after failed import.
- [ ] Fix only verified defects; cancellation rethrows after compensation; keep shortcut refresh based on committed DB lens list.
- [ ] Run coordinator/preferences JVM classes and compile androidTest; commit focused changes with attribution.

## Task 6: Preview and lifecycle widget audit

**Files:** `app/src/main/java/com/mckimquyen/ui/ActSettings.java`, `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsLayoutBackupWidgetTest.kt`.

- [ ] Map v1 default-only copy; v2 lens/entry counts and destructive warning; malformed/future error; cancel no DB/prefs change; confirm invokes apply once; Applied/Failed/RecoveryRequired result; destroy dismisses dialog and suppresses callbacks touching destroyed Activity.
- [ ] Add missing widget cases through real `ActivityScenario` and production preview. Verify cancel using Room and prefs snapshots, not private dialog state only. Exercise positive button via current seam and await async callback without blocking main thread.
- [ ] Confirm test fails for expected current gap; implement minimal lifecycle/UI fix only if reproduced.
- [ ] Run `./gradlew compileDevDebugAndroidTestKotlin`; run focused widget later on TECNO.
- [ ] Commit only ActSettings/widget changes with attribution.

## Task 7: TECNO-only regression and manual SAF smoke

**Device:** TECNO BG6 `118743744X002560`, Android 13. No Pixel calls; no Gradle install/connected tasks.

- [ ] Run `adb devices`; do not issue device commands without explicit serial.
- [ ] Build only: `./gradlew assembleDevDebug assembleDevDebugAndroidTest`.
- [ ] Install only using `adb -s 118743744X002560 install -r <exact-apk-path>` for main and instrumentation APKs. Before screenshot, inspect for ads; if ad visible, stop and wait for owner “done”.
- [ ] Run `WorkspaceBackupImportIntegrationTest`, `FrmSettingsLayoutBackupWidgetTest`, and `LayoutBackupIoIntegrationTest` with `adb -s 118743744X002560 shell am instrument -w -r -e class <fqcn> com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`. Run classes separately if runner rejects comma-separated classes.
- [ ] Manual SAF smoke: create two lenses with distinct order/names; vary active lens, app scope/selection/frozen; export through real picker; mutate lens and layout state; import and confirm exact restoration; repeat import; preview then cancel and assert no changes; check dynamic shortcuts and app fatal logs. Stop if ad appears.
- [ ] Record exact pass count, device/model/OS, APK SHA-256 and each observed smoke outcome. Do not equate integration tests with manual SAF smoke.

## Task 8: Final gates, docs, score and conditional push

**Files:** plan/spec, `doc/task/done/p2-feat-feat-006-layout-backup-export.md`, `doc/feature.md`, `doc/task/README.md`, new dated audit record under `doc/task/`.

- [ ] Run `./gradlew testDevDebugUnitTest --rerun-tasks`; parse XML count; require zero failures/errors.
- [ ] Run `./gradlew lintDevDebug`, `scripts/check-secrets.sh`, `git diff --check`; compare warning count to known baseline and avoid unrelated asset fixes.
- [ ] Review `git diff --name-status origin/dev...HEAD`; inspect each path for scope, secrets, accidental binaries, scratch reports, lifecycle and transaction correctness. Verify all review findings against actual failing/passing test evidence.
- [ ] Update docs with schema/v1 compatibility, exact tests, TECNO smoke, limitations and final score; preserve historical 2026-09-24 story.
- [ ] Score repository rubric exactly: correctness 2.0, unit 1.5, widget 1.0, integration 1.5, smoke 1.0, security 1.0, performance/lifecycle 1.0, docs 1.0. Every deduction cites missing evidence or verified issue; no rounding up.
- [ ] Push gate: require score `>9.0`, all applicable tests green, secret scan clean, manual TECNO SAF smoke pass, no blocking review issue, clean worktree. If any fail, do not push; report what remains.
- [ ] If all gates pass, verify `git branch --show-current` is intended feature branch and `git remote get-url origin` is expected GitHub repository; run `git push origin HEAD` only. No force push. If push rejects, stop.
- [ ] Report score, exact evidence, commit/push outcome, TECNO model/OS/hash and residual limits. Never claim unperformed smoke.

## Self-review

- Spec coverage maps to Tasks 1–2 (inventory/parser/planning), Task 3 (SAF), Task 4 (Room), Task 5 (prefs/failure/refresh), Task 6 (UI/lifecycle), Task 7 (TECNO smoke), Task 8 (docs/audit/push).
- Unperformed manual SAF smoke is an explicit gate; score >9 and push are forbidden without it.
- No dependency or Room schema change is planned.
- Every new production change requires a behavior-proving test; existing equivalent coverage is reused rather than duplicated.
- All Git commits include the required attribution trailer.
- No unresolved placeholders or unspecified devices/tasks remain.

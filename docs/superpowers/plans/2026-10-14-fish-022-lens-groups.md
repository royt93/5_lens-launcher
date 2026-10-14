# FISH-022 Lens Groups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add single-level lens groups, bulk management, and backup-compatible collapsed sections without changing lens paging order or app-layout semantics.

**Architecture:** Store nullable `groupName` on each Room lens row and derive groups from those labels. Add a dedicated management Activity for grouped browsing/bulk edits, keep a current-lens quick action in Home's popup, extend workspace backup v2 additively, and include collapsed-group UI state in the existing preference snapshot/replace/rollback path.

**Tech Stack:** Kotlin/Java, Room v13, Android Activity/RecyclerView, SharedPreferences, `org.json`, JUnit4/Robolectric, AndroidJUnit4 instrumentation.

## Global Constraints

- Work only on existing branch `dev`; do not create/switch branch or worktree.
- Keep `LensWorkspace` as sole owner of a lens's group label; do not add Group table or nested groups.
- Preserve global `ORDER_INDEX`/`CREATED_AT` paging order and active-lens semantics.
- Normalize group name by trim + case-fold; first spelling encountered in lens order is canonical display spelling for matching variants.
- Bulk assignment uses stable lens IDs and a Room transaction.
- Backup remains schema v2; `groupName` and root `collapsedGroupNames` are additive optional fields; absent fields mean null group/all sections expanded.
- Import replaces collapsed-group state together with workspace state and includes it in existing preference compensation/rollback.
- No ads/VIP, store-assets, app visibility/scope semantics, search ranking, or unrelated cleanup.
- Device smoke only on TECNO KJ7 `115333744A005844`; TECNO BG6 `118743744X002560` only if KJ7 absent. Use explicit `adb -s`; never use Gradle `connected*` tasks.

---

## File map

- Modify `app/src/main/java/com/mckimquyen/model/LensWorkspace.kt`: persist nullable group label; preserve it in rename/copy.
- Modify `app/src/main/java/com/mckimquyen/model/AppDatabase.kt` and create `app/schemas/com.mckimquyen.model.AppDatabase/13.json`: schema v13 migration.
- Modify `app/src/main/java/com/mckimquyen/model/LensWorkspaceDao.kt`: transactional bulk group assignment and existing CRUD preservation.
- Create `app/src/main/java/com/mckimquyen/util/LensGroupPolicy.kt`: pure name normalization, canonical group derivation, collapsed-state canonicalization.
- Modify `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt`, `app/src/main/java/com/mckimquyen/util/LayoutBackupSnapshotFactory.kt`, `app/src/main/java/com/mckimquyen/util/LayoutImportApplier.kt`, `app/src/main/java/com/mckimquyen/util/WorkspaceBackupImportCoordinator.kt`, `app/src/main/java/com/mckimquyen/util/WorkspaceBackupRoomStore.kt`, `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`: additive v2 payload and atomic preference snapshot/replace/rollback.
- Create `app/src/main/java/com/mckimquyen/ui/LensManagementActivity.kt` and `app/src/main/java/com/mckimquyen/ui/LensGroupAdapter.kt`; replace `ActHome.showLensSwitcherDialog` entry with Activity launch; preserve lens popup quick group action/manager entry.
- Modify `app/src/main/AndroidManifest.xml`, `app/src/main/res/layout/act_lens_management.xml`, and localized string resources in every shipped `values-*` directory for new UI.
- Add or update focused unit tests, Room migration tests, backup integration tests, widget tests, and management Activity instrumentation tests next to the existing FISH-008, FISH-021, and FEAT-006 suites.
- Modify `doc/feature.md` and add `doc/task/todo/p2-fish-fish-022-lens-groups.md` when implementation starts; move story to `done/` only after gates pass.

---

### Task 1: Room v13 group metadata and migration

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/model/LensWorkspace.kt`
- Modify: `app/src/main/java/com/mckimquyen/model/AppDatabase.kt`
- Create: `app/schemas/com.mckimquyen.model.AppDatabase/13.json`
- Test: `app/src/androidTest/java/com/mckimquyen/model/AppDatabaseMigrationIntegrationTest.kt`

**Interfaces:**
- `LensWorkspace.groupName: String? = null` mapped to nullable column `GROUP_NAME`.
- Existing Room CRUD continues to preserve `groupName` through entity reads/writes; `LensWorkspace.renameLens` and `createLens` copy behavior are implemented after the field exists.

- [ ] **Step 1: Add RED migration test**

Extend `AppDatabaseMigrationIntegrationTest` to create the existing real v10 SQLite fixture, open/migrate it through `MIGRATION_10_11` and `MIGRATION_11_12` into a v12 database, close it, then reopen with the production migration chain including `MIGRATION_12_13`. Assert old lens rows retain ID/name/order/createdAt, `groupName == null`, and app-layout rows remain unchanged. Update the existing v10→current helper chain to include the 12→13 migration as well.

- [ ] **Step 2: Run migration test and confirm RED**

Run `./gradlew assembleDevDebugAndroidTest`, install the test APK on TECNO KJ7 with explicit `adb -s 115333744A005844`, then run the single migration test using direct `am instrument`. Expected: test fails because version-13 entity/migration is missing. Do not use `connected*` tasks.

- [ ] **Step 3: Implement schema and migration**

Set `@Database(... version = 13)`, register `MIGRATION_12_13`, and execute exactly `ALTER TABLE LENS_WORKSPACE ADD COLUMN GROUP_NAME TEXT` (nullable). Add `groupName` with Room `@ColumnInfo(name = "GROUP_NAME")`. Export Room schema with the repository's existing Room schema workflow.

- [ ] **Step 4: Run migration test and confirm GREEN**

Expected: migration passes; schema export diff only adds v13.

### Task 2: Pure group-name and grouping policy

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/LensGroupPolicy.kt`
- Create: `app/src/test/java/com/mckimquyen/util/LensGroupPolicyTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt` to call backup-label canonicalization.

**Interfaces:**
- `data class LensGroupSection(val groupName: String?, val lenses: List<LensWorkspace>)`: `groupName == null` is the ungrouped section.
- Backup overload accepts `List<LayoutBackupLens>` and uses `(orderIndex, inputIndex)` to canonicalize before parser returns a successful model; Room/UI overload accepts `List<LensWorkspace>` and uses `(orderIndex, createdAt)`.
- `normalizeName(raw: String): String?`: trim; return null for blank.
- `canonicalKey(name: String): String`: `name.trim().lowercase(Locale.ROOT)`; never use device-default locale.
- `canonicalize(lenses: List<LensWorkspace>): List<LensWorkspace>` and `canonicalizeBackup(lenses: List<LayoutBackupLens>): List<LayoutBackupLens>`: process by `(orderIndex, createdAt)` for Room or `(orderIndex, inputIndex)` for backup; first trimmed spelling per canonical key wins; return canonicalized labels in original list order.
- `sections(lenses: List<LensWorkspace>): List<LensGroupSection>`: sort by `(orderIndex, createdAt)`, emit ungrouped section first, then grouped sections by first canonical appearance; preserve sorted order within each section.
- `canonicalizeCollapsedNames(names: Set<String>, lenses: List<LensWorkspace>): Set<String>`: map to canonical display spelling from `canonicalize(lenses)` and discard unknown groups.
- Ordering comparator for Room: `compareBy<LensWorkspace> { it.orderIndex }.thenBy { it.createdAt }`, matching existing `LensWorkspaceDao.getAll()`.

- [ ] **Step 1: Add RED tests**

Cover blank names, whitespace trim, case-fold equivalence, first-spelling wins under `(orderIndex, createdAt)`, ungrouped-first section ordering, lens order preservation, empty group removal, and collapsed-state canonicalization/removal of unknown groups.

- [ ] **Step 2: Run pure unit tests and confirm RED**

Run `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LensGroupPolicyTest`.
Expected: compilation/test failure because policy does not exist.

- [ ] **Step 3: Implement pure policy only**

Implement the four functions with standard Kotlin collections; no Android or Room dependency.

- [ ] **Step 4: Run unit tests and confirm GREEN**

Expected: all policy cases pass; `git diff --check` clean.

### Task 3: Transactional bulk DAO

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/model/LensWorkspaceDao.kt`
- Test: `app/src/test/java/com/mckimquyen/model/LensWorkspaceDaoTest.kt`

**Interfaces:**
- DAO `assignGroup(lensIds: Set<String>, groupName: String?)`: transactionally updates exactly the provided existing lens IDs; groupName is already normalized/canonicalized by policy; fail atomically if IDs missing or blank non-null name supplied.

- [ ] **Step 1: Add RED DAO tests**

Add DAO tests for assign, ungroup, missing-ID rollback, and no change to global lens order.

- [ ] **Step 2: Run focused DAO tests and confirm RED**

Run `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LensWorkspaceDaoTest`. Expected: missing DAO API or failing assertions before implementation.

- [ ] **Step 3: Implement bulk DAO transaction**

Validate non-empty ID set; require each ID exists; require non-null name is nonblank and already canonical (equal to `LensGroupPolicy.normalizeName(name)`); perform updates in one `@Transaction`. Do not reorder rows.

- [ ] **Step 4: Run DAO tests and confirm GREEN**

Expected: all migration/DAO behavior passes.

### Task 4: Schema-v2 backup group metadata and collapsed-state rollback

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/LayoutBackupSnapshotFactory.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/LayoutImportApplier.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/WorkspaceBackupImportCoordinator.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/WorkspaceBackupRoomStore.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`
- Test: `app/src/test/java/com/mckimquyen/model/LayoutBackupTest.kt`
- Test: `app/src/test/java/com/mckimquyen/util/LayoutBackupExportSnapshotTest.kt`
- Test: `app/src/test/java/com/mckimquyen/util/WorkspaceBackupImportCoordinatorTest.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/util/WorkspaceBackupImportIntegrationTest.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/util/LayoutBackupIoIntegrationTest.kt`

**Interfaces:**
- `LayoutBackupLens.groupName: String? = null`.
- `LayoutBackup.collapsedGroupNames: Set<String> = emptySet()`.
- `LayoutImportApplier.WorkspaceLensPlan.groupName: String?` and `WorkspacePlan.collapsedGroupNames: Set<String>`.
- Workspace preference snapshot/replace/restore includes collapsed group names as one workspace-owned key.

- [ ] **Step 1: Write failing model/parser/export tests**

Prove old v2 JSON without either additive field parses with all groups null and collapsed set empty. Prove new JSON round-trips group names and collapsed names. Prove malformed type/blank names reject whole backup. Prove `Work` and ` work ` across lens objects canonicalize to the first spelling in order; repeated membership is valid. Prove unknown collapsed names are dropped/canonicalized according to policy.

- [ ] **Step 2: Run model tests and confirm RED**

Run `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LayoutBackupTest --tests com.mckimquyen.util.LayoutBackupExportSnapshotTest`.
Expected: tests fail to compile or fail assertions before implementation.

- [ ] **Step 3: Extend data model and strict parser/export**

Append optional `groupName` and `collapsedGroupNames` with defaults to avoid breaking Kotlin constructors. Export writes `groupName` (JSON null when ungrouped) and root array every time. Parser accepts missing fields as null/empty; reject wrong types and whitespace-only non-null group names; canonicalize names using Task 2 policy.

- [ ] **Step 4: Extend plan and Room replacement**

Copy groupName from backup plan into `LensWorkspace`; exporter factory copies it from Room. Do not alter app row layout/stat handling.

- [ ] **Step 5: Extend preferences snapshot, replace, and restore**

Add one collapsed-group preference key. `snapshotWorkspacePreferences` captures it; replace removes old workspace settings then writes imported canonical collapsed names; restore puts back prior set. Keep synchronous `commit()` behavior so existing coordinator rollback semantics cover this field.

- [ ] **Step 6: Add rollback regression and verify**

Extend coordinator fake-store tests so preference commit failure followed by rollback restores previous collapsed names as well as prior DB/lens/prefs. Extend device integration round-trip and old-v2 fixture tests.

Run the focused model/coordinator tests and existing `WorkspaceBackupImportIntegrationTest`/`LayoutBackupIoIntegrationTest` on TECNO in Task 8.

### Task 5: Management Activity browse/switch UI

**Files:**
- Create: `app/src/main/java/com/mckimquyen/ui/LensManagementActivity.kt`
- Create: `app/src/main/java/com/mckimquyen/ui/LensGroupAdapter.kt`
- Create: `app/src/main/res/layout/act_lens_management.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Modify: `app/src/main/res/values/strings.xml` and every locale counterpart.
- Create: `app/src/androidTest/java/com/mckimquyen/ui/LensManagementActivityWidgetTest.kt`

**Interfaces:**
- Activity receives no mutable lens-list parcelable. It reads Room via existing DAO or reloads from a stable request; launches Home with an explicit lens ID extra when a lens is selected.
- Home validates requested ID against current lenses, switches pager/active lens, then finishes manager; if missing, retain current active lens and show no crash.

- [ ] **Step 1: Add widget tests against production Activity**

Cover ungrouped section, group sections in first-appearance order, collapsed-state restore, expansion toggle, lens rows following existing global order, tapping a lens returns to Home and selects exact ID, invalid/deleted lens ID safely falls back, Activity recreate retains selection/mode safely.

- [ ] **Step 2: Run tests and confirm RED**

Run:

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s 115333744A005844 install -r app/build/outputs/apk/dev/debug/<current-dev-debug-apk>.apk
adb -s 115333744A005844 install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.LensManagementActivityWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: test compilation or initialization fails before the Activity/layout/entry exists; do not use `connected*` tasks.

- [ ] **Step 3: Build browsing Activity and replace current switch dialog**

Implement grouped RecyclerView/List UI; collapsed states update SharedPreferences. Tap section header toggles and persists state; tap row dispatches stable lens ID to Home. Replace `showLensSwitcherDialog()` path called by `tvLensName` with Activity launch. Keep current pager order unchanged.

- [ ] **Step 4: Implement Home return and lifecycle**

Handle explicit selected-lens extra from Activity result/intent; validate against current DAO lenses before changing pager and `KEY_ACTIVE_LENS_ID`; switch app snapshot through existing refresh pipeline. Dismiss/clear Activity observers/adapters/listeners on destroy.

- [ ] **Step 5: Run Activity widget tests and existing lens-switch regressions**

Run `LensManagementActivityWidgetTest`, `ActHomeQuickLensSwitchWidgetTest`, `ActHomeLensManagementWidgetTest`, and `ActHomeMultiLensWidgetTest`; expected all pass.

### Task 6: Bulk group edit and current-lens popup action

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/LensManagementActivity.kt`
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Modify: `app/src/androidTest/java/com/mckimquyen/ui/LensManagementActivityWidgetTest.kt`, `app/src/test/java/com/mckimquyen/model/LensWorkspaceDaoTest.kt`.

**Interfaces:**
- Activity edit mode holds selected lens IDs, not adapter positions.
- DAO `assignGroup(lensIds, groupName)` from Task 3 is the only mutation path; callbacks refresh derived sections from Room.

- [ ] **Step 1: Add RED bulk tests**

Cover bulk assign to existing group, create a group from selection, ungroup, no-op/empty selection behavior, rename/case-fold reuse of existing canonical spelling, selection surviving collapse/expand, and atomic rejection when one lens ID disappears before commit.

- [ ] **Step 2: Implement edit mode UI**

Add selection affordance, group action sheet/dialog, create-name entry, assign-existing list, and ungroup. Normalize names through policy. Cancel makes no writes. Confirm sends stable IDs to DAO transaction, reloads Room, exits edit mode only on success; errors leave selection/state available and show localized error.

- [ ] **Step 3: Add quick current-lens group action**

Add popup item “Manage group” that opens a one-lens group editor (reuse normalization and DAO path) and “Manage lenses” entry to Activity. Preserve all existing menu item IDs/behavior and icon/tint conventions.

- [ ] **Step 4: Run relevant widget/DAO tests and lint**

Expected: all selected lens management tests pass; `./gradlew lintDevDebug` has zero new errors.

### Task 7: Complete locales and feature/backlog documentation

**Files:**
- Modify: `app/src/main/res/values*/strings.xml` for all supported locale directories.
- Modify: `doc/feature.md`.
- Create/move: `doc/task/inprogress/p2-fish-fish-022-lens-groups.md` then `doc/task/done/p2-fish-fish-022-lens-groups.md` after DoD.
- Test: existing locale string completeness/overflow tests; extend the relevant test with every new FISH-022 string key.

- [ ] **Step 1: Add localized strings**

Translate every new visible label/action/error into every locale represented by the existing strings resource tree; do not leave fallback-English values in locale files if repository conventions require explicit parity.

- [ ] **Step 2: Verify locale completeness and layout fit**

Run `LensStringTranslationTest` and long-label overflow/fit tests. Add one regression if new section headers/actions are outside current coverage.

- [ ] **Step 3: Update docs/status**

Record owner-approved design, tests and exact limits in feature tracker/story. Move the story file to `done/` only after all tests, smoke, audit score, and gates succeed.

### Task 8: End-to-end verification on locked TECNO

**Files:**
- No new production files; evidence recorded in story.

- [ ] **Step 1: Confirm target device before build/install/run**

Run `adb devices`. Use TECNO KJ7 `115333744A005844` only; if absent, use TECNO BG6 `118743744X002560`; if no TECNO is connected, stop and ask. Never touch Pixel/S24U. Never run Gradle `connected*` tasks.

- [ ] **Step 2: Run focused suite on exactly the chosen TECNO**

Build app and test APK; install with `adb -s <serial> install -r <apk>`; run direct `adb -s <serial> shell am instrument ...` for migration, management Activity, popup, and backup tests. Record counts and device/API version.

- [ ] **Step 3: Manual smoke**

Create at least three lenses; create two groups; assign via popup and bulk Activity; collapse/expand; switch lens from Activity; rename/duplicate/delete lenses and verify group semantics; export backup; change labels/collapse state; import and verify round trip; inject a failed preference commit in test coverage (not manual device) and verify rollback. Capture logcat for crash evidence.

- [ ] **Step 4: Run final quality gates**

Run `./gradlew testDevDebugUnitTest --rerun-tasks`, `./gradlew lintDevDebug`, `scripts/check-secrets.sh`, and `git diff --check`. Record warnings/failures accurately.

- [ ] **Step 5: Audit and finish on dev**

Review full diff against spec, score with repo rubric, verify only `dev` changed and working tree state. Push only if score exceeds 9.0 and owner-approved gate/test policy is satisfied; never create another branch.

## Plan self-review

- Spec coverage: model/migration, grouping rules, browse/switch Activity, bulk CRUD, popup, backup compatibility/collapsed state, rollback, localization, tests, TECNO smoke and docs all have explicit tasks.
- No unresolved placeholders; all named interfaces are defined in Tasks 1–4 or by the existing Room/Activity flow.
- Dependency cycle resolved: Task 1 adds the nullable `LensWorkspace.groupName` field with Room migration; Task 2 policy consumes that field; Task 3 DAO consumes Task 2 policy. The database v12→13 test explicitly stages v12 through existing migrations before opening with the production 12→13 migration.
- Backup canonicalization boundary is explicit: `LayoutBackupLens` labels canonicalize by `(orderIndex, inputIndex)` in parser; Room/UI labels canonicalize by `(orderIndex, createdAt)`; both use `canonicalKey = trim().lowercase(Locale.ROOT)` and preserve first spelling in each ordering.
- Ordering: Room field/migration → pure policy/parser overload → bulk DAO → backup transaction → browse UI → bulk UI → locales/docs → device verification. No task requires a later interface.

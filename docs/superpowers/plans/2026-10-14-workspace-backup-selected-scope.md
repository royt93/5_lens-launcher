# Workspace Backup Selected Scope Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reject schema-v2 workspace backups that select `SELECTED` scope but contain no selected app IDs, preventing imports from creating blank lenses.

**Architecture:** Enforce the cross-field invariant while parsing each `LayoutBackupLens`, before planning or any database/preferences mutation. Unit coverage pins parser behavior; one instrumentation case verifies malformed import returns before changing the live workspace.

**Tech Stack:** Kotlin, `org.json`, JUnit4/Robolectric unit tests, AndroidJUnit4 instrumentation, Room, SharedPreferences.

## Global Constraints

- Work only on the existing `dev` branch; do not create or switch branches/worktrees.
- Reject invalid backup as `LayoutBackupParseResult.Malformed`; do not normalize or auto-select.
- Preserve valid `ALL` + empty IDs and `SELECTED` + non-empty IDs combinations.
- Do not change schema, add dependencies, modify UI, or touch unrelated files.
- Focused device instrumentation must target only TECNO KJ7 serial `115333744A005844`; do not use Gradle `connected*` tasks.

---

### Task 1: Enforce and test strict parser invariant

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt` (`LayoutBackupLens.fromJson`)
- Modify: `app/src/test/java/com/mckimquyen/model/LayoutBackupTest.kt`

**Interfaces:**
- Consumes: `LensAppScope`, parsed `scope`, and `selectedIds` in `LayoutBackupLens.fromJson`.
- Produces: invalid cross-field combination returns `null`; `LayoutBackup.parse` therefore returns `LayoutBackupParseResult.Malformed` for the whole file.

- [ ] **Step 1: Add failing regression test**

Add a test alongside the schema-v2 adversarial cases:

```kotlin
@Test
fun `v2 selected scope with empty selected ids is malformed`() {
    val json = """
        {"schemaVersion":2,"exportedAt":0,"activeLensId":"default","lenses":[
            {"id":"default","name":"Home","orderIndex":0,"scope":"SELECTED","selectedAppIds":[],"frozen":false,"entries":[]}
        ]}
    """.trimIndent()

    assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
}
```

Also pin valid neighboring states, either in this test or existing tests: `scope=ALL` with `selectedAppIds=[]` parses successfully; a `SELECTED` lens with one valid selected ID parses successfully.

- [ ] **Step 2: Run the focused unit test and confirm RED**

Run:

```bash
./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LayoutBackupTest
```

Expected before implementation: the new empty-selection case fails because parser currently returns `Success`.

- [ ] **Step 3: Add the minimal parser validation**

Immediately after the selected IDs have been parsed and duplicate IDs rejected in `LayoutBackupLens.fromJson`, add:

```kotlin
if (scope == LensAppScope.SELECTED && selectedIds.isEmpty()) return null
```

Keep the existing whole-file malformed behavior and do not alter `LensAppScopeEditor` or import application code.

- [ ] **Step 4: Run focused unit tests and confirm GREEN**

Run:

```bash
./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LayoutBackupTest
```

Expected: all `LayoutBackupTest` cases pass, including the new rejection and valid-neighbor cases.

- [ ] **Step 5: Check diff whitespace and scope**

Run:

```bash
git diff --check
git status --short --branch
```

Expected: branch remains `dev`; only the two listed files changed; no whitespace errors.

### Task 2: Prove malformed import is non-mutating

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/util/WorkspaceBackupImportIntegrationTest.kt`

**Interfaces:**
- Consumes: `LayoutBackup.parse` result and `WorkspaceBackupImportCoordinator.apply`.
- Produces: malformed backup returns `WorkspaceBackupImportResult.Malformed`; Room rows/lenses and workspace preferences remain equal to pre-call snapshots.

- [ ] **Step 1: Add instrumentation regression at the real file-import boundary**

Extend `WorkspaceBackupImportIntegrationTest` with a real cache-file `Uri` and `CountDownLatch`, matching `LayoutBackupIoIntegrationTest`'s existing `importAsync` pattern. Seed two lenses, rows, and per-lens preferences; snapshot `lensWorkspaceDao().getAll()`, `appPersistentDao().getAllRows()`, active lens ID, and each lens's scope/selection/frozen values. Write this JSON to the cache file:

```json
{"schemaVersion":2,"exportedAt":0,"activeLensId":"default","lenses":[{"id":"default","name":"Home","orderIndex":0,"scope":"SELECTED","selectedAppIds":[],"frozen":false,"entries":[]}]}
```

Call `LayoutBackupIo.importAsync(context, uri)`, await the callback, assert `LayoutBackupParseResult.Malformed`, then assert every Room and preference snapshot is unchanged. This tests the real ContentResolver/file read and proves malformed input stops before `LayoutBackupIo.applyWorkspaceAsync`/`WorkspaceBackupImportCoordinator`; do not construct an invalid `LayoutBackup` model because `apply` intentionally accepts only parsed backups.

- [ ] **Step 2: Run the focused instrumentation class on TECNO KJ7 only**

Build/install only if the installed APK does not contain current source. Use explicit serial:

```bash
adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.util.WorkspaceBackupImportIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK`, including the malformed-import non-mutation regression. Do not use `connectedDevDebugAndroidTest`.


- [ ] **Step 3: Re-run parser unit tests and final checks**

Run:

```bash
./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LayoutBackupTest
git diff --check
git status --short --branch
```

Expected: all focused tests pass; only the three planned files are modified; branch remains `dev`.

- [ ] **Step 4: Report verified outcome**

State exact test counts and whether device instrumentation ran. Do not commit or push unless the owner separately authorizes it; do not claim an unrun test passed.

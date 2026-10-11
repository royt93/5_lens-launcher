# Task 3 report — Transactional Room workspace replacement

## Implemented
- `LensWorkspaceDao.replaceWorkspace(workspaces, importedRows)` validates workspace/default-lens membership, duplicate lens IDs, unknown app lens IDs, and duplicate `(lensId, identifier)` rows before mutation.
- Room `@Transaction` snapshots existing app rows, restores imported app layout, keeps old app identities absent from backup as default-layout rows with layout reset to defaults, replaces app rows, then replaces lens rows. DB failures roll back both tables.
- Preserves `openCount`, `paletteColor`, and `notificationCount` from existing records by stable identifier across lens changes. Room remains schema v12.

## TDD and verification
- RED: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LensWorkspaceDaoTest` first failed compilation because `replaceWorkspace` did not exist.
- First GREEN attempt exposed an important behavior gap: app absent from backup lost non-layout stats. Test failed `missing preserved non-layout data`; implementation changed to retain absent app records with layout reset.
- GREEN: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LensWorkspaceDaoTest` — BUILD SUCCESSFUL, 6 tests, 0 failures.
- `git diff --check` passed.

## Files
- `app/src/main/java/com/mckimquyen/model/AppPersistentDao.kt`
- `app/src/main/java/com/mckimquyen/model/LensWorkspaceDao.kt`
- `app/src/test/java/com/mckimquyen/model/LensWorkspaceDaoTest.kt`

## Commit
- `8a31e4d feat(backup): replace workspace transactionally`

## Review follow-up
- Owner selected hidden default-lens parking rows for apps absent from a backup. Updated `appVisible=false`, default layout/order/favorite/folder/pinned-zone reset, while preserving usage/palette/notification values. Updated spec and plan to match. The absent-app test asserts the row stays hidden.
- Marked `replaceWorkspace` explicitly v2-only; v1 must use the default-lens layout applier. Added `OnConflictStrategy.ABORT` for batch row inserts.
- Rollback test needed a genuine post-delete insert failure, not preflight validation. Duplicate identities now reach Room insert and produce a constraint exception; assertions verify both workspace and app rows roll back.
- Re-ran `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.LensWorkspaceDaoTest`: BUILD SUCCESSFUL, 6 tests, 0 failures.
- Per-lens SharedPreferences remain outside this Room transaction; Task 4/5 must implement snapshot/commit/compensation and document process-death gap. No preferences/UI changed here.

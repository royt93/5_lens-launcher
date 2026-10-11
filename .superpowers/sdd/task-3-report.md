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

## Remaining concern
- Per-lens SharedPreferences are not part of this Room transaction; Task 4/5 must implement snapshot/commit/compensation and explicit process-death limitation. No preferences/UI were touched here.

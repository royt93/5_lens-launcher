# FISH-032 Auto Aperture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize distortion on a newly duplicated lens from number of `AppPersistent` layout rows copied, while leaving existing lenses unchanged and preserving later user overrides.

**Architecture:** Add one pure three-band mapper. Return the exact source row count from the same Room read that performs `duplicateLensLayout`; pass the created lens and count through the existing async `LensWorkspace.createLens` callback. `UtilSettings.duplicateLensSettings` continues copying all existing settings except distortion, which is initialized from the mapper and copied count. No runtime recount or migration.

**Tech Stack:** Kotlin + Java, Room DAO, SharedPreferences (`UtilSettings`), JUnit4/Robolectric, AndroidJUnitRunner/ActivityScenario, Gradle.

## Global Constraints

- Device policy: **Pixel only every session** (owner instruction 2026-11-03); verify `adb devices -l`, use connected Pixel only, lock one Pixel serial for session; never touch TECNO, Samsung, or OPPO.
- No Ads/IAP/monetization and no new TalkBack/A11Y work.
- No dependency, Room schema change, backup format change, preference key, migration, UI, or `LensGridCache` change.
- Auto-aperture runs only when duplicating to a new lens. Existing lenses and stored distortion remain unchanged; no startup or app-count-change recompute.
- Count rows actually copied from the source lens. Stale `AppPersistent` rows are included because they are copied into the target.
- Mapping: `0..24 -> 3.5f`, `25..99 -> 2.5f`, `>=100 -> 1.8f`. Keep thresholds/values as named constants; all values within existing range `0.5f..5.0f`.
- User pinch/slider writes after creation win. A manually adjusted source distortion is deliberately not copied to target; all other existing lens settings continue to copy.
- Local commits only; never stage `.idea/`; no push without current explicit authorization.
- Gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `bash scripts/check-secrets.sh`.
- Before device work: backup installed APK/data/settings and verify hashes. After smoke restore and compare every regular-file hash. If ad/consent covers screenshot, stop and wait; never accept consent or bypass.

---

## Verified code and file map

- `app/src/main/java/com/mckimquyen/model/LensWorkspace.kt`
  - `createLens(name, copyFromLensId, onDone)` has one production caller.
  - `createLensNow(name, copyFromLensId)` runs on IO, inserts Room lens, then calls `AppPersistentDao.duplicateLensLayout`.
  - `duplicateLensLayout` internally reads source rows and copies each row. It currently returns `Unit`.
- `app/src/main/java/com/mckimquyen/model/AppPersistentDao.kt`
  - `getAllForLens(lensId): List<AppPersistent>` and `duplicateLensLayout(sourceLensId, targetLensId)` are suspend APIs.
- `app/src/main/java/com/mckimquyen/ui/home/LensMenuCoordinator.java`
  - `createLensDialog` calls `LensWorkspace.createLens`, finds new lens by diffing list, then calls `UtilSettings.duplicateLensSettings` and adopts the list.
- `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`
  - `getDistortionFactor`, `saveDistortionFactor`, `duplicateLensSettings` are existing per-lens APIs. `duplicateLensSettings` currently reads source distortion and copies profile, hood, icon size, scope, hidden IDs, freeze, Smart Focus, and custom preset.
- Create `app/src/main/java/com/mckimquyen/util/ApertureAutoDefault.kt`.
- Create `app/src/test/java/com/mckimquyen/util/ApertureAutoDefaultTest.kt`.
- Extend `app/src/test/java/com/mckimquyen/util/UtilSettingsPerLensTest.kt`.
- Extend `app/src/test/java/com/mckimquyen/model/LensWorkspaceDaoTest.kt` (Robolectric `@RunWith`, tests call `createLensNow` directly and existing async create API).
- Extend `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt` for production Create lens flow if its current fixture can assert settings cleanly; inspect existing setup before choosing assertions.
- Modify `LensWorkspace.kt`, `AppPersistentDao.kt`, `LensMenuCoordinator.java`, `UtilSettings.kt` only as specified below.
- Update story, `doc/task/README.md`, `doc/feature.md`, and add `doc/task/AUDIT_FISH-032_2026-11-03.md` at closure.

## Interfaces

- `ApertureAutoDefault.forAppCount(count: Int): Float`.
- Named constants: `SMALL_LENS_MAX_APP_COUNT = 24`, `LARGE_LENS_MIN_APP_COUNT = 100`, `SMALL_LENS_DISTORTION = 3.5f`, `MEDIUM_LENS_DISTORTION = 2.5f`, `LARGE_LENS_DISTORTION = 1.8f`.
- `AppPersistentDao.duplicateLensLayout(sourceLensId: String, targetLensId: String): Int` returns number of rows successfully copied. Implementation does one source-row read and duplicates exactly that list inside the existing `@Transaction`.
- `LensWorkspace.createLensNow(name: String, copyFromLensId: String): CreatedLens` returns `data class CreatedLens(val lens: LensWorkspace, val copiedAppCount: Int)`.
- `LensWorkspace.createLens(name, copyFromLensId, onDone)` callback delivers `(lenses: List<LensWorkspace>, created: CreatedLens)`.
- `UtilSettings.duplicateLensSettings(fromLensId: String, toLensId: String, copiedAppCount: Int)` sets target distortion through `ApertureAutoDefault.forAppCount(copiedAppCount)` and preserves all non-distortion copies as today. Search every caller before changing; update the only caller directly. Do not add a speculative overload.

---

## Task 1: Pure mapping (TDD)

**Files:** create `ApertureAutoDefault.kt`, create `ApertureAutoDefaultTest.kt`.

- [ ] **Step 1 — RED:** Write these unit behaviors (one test per boundary): 0 and negative -> 3.5; 24 -> 3.5; 25 -> 2.5; 99 -> 2.5; 100 -> 1.8; `Int.MAX_VALUE` -> 1.8.
- [ ] **Step 2 — Verify RED:** Run `./gradlew testDevDebugUnitTest --tests '*ApertureAutoDefaultTest'`; expect unresolved helper.
- [ ] **Step 3 — Implement:** Add named constants and one pure `when` expression. Clamp negative input to zero before mapping. No Context/Room/preferences.
- [ ] **Step 4 — Verify GREEN:** Rerun targeted test; all pass.
- [ ] **Step 5 — Commit:** `feat(lens): map copied app count to auto aperture (FISH-032)`.

## Task 2: Count exactly the copied rows (TDD)

**Files:** modify `AppPersistentDao.kt`, `LensWorkspace.kt`; extend `LensWorkspaceDaoTest.kt`.

- [ ] **Step 1 — RED:** Add a DAO test that inserts a source lens with 24 distinct rows and asserts `duplicateLensLayout(source, target) == 24` and target identifiers equal source identifiers. Add cases 0 and 25 rows (separate small tests or one parameterized loop without test framework changes).
- [ ] **Step 2 — Verify RED:** Run the targeted `LensWorkspaceDaoTest` class; expect compile failure because current DAO method returns `Unit`.
- [ ] **Step 3 — Implement:** Change `duplicateLensLayout` return type to `Int`; inside its existing transaction, bind `val sourceApps = getAllForLens(sourceLensId)`, copy each row to target as today, then return `sourceApps.size`. No extra count query, no `RAppsSingleton` count.
- [ ] **Step 4 — Verify GREEN:** Rerun `LensWorkspaceDaoTest`; assert return count and copied rows.
- [ ] **Step 5 — Commit:** `refactor(db): return copied layout row count for lens duplication (FISH-032)`.

## Task 3: Propagate created lens + copied count through existing API (TDD)

**Files:** modify `LensWorkspace.kt`; extend `LensWorkspaceDaoTest.kt`.

- [ ] **Step 1 — RED:** Add/adjust test for `createLensNow` that seeds 25 source rows and asserts `CreatedLens.lens` is the created lens and `copiedAppCount == 25`; verify actual target rows count 25. Add empty source case returns 0. Update existing direct `createLensNow` tests to unwrap `.lens` where they use `id`/fields.
- [ ] **Step 2 — Verify RED:** Run `./gradlew testDevDebugUnitTest --tests '*LensWorkspaceDaoTest'`; expected type/API compile errors and missing `copiedAppCount`.
- [ ] **Step 3 — Implement:** Add `CreatedLens(val lens: LensWorkspace, val copiedAppCount: Int)` next to `LensWorkspace`. `createLensNow` uses the `Int` from `duplicateLensLayout` and returns `CreatedLens`. Change async callback to `(List<LensWorkspace>, CreatedLens) -> Unit`; after IO result, fetch full current lens list exactly as today and deliver both. Only one production caller exists; update it in Task 5.
- [ ] **Step 4 — Verify GREEN:** Rerun `LensWorkspaceDaoTest`; all old create/delete/order tests remain green.
- [ ] **Step 5 — Commit:** `refactor(lens): return created lens metadata with copied row count (FISH-032)`.

## Task 4: Map count to distortion and preserve other settings (TDD)

**Files:** modify `UtilSettings.kt`; extend `UtilSettingsPerLensTest.kt`.

- [ ] **Step 1 — RED:** Add SharedPreferences-backed tests: source manually set to 4.2, target duplicated with count 10 -> target 3.5, source remains 4.2; counts 60 -> 2.5 and 140 -> 1.8; source profile/hood/icon size/scope/selection/hidden/frozen/Smart Focus/custom preset still copy; calling `saveDistortionFactor(target, 4.1f)` after duplication returns 4.1 and is never recomputed; an unrelated pre-existing lens remains byte/value unchanged.
- [ ] **Step 2 — Verify RED:** Run `./gradlew testDevDebugUnitTest --tests '*UtilSettingsPerLensTest'`; expect missing 3-argument signature / target receives source distortion.
- [ ] **Step 3 — Implement:** Add the `copiedAppCount` argument to `duplicateLensSettings`; set distortion to the pure mapper output; leave every other copy call intact and in the same order.
- [ ] **Step 4 — Verify GREEN:** Rerun `UtilSettingsPerLensTest`, plus `LensProfileSettingsTest`, `LensHoodSettingsTest`, `UtilSettingsIconSizeTest`, `UtilSettingsCustomPresetTest`, `UtilSettingsLensScopeTest`.
- [ ] **Step 5 — Commit:** `feat(settings): initialize duplicate lens distortion from app count (FISH-032)`.

## Task 5: Wire production Add lens flow to returned count (TDD)

**Files:** modify `LensMenuCoordinator.java`; extend `ActHomeLensManagementWidgetTest.kt`.

- [ ] **Step 1 — RED:** In the existing real-Activity Add lens widget test fixture, set default lens source distortion to 4.2 and create a copied lens. Wait for Room callback using the existing test pattern. Assert source stays 4.2, new lens equals mapping from its actual copied row count, and the two lens IDs differ. Test one source just below 25 and one at/above 100 via the fixture/DAO; if rendering 100 installed app rows is impractical, insert 100 `AppPersistent` rows for the source before clicking Add while preserving the real production callback path.
- [ ] **Step 2 — Verify RED:** Run `./gradlew assembleDevDebugAndroidTest`; expected Java callback signature error or assertion that target inherited 4.2.
- [ ] **Step 3 — Implement:** In `createLensDialog`, accept `(lenses, created)` callback; call `settings.duplicateLensSettings(copyFromLensId, created.lens.id, created.copiedAppCount)` once, then `adoptLenses(lenses)`. Remove the old before/after list-diff scan. No caller other than this method uses `LensWorkspace.createLens`.
- [ ] **Step 4 — Verify GREEN:** Run `ActHomeLensManagementWidgetTest` and `ActHomeMultiLensWidgetTest` on Pixel; assert persisted distortion via `UtilSettings.getDistortionFactor` and user override after save.
- [ ] **Step 5 — Commit:** `feat(lens): apply auto aperture when duplicating a lens (FISH-032)`.

## Task 6: Full gate, Pixel smoke, audit and closure

**Files:** update in-progress story, README, `feature.md`; create `doc/task/AUDIT_FISH-032_2026-11-03.md`.

- [ ] **Step 1 — Host gate:** Run exactly `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `bash scripts/check-secrets.sh`; record unit count and failures/skips plus APK SHA-256.
- [ ] **Step 2 — Lock Pixel:** Run `adb devices -l`. If no Pixel is attached, stop device actions and wait; never use another brand. If multiple Pixels, ask once via `AskUserQuestion`, lock one serial for the session. Record model, Android, display size/density, locale, font, HOME role, app locale, permissions, foreground, app/test APK hashes. Backup APKs and app data with `run-as tar` (exclude `./lib` symlink), SHA every member.
- [ ] **Step 3 — Targeted device regression:** Install using locked serial only. Run `LensShare...` is not relevant; run `ActHomeLensManagementWidgetTest`, `ActHomeMultiLensWidgetTest`, `LensViewProfileIntegrationTest`, `LensViewPinchIntegrationTest`, `WorkspaceBackupImportIntegrationTest`, each as a separate `adb -s <pixel> shell am instrument ... -e class ...` with hard timeout and exact per-class result.
- [ ] **Step 4 — Real smoke:** Use Home → lens menu → Add lens on copied source(s); verify value in the real Settings page and then Home. Verify one small-count band and large-count band with actual creation (the 25–99 band remains covered at unit/integration if producing 25+ real apps is impractical). Change target distortion via pinch or slider, return/relaunch, verify user override persists. Verify original lens unchanged. Never use `ActSettings` route. If ad/consent covers a screenshot, stop immediately and wait for owner. Record screenshots, elapsed time, APK hash.
- [ ] **Step 5 — Restore:** Reinstall original APK/test APK, restore app data, role, locale, font, permissions/system settings, compare all hashes. Stop if restore mismatch; report rather than claiming complete.
- [ ] **Step 6 — Mutation:** Mutate each threshold and output, replace mapper with source distortion, write to source instead of target, recompute at runtime; run with `--rerun-tasks` or fresh APK. Each needs a clear fail/pass verdict.
- [ ] **Step 7 — Audit and status:** Score honestly. Story moves to `done/` only if audit **>9.0/10**, gate and smoke pass, restore hashes match. Otherwise stay `inprogress/` and document remaining evidence. Commit local changes only; never stage `.idea/`; no push without current explicit authorization.

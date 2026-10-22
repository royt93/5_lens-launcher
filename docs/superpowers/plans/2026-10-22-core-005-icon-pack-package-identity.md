# CORE-005 Icon-Pack Package Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist icon-pack choice by stable packageName, migrate legacy localized labels without losing selections, and keep icon cache keys locale-stable.

**Architecture:** Add a new package identity preference and one migration resolver in `UtilSettings`/icon-pack catalog path. UI continues showing localized labels but persists packageName; app loading and cache token resolve by package identity. Missing packs fall back to system icons while retaining identity/pending legacy intent for later recovery.

**Tech Stack:** Kotlin, Java, Android SharedPreferences, PackageManager, JUnit4, Robolectric, Android instrumentation, Gradle.

## Global Constraints

- `KEY_ICON_PACK_LABEL_NAME` stays legacy migration input only; active selection is packageName.
- Default/system selection uses one named empty/default package identity constant, never a display label.
- Legacy label maps through currently installed icon-pack catalog; unresolved legacy selection is preserved and retried when catalog changes.
- Missing package renders system icons but retains selected identity for reinstall recovery.
- UI displays localized pack label; stored identity is packageName.
- `iconCacheKey` uses packageName and existing package version/update token, never display label.
- Keep icon-pack preference global, not per-lens; preserve current visual icon generation and fallback behavior.
- Branch `dev` only; no worktree. Locked device is Samsung S24 Ultra `R5CX613VZBR`, model `SM-S928B`, Android 17. Use only `adb -s R5CX613VZBR`; no Gradle `connected*` tasks.
- Preserve prefs/settings/DB/default launcher/locale; screenshot ad overlay means stop and wait for owner. Run `git diff --check` and `scripts/check-secrets.sh`; never stage `.idea`.

---

## File Map

- Modify `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`: new package identity key/default and migration result helper.
- Modify `app/src/main/java/com/mckimquyen/util/UtilApp.kt`: resolve packs and icon-pack cache token by packageName in full load and single icon reload.
- Modify `app/src/main/java/com/mckimquyen/ui/settings/SettingsDialogCoordinator.kt`: localized display entries paired with package identity values; persist packageName.
- Modify `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt`: display selected localized pack name and explicit unavailable-pack state where label preference is shown.
- Modify `app/src/main/java/com/mckimquyen/util/UtilIconPackManager.java` only if catalog needs a small packageName lookup helper; reuse existing `mPackageName` and catalog API otherwise.
- Modify/add tests: `app/src/test/java/com/mckimquyen/util/UtilSettingsPrefMigrationTest.kt`, `UtilSettingsIconPackIdentityTest.kt`, `UtilAppIconReloadTest.kt`, and cache-key tests in `BitmapCacheKeyTest.kt`.
- Modify existing `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsBackgroundWidgetTest.kt` for display/persist selection; update `UtilSettingsMigrationIntegrationTest.kt` only if migration depends on process recreation.
- Update story `doc/task/todo/p2-core-core-005-icon-pack-identity-by-package.md` → inprogress/done, `doc/task/README.md`, `doc/feature.md`, audit `doc/task/AUDIT_CORE-005_2026-10-22.md`.

### Task 1: RED — stable package identity and legacy label migration

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/util/UtilSettingsPrefMigrationTest.kt`
- Create or modify: `app/src/test/java/com/mckimquyen/util/UtilSettingsIconPackIdentityTest.kt`
- Read-only: `UtilSettings.kt`, `UtilIconPackManagerTest.kt`, `UtilSettingsMigrationIntegrationTest.kt`

**Interfaces:**
- Intended API: `UtilSettings.iconPackPackageName(iconPacks: List<UtilIconPackManager.IconPack>): String` resolves/migrates selected identity; or a small pure resolver `resolveIconPackPackageName(storedPackageName, legacyLabel, packs): Resolution`. Choose exact API only after inspecting Java/Kotlin nullability and existing `UtilSettings` style; do not create both.
- Resolution must distinguish `DEFAULT`, `AVAILABLE(packageName)`, and `UNAVAILABLE(packageName or pending legacy label)`; use a sealed type only if caller needs all three states, otherwise named result data class.

- [ ] **Step 1: Move story todo → inprogress.** Verify existing evidence and inspect every package-label reader/writer plus icon pack dialog entries.
- [ ] **Step 2: Write migration RED tests.** Legacy label matching installed pack (`Nova`) resolves to its packageName; new package key is written once; subsequent reads remain package-based after display label changes; system legacy sentinel resolves to default identity.
- [ ] **Step 3: Write missing-pack RED test.** Legacy label with no catalog match must return system-fallback state while preserving old label/pending selection; later catalog with matching label resolves and persists package identity.
- [ ] **Step 4: Write duplicate-label test.** Two packs with same display name cannot be uniquely migrated; retain pending legacy label and system fallback rather than arbitrarily choosing first.
- [ ] **Step 5: Run RED** with `./gradlew testDevDebugUnitTest --tests '*UtilSettingsPrefMigrationTest' --tests '*UtilSettingsIconPackIdentityTest'`; expected assertions fail on missing resolver/new preference behavior, not test setup.

### Task 2: GREEN — identity preference and migration resolver

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`
- Tests from Task 1.

**Interfaces:**
- Add named constants `KEY_ICON_PACK_PACKAGE_NAME` and `DEFAULT_ICON_PACK_PACKAGE_NAME` (empty string).
- Add one selected-package resolver that reads the new key first; if absent, maps legacy label only when exactly one installed pack matches; default sentinel maps to empty identity; ambiguous or unavailable label stays in legacy preference and returns empty/system fallback until later catalog recheck.
- Persist successful migration exactly once; don't erase legacy key until a package identity or confirmed default has been saved.

- [ ] **Step 1: Add the new constants and RED helper test** verifying default identity is the empty package constant and legacy key remains readable.
- [ ] **Step 2: Implement the minimal migration helper** following the exact RED cases, with explicit handling for absent key, default sentinel, unique label match, ambiguous match, unavailable label, and stored package key.
- [ ] **Step 3: Run focused unit tests GREEN.** Expected all migration/identity tests pass, especially retry mapping after a later installed catalog appears.
- [ ] **Step 4: Add cache-key RED test** in `BitmapCacheKeyTest`: same package/component/version/icon-pack package token yields identical key regardless of localized display labels (display labels are not parameters); changed pack package/version token changes key.
- [ ] **Step 5: Run the cache-key test GREEN** using existing `BitmapCache.buildKey`; don't alter it if it already ignores display names. Record if existing behavior already satisfies AC.

### Task 3: GREEN — app loading and reload use package identity

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilApp.kt`
- Tests: `UtilAppIconReloadTest.kt`, selected-package unit tests.

**Interfaces:**
- Full load resolves `selectedIconPack` by `pack.mPackageName == selectedPackageName`; compute existing `iconPackToken` from `mPackageName@versionToken` unchanged.
- Single-icon reload does the same by packageName; missing package returns default bitmap and keeps saved identity.

- [ ] **Step 1: Add failing tests** proving renamed label with same package still selects icon pack, package mismatch falls back to system icon, and missing selected package preserves stored identity while returning system icon.
- [ ] **Step 2: Replace both label comparisons/reads in `getAppShells` and `loadSingleAppIcon`** with the selected package resolver. Keep icon-pack `forceReload`/cache ownership behavior out of scope (that is PERF-006).
- [ ] **Step 3: Run `UtilAppIconReloadTest` and focused resolver tests**; expect no-pack/unknown package behavior unchanged and renamed display label no longer deactivates pack.
- [ ] **Step 4: Verify no `iconPackLabelName` or label equality remains in the app selection path** except explicitly legacy migration/display code.

### Task 4: GREEN — settings display label, persist package identity

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/settings/SettingsDialogCoordinator.kt`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt`
- Modify: `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsBackgroundWidgetTest.kt`

**Interfaces:**
- Dialog values array stores packageName; displayNames array stores localized label. Default entry pairs system label with `DEFAULT_ICON_PACK_PACKAGE_NAME`.
- Current selection index is looked up by packageName. On choice, persist package identity; invoke current callback with display label only if its contract is presentation-only. If callback currently expects stored identity, change it to packageName and update exact callers.
- Settings summary resolves packageName to current catalog label; missing package shows a localized unavailable-pack string only if existing localized strings already support it. Otherwise use existing neutral default display without adding new UI copy beyond scope; record missing-pack summary limitation.

- [ ] **Step 1: Write instrumentation/widget RED**: select fixture option where value differs from label; assert selected value persisted is packageName while visible text remains label. Add legacy migration display assertion.
- [ ] **Step 2: Update dialog lists** to parallel display/value lists with packageName values; preserve duplicate display labels by distinct package values.
- [ ] **Step 3: Update Settings summary lookup** to resolve selected package to current localized label and default/unavailable fallback.
- [ ] **Step 4: Run targeted settings instrumentation test on S24** after installing APK with explicit serial; verify no ad covers the interaction. If no visible UI code changes beyond selected label text, existing widget test applies; do not add duplicate tests.

### Task 5: Integration, locale regression, audit, and close

**Files:**
- Modify: story, README, feature tracker.
- Create: `doc/task/AUDIT_CORE-005_2026-10-22.md`.
- Existing integration tests: `UtilSettingsMigrationIntegrationTest.kt`, `FrmSettingsBackgroundWidgetTest.kt`, `LensViewIconReloadWidgetTest.kt`.

- [ ] **Step 1: Add locale/cache-key regression integration test**: resolve one pack by packageName, change only display label supplied by catalog (representing locale/update), assert selected package unchanged and `BitmapCache.buildKey` unchanged. Do not change system locale in test unless using a configuration context; if manual S24 locale switch is necessary, snapshot and restore in `finally` and honor ad stop rule.
- [ ] **Step 2: Run selected unit/instrumentation suites** on S24 Ultra only: migration, icon reload, settings selection, icon rendering.
- [ ] **Step 3: Measure fixed app-shell/key generation** before/after for locale label change; record query/identity evidence. Correctness is primary; do not claim performance gain unless measured.
- [ ] **Step 4: Run final full verification:** `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`; expected BUILD SUCCESSFUL, zero unit failures, no new lint errors.
- [ ] **Step 5: Run `git diff --check` and `scripts/check-secrets.sh`;** verify no display-label identity read remains outside migration/display UI and all old settings migrate safely.
- [ ] **Step 6: Compare S24 app prefs/system settings/DB/default launcher/locale** to baseline; restore test-owned state; report SDK-owned deltas separately.
- [ ] **Step 7: Update story and trackers**, describe Not applicable UI layers explicitly, write audit with RED/GREEN, migration cases, S24 build/device/time evidence, limitations and score. Move story to done only if score >9.0 and AC pass.
- [ ] **Step 8: Commit and push to `origin dev`** only after all gates. Stage exact files, never `.idea`; trailer exactly `Co-Authored-By: Claude Code <noreply@anthropic.com>`. If remote rejects, report and stop without changing credentials.

## Self-review

- Stable package identity and legacy unique-label migration: Tasks 1–2.
- Unavailable/ambiguous pack preservation and later recovery: Task 2.
- Full load, single reload, and locale-stable cache key: Tasks 3 and 5.
- Settings display vs stored package identity: Task 4.
- Device-only policy, tests, locale/state preservation, secret checks, audit and push gate: Task 5.
- No cache manager optimization is included; PERF-006 remains blocked until CORE-005 is complete.

# CORE-005 — Icon-pack identity by package

**Date:** 2026-10-22

**Story:** `doc/task/todo/p2-core-core-005-icon-pack-identity-by-package.md`

**Status:** Owner-approved design; implementation pending

## Goal and scope

Persist icon-pack selection by stable package identity instead of mutable/localized display label. Migrating an existing installation must preserve the user’s choice when it can be mapped. Changing app locale must not change the selected pack or icon cache identity.

## Design

- Add a dedicated preference key whose value is the selected icon-pack `packageName`; keep `KEY_ICON_PACK_LABEL_NAME` as legacy migration input only, not active identity.
- Default/system selection is represented explicitly by an empty/default package identity (use one named constant); do not overload a display label as identity.
- On first read of the new key, if absent, read the legacy display label and resolve against installed `UtilIconPackManager` catalog. Persist the matched packageName once and mark migration complete. If the legacy label cannot map because pack is absent, retain it as a pending legacy label for later resolution; do not silently erase the user's choice. While unavailable, render system icons and show a clear unavailable selection state in settings. When package catalog changes, retry mapping pending label against installed packs; only commit the package identity once matched.
- Settings dialog displays current localized pack label but stores packageName as choice. Selected pack lookup in `UtilApp.getAppShells` and `loadSingleAppIcon` uses packageName equality. Resolve current display label from current catalog for UI only.
- `iconCacheKey` derives its icon-pack token from packageName plus the existing package version/update token, never from display label. Locale-only label changes therefore do not alter icon cache keys; package update still changes version token and invalidates as today.
- Do not modify or broaden per-lens semantics. Existing global icon-pack preference remains global.

## Tests

- Migration test: legacy label matching an installed pack writes its packageName and returns same selected pack; repeat read does not remigrate.
- Missing-pack test: legacy label with no installed match uses system icon but preserves pending label/choice; installing a matching pack later maps to its package identity without user re-selection.
- Identity test: packs with duplicate display labels remain distinguishable by packageName; selected package lookup is stable if label changes.
- Cache-key test: same packageName/version with changed localized display label produces identical `iconCacheKey`; a changed package version still changes key.
- Settings selection test: dialog displays localized name, persists packageName, and selecting default clears identity explicitly.
- Existing icon-pack and app reload tests verify default/system fallback and custom icon output.

## Verification

- Run `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`.
- Device target is locked Samsung S24 Ultra `R5CX613VZBR` (SM-S928B, Android 17); only use `adb -s R5CX613VZBR`, no Gradle `connected*` tasks.
- Verify locale switch does not rewrite identity/cache key; restore locale and all test-owned prefs. Do not change default launcher or DB.
- Run `git diff --check`, `scripts/check-secrets.sh`; never stage `.idea`.
- Audit migration and missing-pack behavior explicitly; commit/push only after required tests and score gate. If push fails, report exact rejection; never alter credentials or use another identity.

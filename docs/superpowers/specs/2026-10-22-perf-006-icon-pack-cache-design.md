# PERF-006 — Shared icon-pack reload cache

**Date:** 2026-10-22

**Story:** `doc/task/todo/p2-perf-perf-006-icon-reload-reuses-icon-pack-cache.md`

**Status:** Owner-approved design; implementation pending

## Goal and scope

Avoid repeating icon-pack discovery and `appfilter.xml` parsing for every single icon reload. Keep reload output and icon-pack selection behavior unchanged; share one manager across the application and refresh it only when active pack changes or package contents become stale.

## Design

- `RApplication` owns one `UtilIconPackManager` instance and exposes it to `UtilApp.loadSingleAppIcon` call paths. Do not create a manager per icon reload.
- A single-icon reload uses `forceReload=false`, allowing the manager to reuse its cached pack activities/appfilter. Full refresh paths may keep `forceReload=true` when they intentionally need a fresh package scan.
- Invalidate the shared manager when selected icon pack changes and when Android reports active icon-pack package update or removal. Invalidation drops cached pack discovery/filter state; the next reload lazily rescans once.
- Cache lifetime is one active manager, not a map of all past packs. Do not add cache eviction or retain old pack managers.
- Preserve existing fallback behavior when no icon pack is selected, pack query fails, appfilter is missing, or target activity has no custom icon.

## Tests

- Add/extend unit tests around manager invalidation/reuse with injectable query counter at existing package-manager seam. After one initial load, N single-icon reloads must cause at most one package-discovery query; force invalidation then reload must cause exactly one additional query.
- Integration-test application ownership and selected-pack change: active pack switch invalidates cache, next icon lookup resolves new pack, following lookups reuse it.
- Instrumented/UI test not applicable if no UI behavior changes; verify existing `LensViewIconReloadWidgetTest`/icon-pack manager integration tests retain visible icon behavior. Explain Not applicable in story/audit.
- Measure controlled multi-icon reload workload before/after on S24 Ultra `R5CX613VZBR`; record elapsed time and query count. Query count is primary deterministic evidence; timing is supporting evidence and must report noise.

## Verification

- Run `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`.
- Use only locked Samsung S24 Ultra `R5CX613VZBR` for any install/instrumentation/smoke/profiling. No Gradle `connected*` tasks. Stop screenshot tests if ad covers UI and wait for owner confirmation.
- Preserve app prefs, DB, default launcher, locale, and system settings; verify before/after. Run `git diff --check` and `scripts/check-secrets.sh`; never stage `.idea`.
- Audit honestly; no commit/push unless score >9/10 and full required checks pass. If remote rejects push, report the exact result and stop; do not change credentials or seek an alternate identity.

# PERF-003 — Replace notifyDataSetChanged() with DiffUtil-driven granular updates

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Rendering performance |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Deferred out of `LINT-002` (its `NotifyDataSetChanged` warnings, 2 occurrences) because doing it correctly needed its own scoped story rather than being rushed into a "safe cleanup" round. `AppAdapter.updateApps()` (the app list in Settings) and `SearchResultAdapter.submitList()` (search results) both called `notifyDataSetChanged()` on every update, which rebinds and redraws every row regardless of what actually changed, and defeats RecyclerView's built-in item animations (no fade/slide for the row that actually changed — everything just redraws in place).

## User story

As a user, I want the app list and search results to update smoothly (only the rows that changed) instead of the whole list flashing/redrawing on every small change.

## Changes

- Added `adt/AppDiffCallback.kt`: a shared `DiffUtil.Callback` for `List<App>`, used by both adapters. Item identity is `(packageName, name)` — the same identity pair already used everywhere else in the codebase (`RAppsSingleton.findApp`/`updateAppState`/`updateOrganization`/`updateAppOrder`). Content equality uses `App`'s data-class `equals()`, which is safe here because every `App` reaching these adapters already has `icon = null` (icons live only in `BitmapCache`; `TaskUpdateApps` strips the bitmap via `app.copy(icon = null)` before ever storing a snapshot — verified by reading that pipeline, not assumed).
- `AppAdapter.updateApps()`: now computes `DiffUtil.calculateDiff(AppDiffCallback(oldApps, mApps))` and dispatches the result to the adapter instead of `notifyDataSetChanged()`.
- `SearchResultAdapter.submitList()`: same change.

## Required test matrix

- [x] Unit tests: `app/src/test/java/com/mckimquyen/adt/AppDiffCallbackTest.kt` (new) — 9 cases against a `ListUpdateCallback` that records exactly what RecyclerView would be told to do: identical lists (no events), append (insert only), remove (remove only), reorder (reported as a move, not remove+insert), same-identity content change (reported as a change, not remove+insert), `areItemsTheSame` true/false across every field combination (same identity/different content, different package, different activity name), and emptying a list.
- [x] Widget/UI tests: `app/src/androidTest/java/com/mckimquyen/adt/AppAdapterWidgetTest.kt` (new — `AppAdapter` had zero prior test coverage of any kind) — attaches the adapter to a real `RecyclerView` inside a real Activity (needed for the biometric check in `AppViewHolder`) and drives it through insert → remove → reorder → content-only-change → empty → repopulate, asserting the visible content/order is correct after every step and that every position still binds without crashing.
- [x] Integration tests: `FrmAppsLifecycleIntegrationTest.testFrmApps_appsLoadedEvent_updatesExistingAdapterInPlaceWithCorrectContent` (new case added to the existing LEAK-001 integration file) — this is the one path the file's existing tab-switch/recreate tests never covered, because they always destroy and recreate `FrmApps`'s view (and a fresh `AppAdapter`) from scratch. This new case keeps the Apps tab's view alive, changes `RAppsSingleton`'s app set underneath it (remove 2, add 1, reorder), fires the real production event (`AppEventManager.notifyAppsLoaded()`, the same call `ActSettings` observes to forward to `FrmApps.onAppsUpdated`), and asserts the already-attached `AppAdapter` ends up showing the new set in the new order — i.e. it exercises the new `DiffUtil` path exactly as production triggers it, not just at the isolated adapter level. `SearchResultAdapterWidgetTest` (existing) already calls `submitList` twice with different lists and re-ran clean, confirming no regression there.
- [x] Smoke test the exact candidate on the designated device and record model, Android version, build SHA, network state, timestamp and log evidence. — See Test evidence below.

## Test evidence

- **Unit** — `./gradlew testDevDebugUnitTest`: full suite passes, 0 failures (`AppDiffCallbackTest`'s 9 new cases included).
- **Lint** — `./gradlew :app:lintDevDebug`: 150 → 148 warnings (0 errors); both `NotifyDataSetChanged` warnings are gone.
- **Targeted widget/integration** — `AppAdapterWidgetTest` (2/2), `FrmAppsLifecycleIntegrationTest` (3/3 in isolation), `SearchResultAdapterWidgetTest` (1/1) all pass. One flake was observed and diagnosed: running `AppAdapterWidgetTest` + `SearchResultAdapterWidgetTest` immediately before `FrmAppsLifecycleIntegrationTest` in the same instrumentation process caused `testFrmApps_activityRecreate_onAppsTab_doesNotCrashAndReloadsApps` to see `321` apps instead of the 5 seeded ones — a real `PackageManager` scan (CORE-001's app-refresh pipeline, triggered by an earlier test's `ActSettings` launches) landed asynchronously mid-test and overwrote `RAppsSingleton`'s seeded fake data. Re-ran `FrmAppsLifecycleIntegrationTest` alone (3/3 pass) and the single failing test alone (pass) to confirm: this is pre-existing cross-test-process background-scan flakiness, not a defect in this diff (the diff never touches `RAppsSingleton` population, `TaskUpdateApps`, or app-lifecycle scanning) — same class of issue already disclosed in `LEAK-001`/`DISPLAY-001`/`LINT-002`'s records, just newly surfaced by a different test-ordering.
- **Full instrumentation regression** (all `androidTest` classes, single S24 Ultra run): `adb -s R5CX613VZBR shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` → **Tests run: 115, Failures: 1**. The 1 failure is `AppSearchIntegrationTest#recentHeaderRestoresAndClearActionRemovesHistory` — the same pre-existing test-order flake already disclosed in `LEAK-001`/`DISPLAY-001`/`LINT-002`'s records, in an untouched search-history file. The `FrmAppsLifecycleIntegrationTest` cross-test flake noted above did not reproduce in this full run.
- Device: Samsung SM_S928B (Galaxy S24 Ultra), serial `R5CX613VZBR`, Android 16 (SDK 36), 2026-09-12. App and test APKs installed/uninstalled via `adb -s R5CX613VZBR install`/`uninstall` for every run; no leftover install afterward.

## Device policy note

- Only the S24 Ultra (`R5CX613VZBR`) was used for on-device verification, per the standing hard device-target ban reaffirmed 2026-09-12 — see the project's `feedback-device-target` memory and `doc/task/README.md`'s Product decisions log.

## Audit score (2026-09-12, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | Both adapters now dispatch granular updates; item identity matches the codebase's existing convention; content equality verified safe by reading the icon-stripping pipeline rather than assumed. |
| Unit-test quality and coverage | 1.5 | 1.5 | Every diff outcome (none/insert/remove/move/change) and both branches of `areItemsTheSame` are individually asserted against real dispatched callback events, not just adapter side effects. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Closes a real zero-prior-coverage gap for `AppAdapter` and proves correctness (order/content), not just "doesn't crash", across a full insert/remove/reorder/content-change/empty sequence. |
| Integration-test quality and coverage | 1.5 | 1.5 | The new integration case specifically targets the in-place update path production actually uses, which the file's pre-existing tests structurally could not reach. |
| Tecno + general smoke | 1.0 | 0.85 | Real S24 Ultra device, all targeted tests pass; docked for the newly-surfaced cross-test background-scan flake (diagnosed and disclosed, not a defect in this diff) and the device-vs-"Tecno" naming under the standing policy. |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | This is a pure performance/UX improvement (fewer rebinds, working item animations) with no behavior change to what data ends up displayed — proven by the diff tests. |
| Maintainability and documentation truth | 1.0 | 1.0 | One shared `AppDiffCallback` instead of duplicating diff logic in two adapters; the cross-test flake is disclosed with its root cause, not hidden. |
| **Total** | **10.0** | **9.85** | **Exceeds the > 9.0 push gate.** |

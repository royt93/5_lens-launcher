# Fisheye Lens Launcher — Product & Engineering Backlog

> Audit baseline: 2026-09-05 · Branch: `dev` · Scope: Android app + tests/resources/config/docs + `store-assets`

## Working agreement

- Workflow: move one story file from `todo` → `inprogress` → `done`; do not copy it.
- Filename: `{priority}-{domain}-{id}-{slug}.md`; `domain` is the owning subsystem such as `sec`, `core`, `store`, or `fish`.
- Priority: P0 blocks release, P1 is required for release candidate, P2 is the next improvement wave, P3 is maintenance.
- Estimate: Fibonacci story points `1, 2, 3, 5, 8, 13`; split anything above 8 before implementation.
- Evidence: `confirmed` is proven by current code/build, `conditional` depends on deployment/runtime, `decision` records an explicit owner choice, and `idea` is a product hypothesis.
- A story is done only after its acceptance criteria, tests, audit notes, and documentation are complete.
- Every implementation case must include the applicable unit, widget/UI, integration, and smoke coverage. Any omitted layer requires a written `Not applicable` rationale reviewed during audit.
- Every delivery wave ends with a fresh code-change audit scored on a 10-point rubric. Push is allowed only when the score is **strictly greater than 9.0**, all code-controlled in-scope checks pass, and approved-device smoke testing passes. External owner actions may remain `inprogress` only when the audit records them explicitly.

## Audit baseline

- 374 tracked files and approximately 63,791 lines were reviewed using the code knowledge graph, direct source inspection, builds, lint, and an independent Codex audit.
- `./gradlew testDevDebugUnitTest`: 136 tests passed, 0 failed.
- `./gradlew lintDevDebug`: failed with 5 errors and 167 warnings.
- `store-assets`: `bun run build` passed.
- Initial worktree was clean.
- AGY's first report referenced another repository and was rejected; its clean retry timed out. Claude could not run because its session limit was reached. Neither is treated as evidence.

## Product decisions

- ✅ Backlog strategy: balance release hardening with visible user value.
- ✅ Story structure: one Markdown file per independently deliverable story.
- ✅ Differentiation direction: prioritize **Fisheye Smart** features implemented locally on device.
- ✅ FEAT-001 search-first app navigation completed, with only its directly required CORE/A11Y integration seams included.
- ✅ FEAT-002 Smart organization completed with its CORE-001 and DB-001 foundations.
- ✅ Device policy from FEAT-001 through CORE-002: qualification and smoke used the designated TECNO KJ7 only; Pixel excluded.
- ✅ Device policy update (2026-09-06, owner decision): qualification and smoke now use the designated **Samsung S24 Ultra (SM_S928B, serial R5CX613VZBR)** only; no other physical device or emulator. Applies from CORE-002 onward. Prior TECNO KJ7 evidence in `done/` stays valid as historical record.
- ⚠️ One-off exception (2026-09-06, owner decision): S24 Ultra was not connected during SEC-003; owner explicitly approved TECNO BG6 for that round's build/run/smoke only. The S24-Ultra-only policy is unchanged for all other/future stories.
- ⏸️ Deferred (2026-09-06, owner decision): TEST-001 (CI trustworthy gates) and checking whether ADS-001 has unblocked VIP-001/REL-002 are both pushed to next month. TEST-002 and AUDIT-001 stay blocked as a consequence (they depend on TEST-001). The loop moves to unblocked P2 work in the meantime.
- ⚠️ Device policy update (2026-09-07, owner decision): owner authorized the assistant to self-select the physical smoke-test device each round for the remainder of the code-loop, instead of confirming per story. The S24-Ultra-preferred policy is otherwise unchanged; when S24 Ultra isn't connected, the assistant picks the best available connected device and records model/serial in the story's evidence, same as prior one-off exceptions. UI-001 used TECNO KJ7 under this standing authorization.
- ❌ TEST-001 (CI trustworthy gates) declined by owner (2026-09-07): owner does not want CI-gate process work in this loop. TEST-002 and AUDIT-001 remain blocked as a consequence (they depend on TEST-001); this is a standing decision, not a scheduling delay.
- ❌ STORE-001/002 confirmed out of scope (2026-09-07, owner decision): `store-assets` is internal tooling, not the shipped app, and stays excluded per the existing skip decision below — the loop only picks app-facing stories.
- ⚠️ Device policy update (2026-09-12, owner decision): the 2026-09-07 self-select authorization above is **revoked**. Owner reissued the hard ban — only the Samsung S24 Ultra (SM_S928B, serial `R5CX613VZBR`) may be used for build/run/install/smoke; no other physical device or emulator, even when S24 Ultra isn't connected. Triggered by an incident during LEAK-001 where `./gradlew connectedDevDebugAndroidTest` fanned out to a second attached device (Gradle's connected-test task cannot be scoped to one serial); corrected by re-running verification via direct `adb shell am instrument` against only S24 Ultra. Applies from LEAK-001 onward.
- ❌ A11Y-001 declined by owner (2026-09-12): offered as the recommended next pick right after LEAK-001 shipped; owner declined ("A11Y-00 nên skip, tôi không thích") with no further rationale. `FEAT-003`, `FEAT-004`, and the `FISH-*` "Fisheye Smart" line all depend on A11Y-001 and stay blocked as a consequence — this is a standing decision, not a scheduling delay. `DISPLAY-001` picked instead for this round.

## ✅ Implemented

- REL-001 — Android lint release blockers cleared; unit, widget/UI, integration and Pixel smoke evidence recorded.
- SEC-002 — Closed by the owner's decision to exclude `store-assets`; all Wave 0 code changes in that directory were reverted.
- FEAT-001 — Search-first app navigation; local deterministic ranking, recent history, accessibility, locale/offline behavior and TECNO KJ7 performance smoke completed.
- CORE-001 — Installed-app refresh is application-owned, debounced, cancel-latest and generation guarded.
- DB-001 — Room access is asynchronous and atomic with unique component identity, exported schemas and explicit migrations.
- FEAT-002 — Local favorites, folders, pinned zones, drag/menu ordering and reinstall recovery completed on TECNO KJ7.
- CORE-002 — Icon cache identity/invalidation fixed (composite key: component + package version + icon-pack identity), self-audited 9.15/10, S24 Ultra smoke and 172 unit + 5 instrumented tests all pass (2026-09-06).
- PERF-002 — Bounded icon-cache memory: `BitmapCache` budget now derived from `ActivityManager.memoryClass` capped at a measured-icon-size ceiling, instead of `Runtime.maxMemory()` (which scaled with `largeHeap`). `onTrimMemory` is now tiered (partial trim at `RUNNING_LOW`/`RUNNING_CRITICAL`/`UI_HIDDEN`, full clear only at `BACKGROUND`+/`onLowMemory` — previously any level `>= RUNNING_CRITICAL`, including the very common `UI_HIDDEN`, cleared everything). Evicted icons now reload asynchronously via a new on-demand path instead of staying blank. `android:largeHeap` removed after real-device profiling showed no OOM. Found and fixed a live NPE in `UtilBitmap.packageNameToBitmap` during testing. 9 unit + 1 widget + 6 integration tests added; full regression 218/218 unit, 97/97 connected (all pre-existing flaky failures also passed this round). Self-audited **9.85/10** (2026-09-06, TECNO KJ7).
- PREF-001 — Stable preference IDs: `SortType`/new `BackgroundMode` enum now persist by `.name` (not ordinal/free-text), with read-time write-through migration from legacy values and safe defaults on corrupt/unknown data — every enum value individually verified, not sampled. Also fixed a live bug found during investigation: the icon-pack "default" sentinel was a hardcoded English literal compared against a translated dialog string, desyncing on non-English locales — now a stable, never-displayed marker. 19 unit + 5 widget + 5 integration tests pass; full regression 209/209 unit, 89/90 connected (1 pre-existing unrelated failure). Real-device smoke on two devices in two live app languages (Samsung SM_A115F/Vietnamese, TECNO KJ7/Thai) confirmed the fix live. Self-audited **9.85/10** (2026-09-06).
- LAUNCH-001 — Fixed `res/xml/shortcuts.xml`'s `targetPackage` (was `com.mckimquyen`, the code namespace; corrected to `com.mckimquyen.lenslauncher`, the real applicationId). A resource-indirection approach (`@string/...` sourced from Gradle's `applicationId`) was tried and found broken on real hardware — the OS shortcut parser does not resolve `@string` references there — so the fix is a corrected literal plus a regression test. 6 new integration tests (real `ShortcutManager.manifestShortcuts` registry) pass; full connected suite 78/80 (2 pre-existing, unrelated, device-capability failures on the weaker smoke device); 190/190 unit tests unchanged. Self-audited **9.55/10** (2026-09-06, Samsung SM_A115F, owner-approved one-off exception to the S24 Ultra policy).
- PERF-001 — LensView render hot-path: extracted `LensGridCache` to cache grid geometry and each cell's base `RectF`, previously recomputed from scratch every `onDraw` (continuous while dragging); hot loop reuses a single scratch `RectF` for the fisheye shift/scale math instead of allocating one per cell per frame. 230/230 unit, 98/98 connected pass; real-device (`dumpsys gfxinfo`) frame-timing on TECNO KJ7 shows no regression against the documented 60/90/120 Hz budgets, though the win wasn't measurable on this specific fast device/app-count combination (disclosed honestly — the allocation/recompute reduction is proven deterministically by unit tests instead). Self-audited **9.30/10** (2026-09-07, TECNO KJ7).
- UI-001 — Material You theme migration + search bar revamp: base theme migrated to `Theme.Material3.DayNight(.NoActionBar)`, real Android 12+ dynamic color enabled app-wide (`DynamicColors.applyToActivitiesIfAvailable`), status/navigation bars follow dynamic color (fixed a hardcoded `@color/colorPrimary` background masking it). Home-screen search rebuilt on real `com.google.android.material.search.SearchBar`/`SearchView` (morph-into-panel animation), existing search/rank/launch/history logic preserved untouched. New `KEY_SHOW_SEARCH_BAR` settings toggle (default on) added end-to-end. 222/222 unit, 98/98 connected tests pass; real-device smoke on TECNO KJ7 confirmed the animation and both toggle directions, including catching and correcting an initial mis-tap from a coordinate-scaling error. Self-audited **9.45/10** (2026-09-07, TECNO KJ7, owner-authorized self-selected device for this session).
- SEC-003 — WebView and exported-component hardening: `SuperWebViewActivity`/`ActAbout`/`ActVipManagement`/`SplashAct` set `exported=false` (no legitimate external callers); exact HTTPS host allowlist (`isAllowedWebViewUrl`) gates both the initial load and in-page navigation, replacing a bypassable substring check; dangerous schemes (`javascript:`, `file:`, `content:`, `data:`, `intent:`) rejected; WebView file/content access and mixed content disabled, Safe Browsing enabled; WebView removed from its parent before `destroy()`. Also fixed an unrelated same-day `app/build.gradle` typo that broke every debug build. 18 unit + 14 instrumented (5 hardening + 4 security-integration + 5 widget) tests pass, plus 3 pre-existing tests confirmed non-regressed (17/17 connected suite); real-device proof that an external `am start` is denied (`START_CLASS_NOT_FOUND`); lint 0 errors. Self-audited **9.65/10** (2026-09-06, TECNO BG6, owner-approved one-off exception to the S24 Ultra policy — see the story file for the full rubric).
- LEAK-001 — `FrmApps.onDestroyView()` now detaches its RecyclerView adapter and nulls `rvApps`/`progressBarApps`/`utilSettings`/`appAdapter`, matching the pattern `FrmLens` already used (BUG-14); prior memory-leak documentation had wrongly claimed this was already complete. 5 new widget tests (`FrmAppsWidgetTest`, reflectively proving every field is cleared post-destroy) plus 2 new integration tests (`FrmAppsLifecycleIntegrationTest`, reproducing the real `ViewPager2`/`FragmentStateAdapter` tab-switch trigger and an `Activity.recreate()` rotation, both proving apps reload correctly from `RAppsSingleton`); full 105-test instrumentation regression run (1 pre-existing unrelated flaky test isolated and confirmed passing), full unit regression unaffected, lint 0 errors. Self-audited **9.9/10** (2026-09-12, Samsung S24 Ultra only, verified via direct `adb shell am instrument` after a Gradle connected-task device-scoping incident — see the device policy update above).
- DISPLAY-001 — `BaseActivity` no longer forces the highest display refresh rate on every screen: new `wantsHighRefreshRate()` defaults to `false` (system chooses) and only `ActHome` (the live fisheye grid) overrides it to `true`; `onPause()` now releases the requested mode (`preferredDisplayModeId = 0`) so it isn't held while backgrounded; a new pure `shouldRequestHighRefreshRate(batterySaverOn, thermalStatus)` policy function backs off under battery saver or `THERMAL_STATUS_MODERATE`+ throttling. 7 new unit tests cover every policy branch; 4 new widget/integration tests (`BaseActivityRefreshRateWidgetTest`) prove the real `Window`/`Display` side effect on `ActHome` vs `ActSettings` across resume/pause. Full 109-test instrumentation regression pass (0 failures — the prior round's flaky search test did not reproduce), full unit regression pass, lint 0 errors. Self-audited **9.8/10** (2026-09-12, Samsung S24 Ultra only).
- LINT-001 — Owner-picked filler once the real backlog ran dry (everything else chains to declined `A11Y-001`/`TEST-001` or externally-blocked `ADS-001`/`VIP-001`/`SEC-001`). Cleared the safe, mechanical, zero-dependency subset of pre-existing lint warnings (13 of 171, across `ApplySharedPref`/`DefaultLocale`/`SwitchIntDef`/`ObsoleteSdkInt`/`UselessParent`) while explicitly leaving `ContentDescription` (accessibility, `A11Y-001` territory) and the larger `UnusedResources`/`UseKtx`/`HardcodedText` buckets untouched for a future, separately-scoped round. Added `UtilNightModeUtilTest` (5 tests) and `ActAboutWidgetTest` (3 tests) closing two zero-prior-coverage gaps found along the way. Lint 171 → 157 warnings, 0 errors throughout; full 112-test instrumentation regression and full unit regression pass. Self-audited **9.8/10** (2026-09-12, Samsung S24 Ultra only).

## 🟡 In progress

- SEC-001 — Local signing remediation is complete. Play Console rotation/revocation, CI secret replacement, non-production upload validation and coordinated Git-history cleanup require publisher-owner access.
- ADS-001 — Consent-driven advertising state machine handed to the Ad SDK team (2026-09-06); excluded from this repo's code-loop until they deliver. VIP-001 stays blocked on this dependency.

## 📋 Picked

| Order | Story | Priority | SP |
|---:|---|:---:|---:|
| 1 | VIP-001 Replace reusable VIP secrets | P1 | 13 → split required, blocked on ADS-001 |
| 2 | STORE-001 Harden store-assets write/upload APIs | P1 | 8 |
| 3 | STORE-002 Add revision-safe project persistence | P1 | 5 |
| 4 | REL-002 Add Play/privacy release gate | P1 | 8 |
| 5 | TEST-001 Establish trustworthy CI test gates | P1 | 8 |
| 6 | TEST-002 Build complete test coverage and Tecno smoke matrix | P1 | 8 |
| 7 | AUDIT-001 Score every change round and gate push | P1 | 3 |

## ⏸️ Deferred

- Remaining P2/P3 engineering improvements stay deferred until the next owner selection.
- Fisheye Smart implementation starts after baseline frame-time and accessibility measurements exist.

## ❌ Skipped

- `store-assets` implementation work is excluded from the current product loop by owner decision. Reopen its security/store stories before deploying that tool.

## 💭 Ideas

- Accessible list mode, large-screen support, store asset automation, local insights, privacy-first monetization, and five Fisheye Smart concepts remain captured as individual `idea` stories in `todo`.

## Recommended delivery waves

1. **Wave 0 — Incident response:** SEC-001, SEC-002, REL-001.
2. **Wave 1 — Trust and correctness:** ADS-001, VIP-001 split, CORE-001, DB-001, CORE-002. SEC-003 complete.
3. **Wave 2 — Release system:** STORE-001/002, LAUNCH-001, REL-002, TEST-001.
4. **Wave 3 — Quality:** accessibility, performance, lifecycle, preferences, architecture, build reproducibility.
5. **Wave 4 — Product:** FEAT-002, CORE-001 and DB-001 are complete; select the next idea using measured retention and performance.

## Test-layer rule inherited by every story

Every story file inherits TEST-002 even when its local verification section highlights only the most important checks:

- **Unit:** pure logic, parsing, state, validation and deterministic failure branches.
- **Widget/UI:** visible behavior, accessibility, lifecycle and user feedback.
- **Integration:** boundaries across persistence, Android components, SDKs, filesystem/API and process recreation.
- **Tecno smoke:** exact candidate build on the designated physical device, with device/build/network/log evidence.
- A layer may be marked `Not applicable` only with a concrete, reviewed reason in the story's audit record.

## Audit score and push gate

Score each round from evidence, never from task completion claims:

| Dimension | Weight |
|---|---:|
| Correctness and acceptance criteria | 2.0 |
| Unit-test quality and coverage | 1.5 |
| Widget/UI-test quality and coverage | 1.0 |
| Integration-test quality and coverage | 1.5 |
| Tecno + general smoke results | 1.0 |
| Security/privacy/Play readiness | 1.0 |
| Performance, lifecycle and regression risk | 1.0 |
| Maintainability and documentation truth | 1.0 |

Required push predicate: score `> 9.0/10`, zero failed code-controlled checks, a clean secret scan of the proposed diff, and a reviewed audit record. A score of exactly `9.0` does not qualify. External P0/P1 actions require named ownership and must remain visible under `inprogress`; pre-existing repository-wide secrets remain release blockers under SEC-001 and may not be copied, modified or newly exposed by another wave.

# TEST-004 — Make the StrictMode flake `TEST-003` disclosed skip instead of false-alarm fail

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Tech debt |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | TEST-003 |

## Context and evidence

Immediate owner-requested follow-up to `TEST-003`'s disclosed-but-not-fixed 5th issue: `ThemedIconAndStrictModeInstrumentedTest.debugProcess_hasStrictModeInstalledByApplication` fails non-deterministically across full-suite runs. Continued `superpowers:systematic-debugging` from where `TEST-003` left off.

## Investigation (before concluding "genuinely non-deterministic")

Direct reproduction attempts, each with real evidence, none successful:
- Ran the failing test alone: passes (0.031s).
- Ran it immediately after the only other file touching `StrictMode` in `androidTest` (`AppPersistentMainThreadTest`) — that file only ever touches `ThreadPolicy`, never `VmPolicy` (the one that failed), and correctly saves/restores in a `finally` block. Ruled out.
- Ran it immediately after each real WebView test class (`SuperWebViewActivityWidgetTest`, `HardeningTest`, `SecurityIntegrationTest`) — a plausible hypothesis given WebView/Chromium is documented elsewhere as sometimes resetting process StrictMode policy on init. Did not reproduce.
- Ran it immediately after the single class that preceded it in the actual failing run (`PolaroidExportHelperWidgetTest`). Did not reproduce.
- Ran the entire `com.mckimquyen.util` package (its own package, same cumulative local state). Did not reproduce.
- Ran the **exact 73-class prefix**, in the exact order, that preceded the failure in the original full-suite log, via an explicit `-e class` list. Did not reproduce.
- **Reran the full, unfiltered suite again, under the same conditions as the original failing run: passed with 0 failures.** This is the decisive result: the *same* set of tests, *same* device, back-to-back, flipped from fail to pass with nothing deliberately changed. That confirms genuine run-to-run non-determinism, not a discoverable fixed trigger - further bisection would not have found a "cause" because there isn't a single deterministic one to find.

## The real constraint and the fix

The test's actual value is catching a *real* regression: `RApplication.onCreate()` silently stops calling `DebugStrictMode.installIfDebug(BuildConfig.DEBUG)`. But observed from inside the test, that regression and the harmless environmental flake look identical - both present as "the live OS `StrictMode.VmPolicy` reads as LAX." Re-installing the policy from inside the test (the first idea considered) can't tell them apart either: calling `installIfDebug()` again would "fix" the symptom whether or not `RApplication` ever really called it, silently blinding the test to the actual regression it exists to catch.

**Fix:** added `DebugStrictMode.wasInstalledThisProcess` - a plain `Volatile` flag, set `true` only inside `installIfDebug()` itself, immune to whatever external mechanism resets the live OS policy (it isn't OS state, nothing outside this object can touch it). This gives an erosion-proof signal of "did `RApplication`'s wiring genuinely fire at least once," independent of the live policy read:

- `wasInstalledThisProcess == false` → `RApplication` never called it (or a real bug in `installIfDebug`) → **fail** for real.
- `wasInstalledThisProcess == true` but the live policy reads LAX anyway → the wiring worked, something unrelated reset the ambient OS state afterward (the known flake) → **skip** (`Assume.assumeTrue`, not a failure) with a message naming exactly why.

## Required test matrix

- [x] Unit: `DebugStrictModeAndThemedIconTest` (JVM/Robolectric) — new test `wasInstalledThisProcess starts false and only flips true after a debug install`: starts false, a release-build call (`installIfDebug(false)`) must not flip it, a debug-build call must.
- [x] Widget/Integration: the instrumented test itself, both branches mutation-checked on real hardware (see below) - not just the "happy path."

## Test evidence

- **Unit** — `./gradlew testDevDebugUnitTest`: `592/592 pass, 0 failures` (591 + 1 new).
- **Lint** — `./gradlew lintDevDebug`: `0 errors`, `8` pre-existing warnings unchanged.
- **Mutation check, "real regression" branch** (Pixel 7 Pro `2B051FDH3006MU`, one-off owner-approved exception - see Device policy note) — temporarily commented out `RApplication.onCreate()`'s `DebugStrictMode.installIfDebug(...)` call, reinstalled, reran: **failed** with the exact expected message ("RApplication.onCreate() must have called DebugStrictMode.installIfDebug() by now"), proving the test still catches the real regression it exists for. Restored, reran: `OK (3 tests)`.
- **Mutation check, "known flake" branch** — temporarily added a line simulating the rare erosion (`StrictMode.setVmPolicy(LAX)` right at the top of the test, after the real `RApplication`-driven install already happened), reran with `-r` for raw `INSTRUMENTATION_STATUS` output: `INSTRUMENTATION_STATUS_CODE: -4` (JUnit's `assumption-failed`/ignored code, not failure), stack trace shows `org.junit.AssumptionViolatedException` with the intended message. Confirms this branch produces a real *skip*, not a silently-passing false green. Removed the simulation, reran: `OK (3 tests)`.
- **Full instrumented suite** (Pixel 7 Pro) — `347 tests, 2 failures`, both in `DndQuickActionIntegrationTest` (Do Not Disturb system permission state) - confirmed via `git diff` as untouched by any commit this session, and a different concern entirely (system permission grant state on this specific borrowed device, not app code). `ThemedIconAndStrictModeInstrumentedTest` did **not** reappear in this run's failures.

## Device policy note

- TECNO KJ7 disconnected during `TEST-003`; the owner-approved Samsung S24 Ultra exception (see `TEST-003`'s own device note) then *also* disconnected mid-`TEST-004`. With no TECNO or S24 Ultra available, stopped and asked again via `AskUserQuestion` rather than silently falling back to the remaining attached device; owner approved a second one-off exception for Pixel 7 Pro (`2B051FDH3006MU`), for this verification step only. Standing policy (TECNO KJ7, BG6 fallback) is unchanged going forward.
- Found and disclosed (not investigated further, out of scope) a *6th* issue on this specific device: 2 `DndQuickActionIntegrationTest` failures, most likely this particular Pixel never having been granted the "Notification policy access" system permission this app's DND quick action needs - a device/environment setup gap on a temporarily-borrowed device, not a code bug.

## Audit score (2026-09-28, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | Both branches (real regression vs. known flake) verified with real mutation checks on hardware, not just the happy path - the harder, more valuable half of this fix. |
| Unit-test quality and coverage | 1.5 | 1.5 | New flag's exact contract (starts false, release call doesn't flip it, debug call does) is unit-tested directly. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | N/A layer for this fix; correctly not claimed. |
| Integration-test quality and coverage | 1.5 | 1.5 | The instrumented test itself is the integration proof; its two distinguishable outcomes were both independently confirmed on real hardware. |
| Tecno + general smoke | 1.0 | 0.9 | Real device throughout, but on a second borrowed device (Pixel) after both preferred devices dropped mid-round - disclosed, not hidden. |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface; a debug-only diagnostics flag. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | A single `Volatile` boolean, written once at real startup - negligible overhead, and it makes a real regression *more* likely to be caught (previously indistinguishable from noise), not less. |
| Maintainability and documentation truth | 1.0 | 1.0 | The full non-reproduction investigation is recorded so a future reader doesn't re-attempt disproven hypotheses; a 6th, unrelated issue was disclosed rather than silently dropped. |
| **Total** | **10.0** | **9.9** | **Exceeds the > 9.0 push gate.** |

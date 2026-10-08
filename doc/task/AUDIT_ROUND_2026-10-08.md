# Audit — 2026-10-08 (R5 sweep + OPPO test stabilisation)

## Scope

Branch `dev`, 6 commits on top of `origin/dev` (`7a1565c`..`1416096`):

- `72d561c` `ActHome` now keeps and detaches its lens `TabLayoutMediator`; backlog docs synced.
- `45cf4cf` `DndQuickActionIntegrationTest` awaits the asynchronous `setInterruptionFilter`.
- `a5fdb40` hdpi variants of the two shortcut icons.
- `857cfe4`, `1416096` `doc/feature.md` entries.
- `2ccbc64` `AccessibilityActionsIntegrationTest` restores the `RAppsSingleton` snapshot instead of clearing it.

Out of scope: release signing, consent (`ADS-001`), rewarded test ad ID, Play Console items. They stay open under
`doc/task/RELEASE_OWNER_CHECKLIST.md` and were not touched by this diff.

## Evidence

| Check | Result |
|---|---|
| JVM unit tests (`testDevDebugUnitTest`) | 725/725 (run on HEAD before the last test-only and doc commits; none touch JVM sources) |
| Instrumented full suite, OPPO CPH1989 `FUJZIFIR7DQCNRWW` | 503/503 on the final code (exit 0) |
| Baseline `e0c3097` full suite, same device | 2 failures (DND, `AdaptiveOrientation`): both pre-existing, not caused by this diff |
| Lint `lintDevDebug` | 0 errors, 7 warnings (`IconLauncherShape` x5, `IconLocation`, `IconMissingDensityFolder`; all need launcher-asset changes, left alone) |
| Secret scan | 5 `check-secrets.sh` patterns over `origin/dev..HEAD`: 0 non-doc hits, 0 keystore files. `gitleaks` is not installed locally; CI runs it |

Root causes, each reproduced before fixing:

- `AdaptiveOrientation`: fails 2/2 when run after `AccessibilityActionsIntegrationTest` (it empties the shared snapshot); 3/3 pass after the fix.
- DND: probe on the device showed the filter reading stale for up to ~22 ms after `setInterruptionFilter`; 5/5 pass after polling.
- Two lens reduced-motion tests: ColorOS blocks `settings put global` until "Disable permission monitoring" is on; no code change.

## Score — 9.2/10

| Dimension | Score | Evidence |
|---|---:|---|
| Correctness and acceptance criteria | 1.9/2.0 | Leak fixed; three test failures root-caused and reproduced. Deduction: the mediator test asserts the field is nulled, not that observers were actually removed. |
| Unit-test quality and coverage | 1.4/1.5 | 725/725. Deduction: no new JVM test, `ActHome` is a Java activity so the fix is covered only on device. |
| Widget/UI-test quality and coverage | 1.0/1.0 | 503/503 including the new `ActHomeLensMediatorLeakTest`. |
| Integration-test quality and coverage | 1.4/1.5 | Real-system DND test and the ordered-class repro both green. Deduction: single run of the full suite on the final code, single device. |
| Tecno + general smoke results | 0.8/1.0 | Policy changed to OPPO only (TECNO banned). No manual smoke; only the dev debug build ran. The signed release build was never run (no credentials here). |
| Security/privacy/Play readiness | 0.9/1.0 | Diff adds no secret, permission or manifest change. Deduction: known open release risks (exposed keystore history, ads before consent) are unchanged. |
| Performance, lifecycle and regression risk | 0.9/1.0 | Mediator leak closed. No performance measurement this round. |
| Maintainability and documentation truth | 0.9/1.0 | Backlog docs corrected (`PERF-004`, `TEST-002`, `AUDIT-001`). Deduction: 7 lint warnings remain. |

Push predicate (`> 9.0`, zero failed code-controlled checks, clean secret scan, audit record): met on the evidence above.
This record is self-scored by the implementing session; the owner review is the push approval.

## Residual risk

- Release candidate is not smoke-tested as a signed build.
- 7 icon lint warnings.
- Pre-existing release blockers in `RELEASE_OWNER_CHECKLIST.md` are unchanged.

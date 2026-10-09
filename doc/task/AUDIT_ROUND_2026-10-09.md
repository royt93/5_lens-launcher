# Audit — 2026-10-09 (FISH-021: per-lens app scope, freeze order, quick lens switch)

## Scope

Branch `dev`, commits on top of `origin/dev` (`420bd17`) up to `d98a1d9`: spec, plan, Tasks 1-8, the Task 9 fixes, FISH-021 docs. About 46 files, +2.2k lines. Nine tasks, each committed on its own, each with unit, widget and integration tests.

Out of scope and untouched: release signing, consent (`ADS-001`), the rewarded test ad ID, Play Console items. `doc/task/RELEASE_OWNER_CHECKLIST.md` is unchanged and still blocks a release.

## Evidence

| Check | Result |
|---|---|
| JVM unit tests | 770/770 (run on the final code) |
| Instrumented full suite, TECNO KJ7 `115333744A005844` (API 34) | 558/558 (run 4, after the last code and test change) |
| Earlier full-suite runs, same device | 547 tests with 2 red (root-caused, not pre-existing); 549/549; 552/552 |
| Lens test clusters, repeated | 12-16 classes, 5-8 repetitions each, no flake after the fixes below |
| Lint `lintDevDebug` | 0 errors, 7 warnings (5x `IconLauncherShape`, `IconLocation`, `IconMissingDensityFolder`; all pre-existing launcher-asset findings) |
| Secret scan | 5 `check-secrets.sh` patterns over `origin/dev..HEAD`: 0 non-doc hits, 0 keystore files, 0 `.idea` files (one stray `.idea/modules.xml` from `02e3f91` was untracked in `d98a1d9`). `gitleaks` is not installed locally; CI runs it |
| Backlog validator | `validate_backlog.py`: 34 errors, identical to `origin/dev` (0 from FISH-021) |
| Device smoke | TECNO KJ7, fresh app data, screenshots read; results in the done file |

## What the tests missed, and how it was found

The app-checklist dialog read each row's tick back from its `ListView`, which only knows bound rows. On a real 67-app device, unticking 7 rows and pressing OK saved 2 apps instead of 60. The full suite was green (549/549) when this was found, because both dialog tests used 2 apps. It surfaced only in a manual smoke on the device. The fix keeps the choice in a `Set` updated by the dialog's callback, with a 60-app test that was red before and green after, and the older tests now tap rows like a user.

Other defects found by running things rather than reading them, each reproduced before being fixed:
- Commit `02e3f91` shipped tests without the `ActHome` code, so HEAD did not compile its own test until `51c39c6`.
- Four test-isolation leaks (default lens left renamed; `active_lens_id` left pointing at a deleted lens; a cleanup that targeted a lens with no data; `switchLens()` committing the cached real app list over seeded apps). Each was bisected to a cause, none was waved away as flaky.
- A lint hint (`ReportShortcutUsage`) from the new shortcut code, fixed with `LensShortcuts.reportUsed`.

## Score — 9.1/10 (was 8.9 before the follow-up tests)

| Dimension | Score | Evidence |
|---|---:|---|
| Correctness and acceptance criteria | 1.7/2.0 | All criteria met and device-verified. Deduction: a data-loss bug reached HEAD and was caught only by manual smoke, after 549 green tests. |
| Unit-test quality and coverage | 1.4/1.5 | 770/770, every pure rule has its own test. Deduction: `showLensAppsDialog` and `toggleFreezeForLens` logic lives in Java activity code, so it is covered on device only. |
| Widget/UI-test quality and coverage | 1.0/1.0 | Dialog, freeze, switcher, scope and menu covered, including the TalkBack `ACTION_LONG_CLICK` path for "Remove from this lens" (`LensRemoveFromLensAccessibilityIntegrationTest`, 4 cases). Mutating the visibility rule turned it red, then the file was restored. |
| Integration-test quality and coverage | 1.5/1.5 | DAO, event flow, recreate, shortcuts, multi-lens independence, and freezing/unfreezing a `SELECTED` lens (order numbers only for the apps it shows; selection kept). The shortcut tap through a real launcher is still only covered through the intent it carries, noted under smoke. |
| Device smoke and general smoke | 0.8/1.0 | One device (API 34), one run, screenshots read and numbers cross-checked (61 chosen = 61 icons = 61 DB rows). Deduction: shortcuts not seen on the launcher UI; no second device; the release build was not smoked. |
| Security, privacy, Play readiness | 0.9/1.0 | No new permission, manifest entry, secret or network call; strings are translated in 16 locales. Deduction: the known release blockers (exposed keystore history, ads before consent) are unchanged. |
| Performance, lifecycle, regression risk | 0.9/1.0 | Dialog and popup released in `onDestroy`; scope survives `recreate()` and rotation. Deduction: no frame-time measurement, and the prefetch-flicker risk from the spec was reasoned about, not measured. |
| Maintainability and documentation truth | 0.9/1.0 | Spec, plan, done file, feature and README entries, memory updated. Deduction: the 7 lint warnings remain; the plan's code drifted from the final code in several places (`LensShortcuts`, the dialog). |

**Push predicate (`> 9.0`): met on paper at 9.1, with a caveat.** 9.1 clears the number, but it is a self-score, only 0.1 above the line, and the weakest dimension (device smoke, 0.8/1.0) did not improve: still one device, one run, shortcuts not seen on the launcher. The two follow-up test additions moved +0.2 and nothing else did. The owner should read this as a narrow pass, not a comfortable one.

Why not higher: the checklist bug shows the test suite was weaker than its green count suggested, and the smoke that caught it covered one device once. Both are real, so I did not round up. The earlier 8.9 became 9.1 only through the two test gaps that could be closed without another device.

## What would add margin

1. Smoke the same flows on a second device (the owner's OPPO or Pixel, if allowed) and confirm the launcher actually lists the lens shortcuts: about +0.3. Not done: no second device was authorised for this run.
2. Done in this round: the TalkBack test and the freeze-a-`SELECTED`-lens tests (+0.2).

## Residual risk

- Shortcuts are registered with the system but not seen on the TECNO launcher UI (the launcher's long-press menu could not be opened by script).
- Pre-existing release blockers in `RELEASE_OWNER_CHECKLIST.md` are unchanged.
- The self-scored audit has the same limit as the previous one: it is the implementing session grading itself; the owner's review is the push approval.

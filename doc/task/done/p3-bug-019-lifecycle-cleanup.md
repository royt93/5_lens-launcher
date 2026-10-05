# BUG-019 — Lifecycle cleanup for LensView, FrmLens and ActSettings

| Field | Value |
|---|---|
| Type | bugfix |
| Status | done |
| Priority | P3 |
| Evidence | confirmed (TDD + TECNO KJ7) |
| Date | 2026-10-05 |

## Owner decision

Owner selected the lifecycle-cleanup option before the three larger stories deferred to November 2026. Scope expanded from the two review findings in `LensView` and `FrmLens` to the related callback/mediator cleanup in `ActSettings`. Design: `docs/superpowers/specs/2026-10-05-bug-019-lifecycle-cleanup-design.md`. Plan: `docs/superpowers/plans/2026-10-05-bug-019-lifecycle-cleanup.md`.

## Acceptance criteria

- [x] `LensView.onDetachedFromWindow()` resets touch coordinates, gesture state, selection, moving/long-press and hover-haptic state.
- [x] Detaching `LensView` clears the real framework accessibility delegate; re-attaching reinstalls the helper normally.
- [x] `resetToIdleForExport()` reuses the same complete touch-state reset.
- [x] `FrmLens.onDefaultsReset()` invalidates the preview lens after settings and slider values are restored.
- [x] `ActSettings` retains and releases its `TabLayoutMediator` and page callback, then clears the ViewPager2 adapter on destroy.
- [x] Unit, widget, integration, lint and full instrumented verification on the locked device.
- [ ] Independent whole-branch audit >9.0 (push gate pending).

## TDD evidence

### Task 1 — LensView (`95dab8a`)

- RED: `LensViewReattachIntegrationTest` ran 9 tests with exactly the two new cleanup tests failing; the seven existing tests passed.
- GREEN: 9/9. Related regression runs: `com.mckimquyen.views` 103/103, `com.mckimquyen.a11y` 11/11, `PolaroidExportHelperWidgetTest` 4/4.
- `ViewCompat.setAccessibilityDelegate(view, null)` was tested and found not to clear a delegate previously installed through AndroidX: it substitutes an empty compat delegate. The implementation correctly uses framework `View.setAccessibilityDelegate(null)` (available below minSdk 25) and the test proves `ViewCompat.hasAccessibilityDelegate(view) == false` after detach.

### Task 2 — FrmLens (`c16303d`)

- RED: `onDefaultsReset_triggersInvalidateOnPreviewLens` failed because the preview was not dirtied.
- GREEN: focused test 1/1 and full `FrmLensWidgetTest` 8/8.

### Task 3 — ActSettings (`fd53548`)

- RED: after Activity destroy, ViewPager2 adapter remained non-null.
- GREEN: focused test 1/1, `ActSettingsArchitectureWidgetTest` 4/4, `ActSettingsArchitectureIntegrationTest` 3/3.

## Final verification

Device: **TECNO KJ7 `115333744A005844`** only.

- JVM: **722/722**, 0 skipped/failures/errors.
- Android Lint: **0 errors / 8 warnings**; all eight are existing icon-asset warnings.
- Full instrumentation: **505/505 OK** after sequential build, install and run.

The first full-suite attempt ran JVM/lint and APK build/install concurrently against the same Gradle output tree. The installed app APK then lacked `com.mckimquyen.app.RApplication`; instrumentation crashed before test 1 with `ClassNotFoundException`. No code change was made. A sequential rebuild → install → full instrumentation run passed 505/505, confirming build-output contention rather than a BUG-019 regression.

## Reviews and disclosed minor gaps

- Task 1: spec PASS, quality APPROVED, no Critical/Important. Minor: reflection on private `mSelectIndex` makes the test rename-sensitive but avoids adding a production-only setter.
- Task 2: spec PASS, quality APPROVED, no findings.
- Task 3: spec PASS, quality APPROVED, no Critical/Important. Minor: its test proves adapter and retained fields become null, but does not mutation-pin the internal `detach()` and `unregisterOnPageChangeCallback()` calls independently.
- Final whole-branch audit score: pending.

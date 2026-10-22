# LEAK-002 — LensView stale index and listener cleanup

**Date:** 2026-10-22

**Story:** `doc/task/todo/p2-leak-leak-002-lensview-select-index-and-listeners.md`

**Status:** Owner-approved design; implementation pending

## Goal and scope

Prevent stale selected indices from crashing `LensView` if its app list shrinks during a hide animation, and release listener lambdas that can retain `ActHome` after the view detaches. Preserve current gesture and animation behavior; make no unrelated lifecycle changes.

## Design

- In `launchApp`, resolve selected app through `getOrNull(mSelectIndex)` and return when no current app exists. Keep existing `PackageManager` guard, launch bounds, and launch behavior for a valid app.
- In `drawAppName`, resolve the current selected app safely; skip label drawing if selection is absent or no longer valid. Do not mutate touch/animation state from draw code.
- In `onDetachedFromWindow`, null the three Activity-capturing listeners in addition to the three existing test/haptic listeners: `onCurvatureAdjustedListener`, `onEmptySpaceLongPressListener`, and `onSearchSwipeDownListener`. Keep `onEdgeSwipeListener`, `onHapticPerformed`, and `vibratorProvider` cleanup. Preserve `onAttachedToWindow` behavior: Activity owners rebind their callbacks as they already do.
- Remove `shouldAllowAppLaunch` helper and its call at the end of ACTION_UP. Pinch and pinch-release already return from earlier branches, so this guard currently receives `IDLE` unconditionally and has no remaining effect. Tests for pinch non-launch remain in place.
- Do not cancel or alter hide animation when app list changes. If the selected app no longer exists at completion, skip launch; this matches chosen minimal scope.

## Tests

- Add an integration regression test that creates a real attached `LensView` with a valid selected app, starts hide animation, shrinks/replaces the app list before animation completion, advances the animation to completion, and proves no crash/no stale component launch. Establish RED against baseline.
- Extend lifecycle coverage to assign all six callbacks, detach the view, and verify every callback field is null. Reattach and verify callbacks remain unset until owner rebinding; no callback may retain the prior Activity.
- Run existing pinch integration tests to prove pinch-release still cannot launch and ordinary valid launch behavior remains unchanged.
- Unit test not applicable for list indexing and view/window lifecycle; behavior belongs in Android integration tests. Robolectric-only checks cannot verify actual window detach/animation callback ordering.

## Verification

- Run `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`.
- On locked Samsung S24 Ultra `R5CX613VZBR` (SM-S928B, Android 17), run targeted instrumentation tests using `adb -s R5CX613VZBR`; never target Pixel or other devices. Stop screenshot testing if an ad covers UI and wait for owner confirmation.
- Compare app prefs and system settings before/after; keep default launcher/locale unchanged. No Gradle `connected*` tasks.
- Run `git diff --check` and `scripts/check-secrets.sh`; audit with honest score and limits before commit/push. Do not stage `.idea`.

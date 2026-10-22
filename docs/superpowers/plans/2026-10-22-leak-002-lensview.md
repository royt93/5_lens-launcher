# LEAK-002 LensView Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent stale app selection crashes and release Activity-capturing listeners when `LensView` detaches.

**Architecture:** Keep fixes in `LensView`. Resolve current app by index at the last moment in drawing/launch, and null all six callback properties in detach. Preserve animation behavior; remove the dead launch guard only after testing pinching remains non-launching.

**Tech Stack:** Kotlin, Android View/Animation, JUnit4, AndroidX Test `ActivityScenario`, instrumentation runner, Gradle.

## Global Constraints

- Do not alter hide-animation behavior: when selected app no longer exists, skip launch rather than cancelling animation.
- Use `getOrNull(mSelectIndex)` in `launchApp` and `drawAppName`.
- Null `onCurvatureAdjustedListener`, `onEmptySpaceLongPressListener`, and `onSearchSwipeDownListener` on detach; retain cleanup for `onHapticPerformed`, `onEdgeSwipeListener`, and `vibratorProvider`.
- Remove `shouldAllowAppLaunch` and its call only after confirming early pinch/pinch-release returns keep existing no-launch behavior.
- Locked device for entire session: Samsung S24 Ultra `R5CX613VZBR`, model SM-S928B, Android 17. Use `adb -s R5CX613VZBR` only. Do not run Gradle `connected*` tasks.
- Preserve prefs, settings, DB, default launcher, and locale. If an ad overlays UI during screenshot testing, stop and wait for owner confirmation.
- Do not stage `.idea`; run `git diff --check` and `scripts/check-secrets.sh`.

---

## File Map

- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt`: safe app lookup, detach callback cleanup, dead guard removal.
- Create `app/src/androidTest/java/com/mckimquyen/views/LensViewStaleSelectionIntegrationTest.kt`: real-window hide-animation/list-shrink regression.
- Modify `app/src/androidTest/java/com/mckimquyen/views/LensViewReattachIntegrationTest.kt`: callback cleanup and reattach assertions.
- Modify `app/src/androidTest/java/com/mckimquyen/views/LensViewPinchIntegrationTest.kt` to remove the unused `appLaunched` local and assert observable pinch terminal state; inspect branch proof as specified in Task 2.
- Move story `doc/task/todo/p2-leak-leak-002-lensview-select-index-and-listeners.md` to `inprogress/`, then to `done/` after all checks.
- Update `doc/task/README.md`, `doc/feature.md`, and create `doc/task/AUDIT_LEAK-002_2026-10-22.md` at close.

### Task 1: RED — reproduce stale index after hide animation

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/views/LensViewStaleSelectionIntegrationTest.kt`
- Read-only patterns: `LensViewReattachIntegrationTest.kt`, `LensViewDepthOfFieldIntegrationTest.kt`, `LensViewPinchIntegrationTest.kt`

**Interfaces:**
- Use actual `ActivityScenario` window, attach `LensView` as a child, `setApps(ArrayList<App>)`, real `MotionEvent` dispatch, and `startAnimation` through touch dispatch.
- Use `ActivityScenario.onActivity` for all view/animation mutations; use main looper instrumentation or `waitFor` loop to observe animation end. No production test hook.

- [ ] **Step 1: Move story todo → inprogress.** Review full patterns for real window attach/detach, view measure/layout, and existing MotionEvent construction.
- [ ] **Step 2: Write test first.** Use `ActivityScenario.launch(ActHome::class.java)` and the existing `withAttachedContainer`-style real-window host from `LensViewReattachIntegrationTest`. Create one `LensView`, `setPackageManager(null)`, `setApps(arrayListOf(App(label = "Selected", packageName = "com.perf.leak", name = "Main")))`, attach and draw; obtain selected cell with `getAppBounds(0, rect)`. Dispatch `ACTION_DOWN` at `rect.exactCenterX/Y`, then `ACTION_MOVE` vertically by `2 * ViewConfiguration.get(context).scaledTouchSlop` to start pan/show animation. Wait on main thread until private `mAnimationMultiplier == 1f` using a 2-second monotonic deadline and 16ms sleeps. Dispatch `ACTION_UP` at moved coordinates; immediately call `setApps(arrayListOf())` before hide animation ends. Poll until `mAnimationHiding == false` and `mTouchX == -Float.MAX_VALUE` with a 2-second deadline; assert completion and no uncaught test failure. Read private fields only via reflection in this test; do not add production seams. Empty list triggers stale `drawAppName` during hide on baseline, and the animation end also reaches `launchApp`.
- [ ] **Step 3: Run the new test against baseline.** Build instrumentation APK with `./gradlew assembleDevDebugAndroidTest`, install only with `adb -s R5CX613VZBR install -r <apk>`, then run with `adb -s R5CX613VZBR shell am instrument -w -e class com.mckimquyen.views.LensViewStaleSelectionIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`. Expected RED: `IndexOutOfBoundsException` during label draw or hide animation end. RED may identify either direct indexing site. If setup does not reach either site, fix the test setup before production edits.
- [ ] **Step 4: Record exact RED output** in working notes; don't weaken assertions to make it pass.

### Task 2: GREEN — safe app lookup and remove dead launch guard

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/views/LensViewStaleSelectionIntegrationTest.kt`
- Regression: `app/src/androidTest/java/com/mckimquyen/views/LensViewPinchIntegrationTest.kt`

**Interfaces:**
- `launchApp()` keeps return type `Unit`, obtains `val app = mApps?.getOrNull(mSelectIndex) ?: return`, and uses `app` for the existing `UtilApp.launchComponent` arguments.
- `drawAppName(canvas, rect)` obtains `val app = mApps?.getOrNull(mSelectIndex) ?: return`, then draws `app.label`; no direct `list[mSelectIndex]` access remains.

- [ ] **Step 1: Verify pinch non-launch from existing code path** before editing. `LensViewPinchIntegrationTest.testPinchLiftEntersPinchReleaseAndDoesNotLaunchApp` ends in `PINCH_RELEASE`, then `ACTION_UP` returns from the explicit pinch branch before normal launch code. Remove the unused `appLaunched` local from that test and assert only observable behavior: final state is `IDLE`, `selectedIndexForTest == -1`, and no live curvature remains pending without user action. Do not claim this proves a launch spy; document branch-level proof. No new production launch seam.
- [ ] **Step 2: Implement only the safe lookups** using exact code above. Preserve current `mPackageManager` condition and launch bounds.
- [ ] **Step 3: Run targeted stale-index instrumentation test.** Expected GREEN, no `IndexOutOfBoundsException`; valid selected app still follows existing launch path.
- [ ] **Step 4: Delete `shouldAllowAppLaunch` helper and call** after tests confirm ACTION_POINTER_DOWN/UP pinch paths return before ACTION_UP launch handling. Keep the explicit early returns for PINCHING/PINCH_RELEASE.
- [ ] **Step 5: Run `LensViewPinchIntegrationTest` and `LensViewWidgetTest`** on S24 Ultra. Expected all pass; pinch release stays idle/no launch branch, ordinary valid tap path remains unchanged.

### Task 3: GREEN — null all six listeners on detach

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Modify: `app/src/androidTest/java/com/mckimquyen/views/LensViewReattachIntegrationTest.kt`

**Interfaces:**
- Existing public/internal callback properties stay unchanged:
  - `onHapticPerformed: ((Int) -> Unit)?`
  - `onEdgeSwipeListener: OnEdgeSwipeListener?`
  - `vibratorProvider: (() -> Vibrator?)?`
  - `onCurvatureAdjustedListener: OnCurvatureAdjustedListener?`
  - `onEmptySpaceLongPressListener: OnEmptySpaceLongPressListener?`
  - `onSearchSwipeDownListener: OnSearchSwipeDownListener?`

- [ ] **Step 1: Add a test to `LensViewReattachIntegrationTest`** that assigns all six callbacks before actual `host.removeView(view)`. Construct `OnCurvatureAdjustedListener { _, _ -> }`; set `onEmptySpaceLongPressListener = OnEmptySpaceLongPressListener { _, _ -> }`; set `onSearchSwipeDownListener = OnSearchSwipeDownListener { }`; set `onEdgeSwipeListener = OnEdgeSwipeListener { }`; set `onHapticPerformed = { }`; set `vibratorProvider = { null }`. After `host.removeView(view)`, assert each property null. Reattach same view and assert all six remain null until explicit owner rebinding. Wrap removal in `try/finally` cleanup; no callback may retain the test Activity after detach.
- [ ] **Step 2: Run this test before implementation.** Expected RED only for `onCurvatureAdjustedListener`, `onEmptySpaceLongPressListener`, and `onSearchSwipeDownListener`; the other three callback assertions pass.
- [ ] **Step 3: Add three null assignments** alongside the existing `onHapticPerformed`, `onEdgeSwipeListener`, and `vibratorProvider` cleanup in `onDetachedFromWindow`. Do not add helpers or change attach behavior.
- [ ] **Step 4: Run the new test and full `LensViewReattachIntegrationTest`.** Expected GREEN, all existing reattach behavior preserved.

### Task 4: Full verification, S24 smoke, audit, and close

**Files:**
- Move story `inprogress/` → `done/`.
- Modify `doc/task/README.md`, `doc/feature.md`.
- Create `doc/task/AUDIT_LEAK-002_2026-10-22.md`.

- [ ] **Step 1: Capture state baseline** for S24 Ultra using only `adb -s R5CX613VZBR`: model/build/Android, relevant launcher SharedPreferences and settings. Don't change default launcher, locale, DB, or animation/font setting outside DeviceStateGuard.
- [ ] **Step 2: Run full verification:** `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`. Expected `BUILD SUCCESSFUL`, all unit tests pass, no new lint errors. Do not call `connected*` tasks.
- [ ] **Step 3: Install and run only on S24 Ultra.** Install both APKs with `adb -s R5CX613VZBR install -r ...`; run targeted class(es) with `adb -s R5CX613VZBR shell am instrument -w -e class <fully.qualified.TestClass> com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`. If screenshot shows an ad covering UI, stop and wait for owner `done` before continuing.
- [ ] **Step 4: Smoke S24**: open test flow showing lens, perform a normal tap/pan and a hide animation while fixture list shrinks, verify no crash and ordinary installed-app tap still behaves as before; record evidence. Do not dismiss ads or accept consent.
- [ ] **Step 5: Compare device state after run.** Restore any changed user-facing settings and test prefs; verify default launcher and locale remain unchanged. Report SDK-owned changes separately, never modify SDK tokens.
- [ ] **Step 6: Run `git diff --check` and `scripts/check-secrets.sh`;** review lifecycle code for callbacks, handlers, animation and observer cleanup.
- [ ] **Step 7: Complete story and audit.** Record RED/GREEN, unit/widget/integration applicability, device model/Android/build/timestamp/logs, state diff, limitations, and score. Move story to done only after every AC passes and score > 9.0.
- [ ] **Step 8: Commit and push only after final verification.** Stage only intended paths, never `.idea`; commit message ends with `Co-Authored-By: Claude Code <noreply@anthropic.com>`. Push `origin dev` only if permission allows; if GitHub returns 403, report local commit and stop without changing authentication or attempting alternate credentials.

## Self-review

- Stale-index safety covered in Task 2; test uses real window and hide animation.
- Listener cleanup and detach/reattach ownership covered in Task 3.
- Dead guard removal and pinch behavior covered in Task 2.
- Spec explicitly says no animation semantic changes; plan honors that boundary.
- Device lock, no `connected*`, ad stop rule, state preservation, audit score gate, secret scan, and `.idea` exclusion covered in Task 4.

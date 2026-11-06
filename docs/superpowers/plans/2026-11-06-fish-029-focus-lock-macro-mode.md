# FISH-029 — Focus Lock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Let users pin fisheye focus centre with double-tap on empty grid space, then release with a second double-tap, preserving icon tap and existing gesture behavior.

**Architecture:** Add a pure tap-pairing policy and session-only focus state in `LensView`; pinned focus affects only the visual warp centre, never touch/hit-test state. Add one host-level `ExploreByTouchHelper` action for this story's TalkBack exception, and clear pinned/pending state on pause/detach.

**Tech Stack:** Kotlin, Android `MotionEvent`/`ViewConfiguration`/`Handler`, AndroidX `ExploreByTouchHelper`, JUnit4/Robolectric and AndroidJUnit4. No new dependency.

## Global Constraints

- TECNO KJ7 `115333744A005844` only; never use Pixel, Samsung or OPPO. No Gradle `connected*` tasks.
- Back up main/test APKs and app data, hash every data file, and record HOME role/locale/font/animation scales before device work. Restore and compare after.
- Do not tap “Start (Ads may appear)”. If ad/consent covers required UI, follow owner-approved policy; do not interact with ad content.
- Narrow TalkBack exception: FISH-029 may add only one `Toggle focus lock` action. A11Y-002/A11Y-003 remain declined; do not implement general accessibility actions.
- No persistence, Settings toggle, dependency, app-tap delay, nearest-icon snapping or lock-screen behavior. Focus state must not affect hit-test, launch, haptics, hover selection, DoF or accessibility bounds.
- Test first for every behavior change: RED → verify expected failure → minimum GREEN → rerun. Do not stage `.idea/`.

---

## Files and Ownership

- Create `app/src/main/java/com/mckimquyen/util/FocusLockTapPolicy.kt`: pure validation/pairing for empty-space taps.
- Create `app/src/test/java/com/mckimquyen/util/FocusLockTapPolicyTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt`: pending tap, pinned focus, visual warp selection, testing seam and lifecycle reset.
- Modify `app/src/main/java/com/mckimquyen/views/LensAccessibilityHelper.kt`: host-level action and pinned/unpinned state announcement; do not attach duplicate custom actions to each virtual app node.
- Modify `app/src/main/java/com/mckimquyen/ui/ActHome.java`: clear focus-lock state on pause, in addition to `LensView` detach/reset.
- Modify `app/src/main/res/values/ids.xml` only if a stable custom accessibility-action ID is needed; add action/state strings to base `values/strings.xml` plus exactly 16 locale folders `values-ar/de/es/fr/hi/in/it/ja/km/ko/lo/pt/ru/th/vi/zh`.
- Extend `app/src/test/java/com/mckimquyen/views/LensViewGestureStateTest.kt` and `app/src/test/java/com/mckimquyen/a11y/LensAccessibilityHelperTest.kt`.
- Create `app/src/androidTest/java/com/mckimquyen/views/LensViewFocusLockIntegrationTest.kt`; extend `LensViewReattachIntegrationTest.kt` only if its existing attachment/lifecycle setup can prove the Activity pause contract without broader fixture changes.
- Final evidence: `doc/task/AUDIT_FISH-029_2026-11-06.md`, update `doc/feature.md`/`doc/task/README.md`, and move story only if DoD is met.

## Exact Pure Interface

```kotlin
data class EmptyTapSample(val x: Float, val y: Float, val eventTimeMs: Long)

object FocusLockTapPolicy {
    fun acceptsEmptyTap(
        armed: Boolean,
        moving: Boolean,
        longPressTriggered: Boolean,
        pointerCount: Int,
        selectedIndex: Int,
        x: Float,
        y: Float,
        width: Int,
        height: Int
    ): Boolean

    fun isDoubleTap(
        first: EmptyTapSample?,
        second: EmptyTapSample,
        timeoutMs: Long,
        slopPx: Float
    ): Boolean
}
```

Rules: a candidate is a completed, armed, stationary, non-long-press single-pointer tap on empty grid space (`selectedIndex < 0`) with finite coordinates inside positive view bounds. A pair requires non-negative elapsed time `<= timeoutMs`, finite positive timeout/slop, and Euclidean distance `<= slopPx`. `LensView` reads `ViewConfiguration.getDoubleTapTimeout()` and `scaledDoubleTapSlop`; do not introduce business-meaning constants for these platform values.

`LensView` testing API:

```kotlin
@VisibleForTesting internal fun isFocusPinnedForTest(): Boolean
@VisibleForTesting internal fun pinnedFocusForTest(): Pair<Float, Float>?
@VisibleForTesting internal fun toggleFocusLockAt(x: Float, y: Float): Boolean
@VisibleForTesting internal fun clearFocusLock()
```

`toggleFocusLockAt` returns false if detached/unlaid out, invalid or out-of-bounds; otherwise toggles, stores the exact requested point when pinning, invalidates, and announces state. Accessibility invokes it at `(width / 2f, height / 2f)`.

---

## Task 1 — Pure Empty-Tap Pairing Policy

**Files:** create `FocusLockTapPolicy.kt`, `FocusLockTapPolicyTest.kt`.

- [ ] **Step 1: RED.** Write separate unit tests for valid empty tap; icon index; unarmed; moving; long-press; pointerCount 2; NaN/±Infinity; zero width/height; each out-of-bounds edge; null first sample; exact timeout; timeout+1; negative elapsed; exact slop; slop+epsilon; invalid timeout/slop. Pair result must not return a point: the stored focus coordinate comes from second valid tap in caller.
- [ ] **Step 2: Run RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.FocusLockTapPolicyTest'`. Expected compile failure only because policy/types do not exist.
- [ ] **Step 3: GREEN.** Implement finite/bounds checks and pairing exactly as above. Use `kotlin.math.hypot` for robust distance; return false on invalid inputs.
- [ ] **Step 4: Verify.** Rerun focused class; all tests pass.
- [ ] **Step 5: Commit.** `git add app/src/main/java/com/mckimquyen/util/FocusLockTapPolicy.kt app/src/test/java/com/mckimquyen/util/FocusLockTapPolicyTest.kt && git commit -m "feat(fish-029): add focus tap policy"`.

## Task 2 — LensView Session State and Double-Tap

**Files:** modify `LensView.kt`; extend `LensViewGestureStateTest.kt`; create `LensViewFocusLockIntegrationTest.kt`.

- [ ] **Step 1: RED policy/render tests.** Extend `LensViewGestureStateTest` to pin precedence: active real touch centre > pinned visual centre > idle sentinel; assert helper never changes touch/hit-test coordinates.
- [ ] **Step 2: RED MotionEvent integration.** Host a real `LensView` in `OrganizationHarnessActivity`; dispatch through `dispatchTouchEvent`, draw after events. Cover first short empty tap no immediate change; second valid empty tap pins exact second-tap coordinates; third/fourth valid empty taps unpin; a single empty tap after timeout remains no-op; icon tap launches immediately and exactly once; icon long-press still reaches existing menu; movement/pan, pinch, pager handoff, edge-swipe, pull-down search, long-press and ACTION_CANCEL invalidate first tap; real touch temporarily overrides pin, ACTION_UP returns to stored pin; detach clears both.
- [ ] **Step 3: Verify RED.** Run focused JVM tests and `./gradlew compileDevDebugAndroidTestKotlin`; then run new integration class on TECNO only after Task 5 backup gate. RED must be missing focus lock behavior, not event/fixture failure.
- [ ] **Step 4: Minimum GREEN gesture state.** Add nullable first `EmptyTapSample`, pinned flag and separate x/y. On ACTION_UP, record a candidate only if current touch started/ended empty, remained under touch slop, was unarmed before long press, and was not search/edge/pinch/drag. On following ACTION_DOWN expire/check the prior candidate; only toggle after second valid empty ACTION_UP. Use `MotionEvent.eventTime`, platform double-tap timeout and slop. Icon path never waits. Invalidating events clear pending first sample.
- [ ] **Step 5: Minimum GREEN render.** In `drawGrid`, calculate visual focus centre by precedence: actual active touch, pinned focus, then idle sentinel. Use these coordinates only for warp calculations; preserve `mTouchX/Y` for hit-testing, DoF, hover and launch. Do not modify `LensGridCache` base geometry or allocate in its per-frame cell loop.
- [ ] **Step 6: Verify.** Rerun new policy/integration cases plus `LensViewPinchIntegrationTest`, `LensViewPagerGestureIntegrationTest`, `LensViewFocusRingIntegrationTest`, existing search/edge-swipe tests and `LensViewReattachIntegrationTest`.
- [ ] **Step 7: Commit.** `git add` Task 2 code/tests only; `git commit -m "feat(fish-029): pin focus on empty-space double tap"`.

## Task 3 — Host TalkBack Action (FISH-029 Exception Only)

**Files:** modify `LensAccessibilityHelper.kt` and `LensView.kt`; extend `LensAccessibilityHelperTest.kt`; add strings to base and 16 locale files; modify `ids.xml` only if needed for one unique action ID.

- [ ] **Step 1: RED JVM helper tests.** Test one host node custom action titled “Toggle focus lock”, host action pins at view centre/unpins, announcements distinguish states, repeated toggle is deterministic, and no per-app virtual node gains the custom action. Invalid/detached view returns false and leaves state unchanged.
- [ ] **Step 2: Run RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.a11y.LensAccessibilityHelperTest'`; expected missing host action/state assertion.
- [ ] **Step 3: RED instrumentation test.** On attached LensView, perform the actual host accessibility action twice; assert centre pin then unpin, no app launch, and accessible state announcement. Compile with `./gradlew compileDevDebugAndroidTestKotlin`.
- [ ] **Step 4: GREEN.** Implement host-level `ExploreByTouchHelper` action using the public host-node/host-action overrides; keep virtual app nodes and existing ACTION_CLICK/LONG_CLICK/hide/move behavior unchanged. Wire to `LensView.toggleFocusLockAt(center)`; report pinned/released state via localized text/event, not per-frame content descriptions.
- [ ] **Step 5: Translate.** Add `lens_focus_lock_action`, `lens_focus_locked`, `lens_focus_unlocked` (or fewer keys if one formatted state string suffices) to base + 16 locales. Keep all placeholders identical.
- [ ] **Step 6: Verify.** Run `LensAccessibilityHelperTest`, `LensStringTranslationTest`, `AllStringsTranslationTest`, new host accessibility integration, plus existing accessibility unit/widget/integration regression. `A11Y-002/A11Y-003` remain unchanged.
- [ ] **Step 7: Commit.** Stage only this task; `git commit -m "feat(fish-029): add focus-lock accessibility action"`.

## Task 4 — Lifecycle Reset and Regression Gate

**Files:** modify `ActHome.java`; extend/add Activity lifecycle instrumentation test only if existing `LensViewReattachIntegrationTest` cannot observe `onPause` reset.

- [ ] **Step 1: RED.** Pin focus in Home test seam; send `ActivityScenario.moveToState(STARTED/CREATED)` or call actual pause path and assert focus/pending sample clear; recreate and assert no restoration. Also test page detach/re-attach resets.
- [ ] **Step 2: Run RED.** Focused Activity test fails because Home pause does not yet clear pinned focus.
- [ ] **Step 3: GREEN.** In `ActHome.onPause`, clear focus on current `lensViews` before existing tilt reset and `super.onPause()`. `LensView.resetTouchState()`/`onDetachedFromWindow()` clear pending tap and pinned state; do not add listener/timer requiring disposal.
- [ ] **Step 4: Full regression.** Run gesture, pinch, focus ring, pager/edge-swipe, search swipe, accessibility and reattach tests. Confirm Home pause still stops tilt and search behavior is unchanged.
- [ ] **Step 5: Commit.** `git add` only lifecycle code/test; `git commit -m "fix(fish-029): reset focus lock on Home pause"`.

## Task 5 — Full Gate, TECNO Smoke, Audit and Closure

**Files:** `doc/task/AUDIT_FISH-029_2026-11-06.md`, `doc/feature.md`, `doc/task/README.md`, story move `todo/`→`inprogress/`→`done/` as justified.

- [ ] **Step 1: Host gate.** Run `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`, `scripts/check-secrets.sh`, `git diff --check`; record exact counts/warnings/APK SHA.
- [ ] **Step 2: Device backup.** Confirm only TECNO KJ7 `115333744A005844` target. Back up APK/test APK and app data; SHA-256 every data member; record HOME resolver, locale, font and animation scales. No implicit device selection and no `connected*` Gradle task.
- [ ] **Step 3: Device smoke.** Install exact candidate APKs; verify empty-space double-tap pins exact touched point, second double-tap releases, icon single-tap launches with no delay, touch/pan temporarily overrides pin, pinch/edge-swipe/search/long-press remain intact, TalkBack host action toggles at centre, pause/recreate/detach reset state. If ad/consent obscures a required interaction, follow owner-approved policy; never interact with ad content.
- [ ] **Step 4: Restore.** Restore original HOME role/APKs/app data; compare every file hash and device settings. Stop and disclose if exact restore fails.
- [ ] **Step 5: Audit.** Score only from recorded evidence. Document bypass/limits and any unrun case. Story closes only if score `>9.0`, host/device gates pass and restore is proven; no push unless separately authorized.
- [ ] **Step 6: Commit.** Commit audit/status separately; never stage `.idea/`.

## Self-review

- Design goal/decisions map to Tasks 1–4; host/device verification/restore/closure map to Task 5.
- State ownership and behavior names stay consistent: `EmptyTapSample`, `FocusLockTapPolicy.acceptsEmptyTap`, `FocusLockTapPolicy.isDoubleTap`, `LensView.toggleFocusLockAt`, `LensView.clearFocusLock`.
- No placeholders; no new dependencies; no unrelated TalkBack scope; no persistent focus state; no app-tap delay.

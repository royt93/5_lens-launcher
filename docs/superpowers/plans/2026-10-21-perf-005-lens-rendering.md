# PERF-005 Lens Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove confirmed per-frame/per-event waste in `LensView` while preserving icon geometry and gesture behavior.

**Architecture:** Keep changes local to `LensView`; reuse one source `Rect`, delete the unassigned workspace-background drawable path, and cache reduced-motion policy for each app touch gesture. Verify with existing real `MotionEvent` widget/integration patterns and profiling on the owner-locked Pixel 7 Pro.

**Tech Stack:** Kotlin, Android `Canvas`/`MotionEvent`, JUnit4, Robolectric, Android instrumentation tests, Gradle, `adb shell dumpsys gfxinfo`.

## Global Constraints

- Do not change grid geometry, bitmap selection, or user-visible gesture behavior.
- Sample `LensPhysicsPolicy.shouldReduceLensMotion(context)` once on app gesture `ACTION_DOWN`; use cached value through `ACTION_UP` or `ACTION_CANCEL`.
- Remove dead drawable field/method and all references; do not add replacement background behavior.
- Device target is Pixel 7 Pro `2B051FDH3006MU` for this session; never run device commands against another serial.
- If an ad covers UI during screenshot-based checks, stop and wait for owner confirmation.
- Do not add dependencies or production-only abstractions for tests.

---

## File Map

- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt`: scratch source `Rect`; remove unused workspace-background path; gesture-scoped policy value and reset.
- Modify `app/src/androidTest/java/com/mckimquyen/views/LensViewWidgetTest.kt` or create `LensViewPhysicsSamplingIntegrationTest.kt`: verify real dispatched event sequence and policy calls using seams available in the repo.
- Modify the existing relevant unit test only if sampling state is extracted as a pure helper; avoid extraction unless real widget dispatch cannot test behavior cleanly.
- Modify `doc/task/todo/p2-perf-perf-005-lens-draw-alloc-and-binder-per-event.md` → `inprogress/` at start, then acceptance checkboxes/results and move to `done/` only after verification.
- Update `doc/task/README.md`, `doc/feature.md`, and add `doc/task/AUDIT_PERF-005_2026-10-21.md` at close.

### Task 1: Establish RED regression for one policy sample per gesture

**Files:**
- Create or modify: `app/src/androidTest/java/com/mckimquyen/views/LensViewPhysicsSamplingIntegrationTest.kt`
- Read-only reference: `app/src/androidTest/java/com/mckimquyen/views/LensViewHapticIntensityIntegrationTest.kt`, `LensViewElasticOverscrollWidgetTest.kt`, `LensViewWidgetTest.kt`

**Interface:**
- Consume existing `LensView` constructor and `dispatchTouchEvent(MotionEvent)` behavior.
- Use existing static/mock interception patterns in test dependencies to count `LensPhysicsPolicy.shouldReduceLensMotion(Context)` calls; first inspect dependencies and test conventions. If static mocking is unavailable, prefer an existing injectable seam; do not invent an abstraction until verified necessary.

- [ ] **Step 1: Move story todo → inprogress and review test patterns.** Confirm static interception support and how tests set up a laid-out `LensView`, dispatch events, and release `MotionEvent` objects.
- [ ] **Step 2: Write failing integration test** for `ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP`; assert policy sampled once on DOWN, no extra event reads, and sampling resets so the next DOWN samples again.
- [ ] **Step 3: Add cancellation assertion**: dispatch DOWN, MOVE, CANCEL, then a second DOWN; verify the second gesture takes a new sample. Keep event times monotonic and recycle every event in `finally`/`use`-equivalent.
- [ ] **Step 4: Run targeted test to prove RED.** Run `./gradlew :app:connectedDevDebugAndroidTest` is forbidden. Build test APK via `./gradlew assembleDevDebugAndroidTest`; install/run only on locked Pixel with `adb -s 2B051FDH3006MU` and `am instrument`. Expected: assertions fail because current `onTouchEvent` queries policy on each event.
- [ ] **Step 5: Commit test-only RED** if independently meaningful; do not include unrelated `.idea` files.

### Task 2: Cache policy once per gesture and prove GREEN

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/views/LensViewPhysicsSamplingIntegrationTest.kt`

**Interfaces:**
- Produce a private gesture policy field, initialized to a safe default and assigned from `shouldReduceLensMotion(context)` only on app `ACTION_DOWN`.
- All policy-dependent logic during the gesture uses the stored field. Animation initialization and independent long-press callbacks may retain their separate policy sampling only if not reached per MotionEvent; audit each call site and preserve established behavior.

- [ ] **Step 1: Set the policy sample at the beginning of `ACTION_DOWN`.** Remove the unconditional top-level read in `onTouchEvent`; `ACTION_POINTER_DOWN`, `ACTION_POINTER_UP`, and `ACTION_MOVE` must read cached gesture value, not call the policy.
- [ ] **Step 2: Route gesture-scoped haptic checks through cached value.** Ensure hover/launch vibration triggered as part of the active gesture does not re-query system state. Preserve behavior for any call outside an active gesture by checking call paths first.
- [ ] **Step 3: Reset gesture validity/value on both `ACTION_UP` and `ACTION_CANCEL`, including early-return branches.** Use a `try/finally` or one shared reset helper only if required to cover every return; avoid stale cache after a terminal event.
- [ ] **Step 4: Run the focused Pixel instrumentation test.** Expected: all policy-count assertions pass; cached result remains stable when the underlying mocked/system policy value changes mid-gesture; next gesture samples updated value.
- [ ] **Step 5: Run relevant existing tests** `LensViewWidgetTest`, `LensViewElasticOverscrollWidgetTest`, `LensViewPinchIntegrationTest`, `LensViewHapticIntensityIntegrationTest`; expected all pass on locked Pixel, no ad-covered test interaction.

### Task 3: Reuse icon source Rect and remove dead background code

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Existing behavior tests: `app/src/androidTest/java/com/mckimquyen/views/LensViewWidgetTest.kt`, `LensViewDepthOfFieldWidgetTest.kt`

**Interfaces:**
- Add one private `Rect` scratch field adjacent to existing `mScratchRect` (which is a `RectF`).
- Keep `drawAppIcon` call signature and `Canvas.drawBitmap` arguments equivalent, except source rect comes from the reused field.

- [ ] **Step 1: Add or extend test** to render known bitmap dimensions into the expected icon destination; verify bitmap source and output remain unchanged using existing view rendering/test seams. If existing tests already assert rendered output sufficiently, cite them in audit rather than duplicating.
- [ ] **Step 2: Run test to verify baseline passes;** this test protects behavior rather than proving allocation, so verify the source allocation via code inspection or allocation profiler.
- [ ] **Step 3: Add `private val mScratchIconSourceRect = Rect()`** and replace per-icon `Rect(0, 0, appIcon.width, appIcon.height)` with `.set(0, 0, appIcon.width, appIcon.height)` immediately before drawing.
- [ ] **Step 4: Remove `mWorkspaceBackgroundDrawable`, `drawWorkspaceBackground`, the call in `onDraw`, its detach reset, and the now-unused import if any.** Confirm no assignments or resource references exist before deletion.
- [ ] **Step 5: Run focused LensView widget tests** and inspect `git diff` for identical destination rect/geometry and no remaining workspace background symbol.

### Task 4: Full verification, device profile, audit, and close

**Files:**
- Modify story under `doc/task/inprogress/` then move to `doc/task/done/`.
- Modify `doc/task/README.md`, `doc/feature.md`.
- Create `doc/task/AUDIT_PERF-005_2026-10-21.md`.

- [ ] **Step 1: Capture Pixel baseline.** Record device model/Android (`adb -s 2B051FDH3006MU shell getprop ro.build.version.release`), build commit, timestamp. Run a fixed number of controlled lens drags; capture `adb -s 2B051FDH3006MU shell dumpsys gfxinfo com.mckimquyen.lenslauncher framestats` and allocation/profile evidence if available. Do not install or profile on other devices.
- [ ] **Step 2: Run final project verification.** `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`; expected `BUILD SUCCESSFUL`, all unit tests pass, lint has no errors. Run `git diff --check` and `scripts/check-secrets.sh`.
- [ ] **Step 3: Install the built dev APK/test APK only on locked Pixel** using `adb -s 2B051FDH3006MU install -r ...`; run targeted instrumentation class with `adb -s 2B051FDH3006MU shell am instrument -w -e class <fully.qualified.TestClass> com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`. Check foreground before any manual interaction; stop if an ad overlays UI.
- [ ] **Step 4: Repeat identical gfxinfo interaction after change.** Compare total frames, janky frames, 50th/90th/95th/99th percentiles when present; state workload and noise. Do not claim frame-rate improvement if counts are too small or distributions overlap; report structural allocation/read reduction separately.
- [ ] **Step 5: Audit lifecycle/state.** Ensure touch cache resets on detach via `resetTouchState()` or explicit cleanup; verify no new handler/listener/timer was added. Compare touched device settings/prefs against baseline and restore any changed values in `finally`.
- [ ] **Step 6: Complete story, feature tracker, README, and audit.** Explain not-applicable test layers explicitly, include RED/GREEN, full checks, device profile, residual limits, and score. Move story to done only when all criteria pass and score exceeds 9.0.
- [ ] **Step 7: Commit and push only after score > 9.0, verification green, and state restored.** Stage only intended files; never stage `.idea`. Commit message must end with `Co-Authored-By: Claude Code <noreply@anthropic.com>`. Push authorized `dev` branch to `origin/dev`; verify remote SHA matches local.

## Self-review

- Spec goal and all three scope items covered by Tasks 2–3.
- Sampling test and RED/GREEN flow covered by Tasks 1–2.
- Widget/integration tests and explicit scope-dependent applicability covered by Tasks 1–3 and audit in Task 4.
- Pixel-only gfxinfo, system test routing, ad stop rule, state restoration, checks and score-gated push covered in Task 4.
- No new dependency, unrelated refactor, or extra production abstraction planned.

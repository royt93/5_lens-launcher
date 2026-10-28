# FISH-038b — Drag an app between lenses Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users hold and drag an app from `LensView` onto another lens chip, using the existing FISH-038 move and Undo path.

**Architecture:** Keep gesture classification pure in `LensDragPolicy`; use Android system drag-and-drop rather than adding custom move/hit-test state to the existing pan/pinch/edge-swipe machine. `LensDropStrip` renders chips over the home root and calls the originating `LensView`'s existing `moveAppToLens` path on a valid drop; `ActHome` owns and tears down the strip.

**Tech Stack:** Kotlin, Java, Android `View.startDragAndDrop` / `DragShadowBuilder` / `OnDragListener`, JUnit4, Robolectric, AndroidJUnitRunner.

## Global Constraints

- Device: TECNO KJ7 `115333744A005844` only for this whole session; never send commands to another device.
- On a one-lens install, long-press behavior remains unchanged: app menu appears at timeout.
- With 2+ lenses: hold past long-press timeout, then move beyond `touchSlop` to start drag; release without moving to open app menu.
- The source lens is never a drop target. Dropping outside a chip or cancelling performs no write.
- Drop reuses `LensView.moveAppToLens` → `LensMoveApplier` → `OnAppMovedToLensListener` → existing Undo Snackbar. Do not add another persistence path.
- Keep TalkBack long-click and existing "Move to lens…" menu path unchanged.
- Do not add dependencies. Do not modify Room schema or backup format.
- Add the accessibility description to all existing 16 locale resource sets, consistent with neighboring lens strings.
- Run unit/build and lint separately if kapt reports a stub `FileNotFoundException`; never accept a compile-error mutation as caught.
- Preserve unrelated working-tree changes and never stage `.idea`.

## File map

- Create `app/src/main/java/com/mckimquyen/views/LensDragPolicy.kt`: pure decision functions for long-press/menu/drag and target filtering.
- Test `app/src/test/java/com/mckimquyen/views/LensDragPolicyTest.kt`: truth table for every policy branch and target list.
- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt`: pending-hold state, system drag initiation, shadow, cancellation cleanup, callbacks, and a public move entry point that delegates to its existing private move function.
- Modify `app/src/main/java/com/mckimquyen/ui/ActHome.java`: own `LensDropStrip`, connect active lens callbacks, add/remove strip, and tear down safely.
- Create `app/src/main/java/com/mckimquyen/ui/home/LensDropStrip.java`: top chip strip, drag event highlighting/drop handling, checks current lens list, removes view/listeners on teardown.
- Modify `app/src/main/res/layout/act_home.xml`: no new permanent overlay; strip is attached programmatically to existing `rootLayout` so it exists only while dragging.
- Modify `app/src/main/res/values/strings.xml` and matching `values-*` files: one localized strip content description.
- Test gesture at `app/src/androidTest/java/com/mckimquyen/views/LensViewQuickActionsIntegrationTest.kt` or a focused `LensViewDragGestureWidgetTest.kt`; keep direct `showAppOptionsAtIndex()` tests unchanged.
- Test real drop in `app/src/androidTest/java/com/mckimquyen/ui/MoveAppToLensWidgetTest.kt` or a focused `LensDropStripWidgetTest.kt`; assert move, undo, no-op, and pager behavior.

## Task 1: Pure drag policy (red → green)

**Interfaces:**
- Produces `LensDragPolicy.shouldStartDrag(holdTriggered: Boolean, hasOtherLenses: Boolean, movedPastSlop: Boolean): Boolean`.
- Produces `LensDragPolicy.shouldOpenMenuOnRelease(holdTriggered: Boolean, dragStarted: Boolean, hasOtherLenses: Boolean): Boolean`.
- Produces `LensDragPolicy.shouldOpenMenuOnTimeout(hasOtherLenses: Boolean): Boolean`.
- Produces `LensDragPolicy.targets(lenses: List<LensWorkspace>, sourceLensId: String): List<LensWorkspace>`.

- [ ] **Step 1: Write JVM tests.** Create `LensDragPolicyTest.kt` asserting: drag only when hold + other lens + beyond slop; no drag if any condition false; release opens menu only for multi-lens pending hold without drag; single-lens timeout opens menu; multi-lens timeout does not; target list excludes source and preserves order; missing source still returns all existing lenses except matching id.
- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.views.LensDragPolicyTest'`; expect unresolved `LensDragPolicy`.
- [ ] **Step 3: Implement minimal pure functions.** Implement only the four signatures above; target filtering uses `filter { it.id != sourceLensId }`.
- [ ] **Step 4: Run green and mutation checks.** Rerun the test. Mutate each boolean operand and source exclusion one at a time; each valid, compiling mutation must fail a named test, then restore source and rerun green.
- [ ] **Step 5: Commit Task 1.** Commit only `LensDragPolicy.kt` and its test.

## Task 2: Change long-press resolution and start system drag

**Files:** `LensView.kt`, `LensDragPolicy.kt`, `LensViewQuickActionsIntegrationTest.kt` (or focused new gesture widget test).

**Interfaces:**
- Add `OnAppDragStartListener.onDragStarted(sourceView: LensView, app: App, sourceLensId: String)` and `onDragFinished()`; nullable `LensView.onAppDragStartListener`.
- Test seam: `@VisibleForTesting internal var dragStarter: (LensView, ClipData, View.DragShadowBuilder, Any) -> Boolean = { v, clip, shadow, payload -> v.startDragAndDrop(clip, shadow, payload, 0) }`. Widget tests replace it to record the call (and return true/false) so "drag requested" and the `false` fallback to the menu are observable without a real system drag; the real system drag is exercised only by the `adb input draganddrop` smoke.
- Add `LensView.moveAppToLensFromDrop(app: App, targetLensId: String, targetName: String)` as a public wrapper around existing `moveAppToLens`; keep `LensMoveApplier` as the only writer.

- [ ] **Step 1: Write failing widget tests.** On a real `LensView` with 2+ lenses, hold at an icon past timeout and assert menu has not appeared; release without movement and assert menu appears. Hold then cross `touchSlop` and assert `startDragAndDrop` request happened and menu did not. With one lens, assert menu still opens at timeout. Keep direct menu and accessibility tests intact.
- [ ] **Step 2: Run red on TECNO.** `./gradlew assembleDevDebug assembleDevDebugAndroidTest`; install only on locked TECNO with `adb -s 115333744A005844 install -r ...`; run focused class with `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewDragGestureWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`. Expect the new behavior assertions to fail before implementation.
- [ ] **Step 3: Implement pending-hold transition.** In the existing long-press runnable: for a non-empty selected icon and `mHasOtherLenses`, set the hold-triggered state and give haptic feedback without opening the menu; for one lens, retain immediate `showAppOptionsAtIndex`. On `ACTION_UP`, if hold triggered and no drag started, open the app menu for 2+ lenses. For empty-space long-press, keep current callback.
- [ ] **Step 4: Start API drag after slop** (through `dragStarter`, so the seam above is the only call site of `startDragAndDrop`). In `ACTION_MOVE`, before existing pan classification, if hold triggered, multi-lens, selected icon valid, and `exceedsTouchSlop(event.x - mTouchDownX, event.y - mTouchDownY, mTouchSlop)`, create `ClipData.newPlainText` and a `DragShadowBuilder` from the current cached app icon, set a local payload containing source view/app/source lens ID, and call `startDragAndDrop`. If it returns `false`, clear pending drag state and open the menu. Do not start drag from empty space. On start, notify `onAppDragStartListener` so `ActHome` shows chips.
- [ ] **Step 5: Ensure gesture cleanup.** On `ACTION_CANCEL`, `ACTION_UP`, detach and drag end, clear hold/drag payload state and restore view gesture state; never call `showAppOptionsAtIndex` after drag started. Preserve existing pan, pinch, edge-swipe, search, and `ViewPager2` horizontal hand-off behavior for touches not held past timeout.
- [ ] **Step 6: Run focused and existing tests.** Run `LensViewGestureStateTest`, `LensViewQuickActionsIntegrationTest`, `LensViewWidgetTest`, `LensViewAccessibilityWidgetTest`, and new drag gesture test. Verify menu release behavior, one-lens compatibility, and TalkBack path.
- [ ] **Step 7: Commit Task 2.** Commit only `LensView.kt` and the focused tests.

## Task 3: Implement LensDropStrip

**Files:** create `ui/home/LensDropStrip.java`; modify resource strings.

**Interfaces:**
```java
interface Host {
    Activity activity();
    ViewGroup root();
    List<LensWorkspace> lenses();
    void onDrop(LensView source, App app, String targetLensId, String targetName);
}
```
`LensDropStrip.show(LensView source, App app, String sourceLensId)`; `hide()`; `onDestroy()`.

- [ ] **Step 1: Add accessibility-string contract test.** Follow an existing resource-contract test to assert `lens_drag_targets_description` exists in default `strings.xml` and every supported translated `values-*/strings.xml`.
- [ ] **Step 2: Implement minimum UI.** Build a horizontally scrolling, accessible row of `TextView` chips from `LensDragPolicy.targets(host.lenses(), sourceLensId)`. Use lens names as labels, `contentDescription` from the new string, minimum 48dp target size, and add the strip to `rootLayout` at top-center with the system-bar inset accounted for. Hide on construction.
- [ ] **Step 3: Implement drag event handling.** Each chip stores its `LensWorkspace` ID. `ACTION_DRAG_STARTED` accepts only the in-process FISH-038b payload; `ACTION_DRAG_ENTERED` highlights that chip; `ACTION_DRAG_EXITED` restores normal appearance; `ACTION_DROP` re-reads `host.lenses()`, rejects missing/current source IDs, calls `host.onDrop`, and returns true only for a valid target; `ACTION_DRAG_ENDED` hides the strip. Drop outside a chip does not call host.
- [ ] **Step 4: Verify lifecycle in a focused test.** Test target exclusion, highlight enter/exit, valid drop callback exactly once, invalid/deleted target no callback, cancellation hide, accessibility description, and min target size. Run resource contract test and focused widget test.
- [ ] **Step 5: Commit Task 3.** Commit the strip and strings/tests.

## Task 4: Wire ActHome and reuse move + Undo path

**Files:** `ActHome.java`, `LensViewHolder.java` only if needed for callback wiring, `LensDropStrip.java`, `LensView.kt`.

- [ ] **Step 1: Create failing wiring test.** In a widget test, launch `ActHome` with two seeded lenses; trigger source `LensView` drag-start callback; assert chip strip appears with only the other lens. Drop onto the chip and assert `LensMoveApplier`-backed preferences change and the existing `LensViewHolder` Undo Snackbar is offered. Tap Undo and assert both lens states exactly restore.
- [ ] **Step 2: Wire minimal host in ActHome.** Create one `LensDropStrip` for the Activity, use `rootLayout` and `lensHolder.currentLenses()`, and install `onAppDragStartListener` when `LensViewHolder.bindLensView` binds a page. `onDrop` calls `source.moveAppToLensFromDrop(app, targetId, targetName)`. Revalidate source and target IDs against the latest lens list before calling.
- [ ] **Step 3: Handle cancel and destroy.** Hide strip on drag end, `ACTION_DRAG_ENDED`, active page change, and `ActHome.onDestroy`. `LensDropStrip.onDestroy()` clears each chip listener and removes the strip from parent. Ensure a recycled page has one listener, not duplicates.
- [ ] **Step 4: Run widget tests.** Run existing `MoveAppToLensWidgetTest` and new end-to-end drag/drop test; confirm menu move and drag move both use `LensMoveApplier`, Undo restores scope/selection/hidden keys, and dropped-outside/cancelled drags leave preferences unchanged.
- [ ] **Step 5: Commit Task 4.** Commit only wiring and its test.

## Task 5: TECNO regression, audit, close and push

- [ ] **Step 1: Full JVM/build gate.** `./gradlew testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`; parse XML and record counts, no failures/skips.
- [ ] **Step 2: Lint separately.** `./gradlew lintDevDebug`; record warnings and require 0 errors.
- [ ] **Step 3: Static checks.** `git diff --check`; `bash scripts/check-secrets.sh`; verify no resource locale missing.
- [ ] **Step 4: Backup TECNO data and state.** Device `115333744A005844` only. Save the full app-data tar and record locale, font scale, READ_CONTACTS grant and hashes before installing; never read/invoke any signing credential.
- [ ] **Step 5: Install and run focused tests.** Install `devDebug` and `devDebugAndroidTest` with `adb -s 115333744A005844`; run gesture, strip, `MoveAppToLensWidgetTest`, menu and accessibility tests. Check screenshot before further test actions; if any ad is visible, stop and wait for user confirmation.
- [ ] **Step 6: Real-device smoke.** Use `adb -s 115333744A005844 shell input draganddrop` from a real app icon to a visible chip; verify chip highlight, target lens scope, source lens state, Undo. Also test release-at-place opens menu and one-lens install keeps timeout menu.
- [ ] **Step 7: Run full locked-device regression.** Run all 36 `ActHome*` classes plus `MoveAppToLensWidgetTest`, `LensViewQuickActionsIntegrationTest`, `LensViewWidgetTest`, `LensViewAccessibilityWidgetTest`, and `LauncherIntegrationTest` on TECNO only. Stop at first red.
- [ ] **Step 8: Restore app data.** Restore full backup byte-for-byte, compare every member hash, and verify locale/font/permission match baseline. Do not run more test actions afterward.
- [ ] **Step 9: Write audit.** Create `doc/task/AUDIT_FISH-038b_2026-10-28.md` with score, unit/widget counts, mutations, gate output, TECNO model/Android/APK SHA/timestamp, state restore hashes and honest limitations. Score must exceed 9.0 before push.
- [ ] **Step 10: Close story and docs.** Move FISH-038b `todo/` → `done/`; update `doc/task/README.md` and `doc/feature.md` immediately. Preserve exactly one `## 🟡 In progress` heading in `doc/feature.md`.
- [ ] **Step 11: Commit and push.** Commit only feature/test/docs files (never `.idea`); push `origin/dev` only if audit score > 9.0 and all gates pass.

## Self-review

- Spec coverage: gesture policy and one-lens compatibility (Tasks 1–2); system drag and shadow (Task 2); chips and accessibility (Task 3); shared move/undo and lifecycle (Task 4); TECNO-only smoke, regression and state restore (Task 5).
- Scope: one subsystem; no separate spec needed.
- No dependencies, Room schema or backup format changes.
- Known visible behavior change (multi-lens menu opens on release rather than timeout) is tested and called out in the audit.

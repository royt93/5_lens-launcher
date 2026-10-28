# FISH-038b — Drag an app onto another lens (design)

Date: 2026-10-28. Story: `doc/task/todo/p3-fish-fish-038b-drag-apps-between-lenses.md`. Device: TECNO KJ7 `115333744A005844` only, for the whole session (owner instruction 2026-10-28). Depends on `FISH-038` (menu path, `LensMoveApplier`) and `ARCH-002` (done; `ActHome` is 1,054 lines).

## Evidence check (code, 2026-10-28)

- `LensView.onTouchEvent` already arbitrates pan, pinch, edge-swipe, pull-down search and a horizontal-swipe hand-off to `ViewPager2` (`handleAppTouch`, `isHorizontalSwipeIntent`). A hand-rolled drag would add one more state to that machine.
- Long-press is a `Runnable` (`mLongPressRunnable`) that fires after `ViewConfiguration.getLongPressTimeout()` and calls `showAppOptionsAtIndex(mSelectIndex)` at once. `ACTION_UP` after a triggered long-press only clears `mLongPressTriggered`.
- Tests of the app menu call `showAppOptionsAtIndex()` directly (`LensViewQuickActionsIntegrationTest`, `MoveAppToLensWidgetTest`, `ActHomeTemporaryHidingWidgetTest`, `LensViewAccessibilityWidgetTest`); none depends on the moment the menu opens. TalkBack long-click also goes through `showAppOptionsAtIndex()`.
- `LensView.moveTargets(lenses, currentLensId)` (every lens except the current one), `mHasOtherLenses` and `OnAppMovedToLensListener` (target name + undo callback, consumed by `LensViewHolder` to show the Undo Snackbar) already exist from FISH-038. `LensMoveApplier.move` returns `null` for the same lens.
- No `startDragAndDrop` / `OnDragListener` exists in `app/src/main`.
- `adb shell input draganddrop <x1> <y1> <x2> <y2> [duration]` exists on the TECNO (Android 14), so a real system drag can be driven on the locked device.

## Decisions (owner, 2026-10-28)

1. Drop target: a **strip of lens chips** shown while dragging (not the pager dots, not edge auto-paging).
2. Start: **press and hold, then move**; the app menu opens **only if the finger lifts without moving** (a hold that moves becomes a drag).
3. Mechanism: the **system drag-and-drop API** (`View.startDragAndDrop`, `DragShadowBuilder`, `OnDragListener`).

## Design

### Gesture (only when `mHasOtherLenses`; with one lens nothing changes)

1. Hold on an icon past the long-press timeout: haptic feedback, `mLongPressTriggered = true`, and **no menu yet**. Remember the icon index and its `App`.
2. `ACTION_UP` without movement past `touchSlop`: open the app menu with `showAppOptionsAtIndex`, as today.
3. `ACTION_MOVE` past `touchSlop` while that hold is pending: start the drag with `startDragAndDrop` (drag shadow = the icon's bitmap from the existing icon cache). The system now owns the touch stream, so `ViewPager2`, pan, pinch and edge-swipe cannot compete; `LensView` receives `ACTION_CANCEL`, which the existing branch already winds down.
4. Empty-space long-press, pan before the timeout, pinch, edge-swipe and pull-down search keep their current behavior. TalkBack long-click and the "Move to lens…" menu entry are unchanged and remain the accessible path.

With a single lens (`mHasOtherLenses == false`) the long-press runnable opens the menu on timeout exactly as before.

### Drop strip

- A horizontally laid out strip of chips at the top of the home screen, hidden until a drag starts, listing `LensView.moveTargets(...)` only (never the source lens).
- Chip under the finger is highlighted on `DRAG_ENTERED` and restored on `DRAG_EXITED`. Reduce-motion skips the fade, not the drag.
- The drag carries a `LensDragPayload(sourceView, app)` as drag local state. `DROP` on a chip calls `LensView.moveAppToLens(app, targetLensId, targetName)` on the source view (today a private function, made `internal`). That function already calls `LensMoveApplier.move` and then `onAppMovedToLensListener` (target name + undo), which `LensViewHolder` turns into the Undo Snackbar, so the drop adds no write path and no second Undo. A `null` result (same lens) shows nothing.
- Drop outside any chip, `DRAG_ENDED` without a drop, or a system cancel: no write, strip hides.

### Units

| Unit | Responsibility | Depends on |
|---|---|---|
| `views/LensDragPolicy.kt` (pure) | `shouldStartDrag(holdTriggered, hasOtherLenses, movedPastSlop)`, `shouldOpenMenuOnRelease(holdTriggered, dragStarted, hasOtherLenses)`, `shouldOpenMenuOnTimeout(hasOtherLenses, ...)`, `chipsFor(lenses, currentLensId)` | `LensWorkspace` |
| `views/LensView.kt` | Replace the timeout menu with hold-pending state when `mHasOtherLenses`; call `startDragAndDrop`; new `onAppDragListener` (start/end) so the host can show and hide the strip | `LensDragPolicy` |
| `ui/home/LensDropStrip.java` | Build/show/hide the chips, own the `OnDragListener`, forward a drop to the host | host interface below |
| `ActHome` / `LensViewHolder` | Wire the strip: one-line host delegates; `onDestroy` removes listeners and hides the strip | existing holder |

`LensDropStrip.Host`: `activity()`, `lenses()` (list for chips), `currentLensId()`, `dragEnded()`. The drop itself needs no host call: the payload's source view performs the move.

### Errors and lifecycle

- `startDragAndDrop` returning false (no window, system refused): fall back to opening the menu, so a hold never silently does nothing.
- Rotation or `onDestroy` mid-drag: `LensDropStrip.onDestroy` clears listeners and hides the strip; no Activity reference leaks.
- A lens deleted during a drag: the strip re-reads the lens list on each drag start, and a drop on a target that no longer exists does nothing (`LensMoveApplier` writes only preference keys for the ids it is given, so the drop handler checks the id against `lenses()` first).
- Strings: chips use lens names; no new user-visible string except an accessibility description for the strip (added in `values` and the existing 16 locales).

### Out of scope (YAGNI)

Edge auto-paging to the next lens while dragging, dragging several apps, dragging from the Apps tab, reordering inside a lens by drag.

## Testing (TECNO KJ7 only)

- **Unit (JVM, red first, mutation-checked):** every `LensDragPolicy` branch, including one-lens behavior and "menu only on release without movement".
- **Widget:** hold then release opens the app menu; hold then move starts a drag and does **not** open the menu; one lens keeps the old timeout menu; drop on a chip moves via `LensMoveApplier` and Undo restores both lenses; drop outside any chip changes nothing; the source lens is not listed; `ViewPager2` stays on the same page during a drag; strip hides on `DRAG_ENDED`.
- **Regression:** the 36 `ActHome*` classes plus `MoveAppToLensWidgetTest`, `LensViewQuickActionsIntegrationTest`, `LensViewWidgetTest`, `LensViewAccessibilityWidgetTest`, `LauncherIntegrationTest`.
- **Smoke:** `adb shell input draganddrop` on the real icon onto a chip, with byte-for-byte app-data restore, lock-screen/ad checks per the session rules.
- Gate: `testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest`, `lintDevDebug` separately, `git diff --check`, `scripts/check-secrets.sh`; audit `> 9.0` before push.

## Risks

- The system drag stops `LensView` receiving moves, so the magnifier effect ends when a drag starts; accepted (a drag is a different mode).
- `adb input draganddrop` timing varies; tests wait for the strip to appear before dropping instead of using fixed sleeps.
- Changing when the menu opens (release instead of timeout) is the one visible behavior change for users with 2+ lenses; it is called out here and in the audit.

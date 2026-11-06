# FISH-029 — Focus lock by double-tap — Design

Date: 2026-11-06  
Story: `doc/task/todo/p3-fish-fish-029-focus-lock-macro-mode.md`  
Device: TECNO KJ7 `115333744A005844` only. No push.

## Goal

Let one-handed users pin the fisheye lens focus centre to a chosen point with a double-tap on empty grid space, then release it with another double-tap. Preserve existing icon tap, pan, pinch, edge-swipe and long-press behavior.

## Decisions

- Double-tap target: **empty grid space only**. Do not intercept icon taps or delay app launch to distinguish single from double tap.
- First accepted double-tap pins focus to the exact local `(x, y)` of the second tap. No snapping/clamping to nearest icon or grid centre.
- A further accepted double-tap on empty grid space unpins focus.
- A single tap while pinned does not unpin and retains existing behavior: empty-space tap does nothing; icon tap launches that app. Explicit accessibility action toggles focus lock.
- Gesture-owned focus state lives in `LensView`, separate from `mTouchX/mTouchY`. A real pan/pinch/edge-swipe takes priority during the gesture; after `ACTION_UP`, rendering returns to pinned focus. Pinned focus never changes hit-testing, touch-selection, haptics, Depth-of-Field, app launch or accessibility bounds.
- TalkBack exception is limited to this story: add one `LensView` host accessibility action, **Toggle focus lock**, which pins at view centre when unpinned and unpins when pinned. Do not reopen A11Y-002/A11Y-003 or add unrelated accessibility work.
- State is session-only: no SharedPreferences, Room or backup. Reset on Home pause/destroy and `LensView` detach/recycle. No pinned state survives process recreation/relaunch.
- If reduced motion is active, state changes immediately without animation. Otherwise reuse existing lens show/hide animation semantics where they do not alter other gesture contracts. Avoid new animation timers/listeners that need disposal.
- This is a temporary visual focus aid, not a camera/privacy lock.

## Architecture and data flow

1. Extend the existing touch classifier in `LensView.handleAppTouch`; reuse `mTouchDownX/Y`, `mTouchSlop`, `mLongPressArmed`, `mLongPressTriggered` and existing `Handler` rather than adding a competing detector or scheduler.
2. Only a short, stationary, armed gesture that began and ended on empty space may count as a tap candidate. Pinch, pan past touch slop, page-swipe handoff, pull-down search, edge-swipe, long-press, cancellation and an icon hit cancel the candidate.
3. Use Android's platform double-tap timeout/slop semantics without adding a dependency. Keep the first empty tap's pending-up state small and bounded; a second valid empty tap before timeout toggles pinned state. A single empty tap continues its existing no-op behavior after timeout.
4. Render with a dedicated pinned-focus `(x, y)` pair and enabled flag, selected ahead of idle centre in `drawGrid`'s visual warp-centre calculation, but only when no real touch/pan/pinch currently owns the lens. Do not reuse/mutate `mTouchX/Y`; all hit-test and app launch decisions remain touch-only.
5. Add the custom accessibility action through `LensAccessibilityHelper`/`LensView` host node, not one action per app. Action toggles state at view centre and invalidates the view; TalkBack state description announces pinned/unpinned using localized strings.
6. Reset pending tap and pinned focus synchronously on `ACTION_CANCEL`, `resetTouchState`, `onDetachedFromWindow`, Home `onPause`, and Activity destruction. Existing callbacks remain disposed by current lifecycle paths.

## Error and edge behavior

- Icon tap always follows existing launch behavior; never delays launch for double-tap detection.
- Long-press on blank space still opens lens menu; long-press on icon keeps existing quick-action/focus-ring behavior.
- Pinch or horizontal pager interception cancels any pending first tap. No double-tap action may fire after detach, pause, page recycle or cancellation.
- Coordinates must be finite and within the `LensView` bounds; invalid/out-of-range samples are ignored.
- Repeated accessibility action toggles deterministically and never creates pending touch state.
- No lens or detached view: accessibility action returns false / no state change.

## Tests (RED before code)

### Unit

- Pure tap-candidate policy: two valid empty taps inside timeout/slop pin exact second-tap coordinates; another pair unpins.
- Single tap timeout does not pin; icon taps do not become candidates; movement, long-press, cancel, pinch, edge-swipe and search cancel first-tap candidate.
- Visual focus selection prefers real active touch over pinned focus, then pinned focus over idle centre; hit-test coordinates remain the real touch only.
- Session reset clears pending first tap and pinned state.

### Widget / integration

- Real `LensView` receives synthetic `MotionEvent`s through the same `onTouchEvent` path; empty-grid double tap pins visible warp centre, second double tap unpins.
- Single icon tap launches immediately without a timeout delay; icon long-press menu, pan, pinch, edge swipe and pull-down search continue unchanged.
- Pinned focus survives touch override until release, but clears on Activity pause/recreate and view detach/recycle.
- Accessibility node exposes one `Toggle focus lock` action; invoking it pins at centre/unpins, updates localized state announcement, and does not launch an app.
- Verify no per-frame allocation/geometry-cache invalidation is introduced by pinned focus; existing `LensGridCache` stays invariant.

### TECNO smoke

Backup APK/data/per-file hashes and device settings first. Verify empty-space double-tap pins/unpins, icon single-tap still launches, pan/pinch/edge-swipe/search still work, TalkBack action toggles at centre, pause/recreate clears state, then restore and compare hashes/settings. If an ad/consent covers a required control, follow owner-approved policy; do not tap “Start (Ads may appear)”.

## Scope / non-goals

- No persistence, toggle in Settings, new dependency, general TalkBack remediation, app-launch delay, nearest-icon snapping, lock-screen semantics or camera functionality.
- `A11Y-003` remains declined. Explicit owner exception permits only the FISH-029 action described here.
- FISH-034/FISH-035 remain separate sequential stories; do not start them in this design cycle.

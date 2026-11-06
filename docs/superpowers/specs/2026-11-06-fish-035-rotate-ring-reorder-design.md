# FISH-035 — Rotate the focus ring to cycle app order — Design

Date: 2026-11-06  
Story: `doc/task/todo/p3-fish-fish-035-rotate-ring-reorder.md`  
Device: TECNO KJ7 `115333744A005844` only. No push.

## Goal

With the focus pinned (FISH-029), a one-finger circular drag around the pinned centre rotates the whole displayed app order by one slot per step (like turning a wheel). The result is committed through the existing generation-guarded `AppPersistent.setAppOrderBatch` and the lens is marked frozen so Smart Focus will not reshuffle it. The same operation is reachable without the gesture through two accessibility host actions.

## Decisions (owner, 2026-11-06)

- **Trigger: only while focus is pinned.** Pan/pinch/edge-swipe/search/long-press behavior with no pinned focus is unchanged. This makes FISH-029 a real dependency and avoids a global gesture conflict.
- **Meaning: rotate the entire displayed list.** One step shifts every app one position (`[a,b,c,d]` → `[b,c,d,a]` forward, `[d,a,b,c]` back). No per-app reorder, no ring-only subset, no drag-to-slot.
- **Distinguishing from pan: swept angle + minimum radius.** A drag is a rotation only when the finger is at least a minimum distance from the pinned centre **and** its swept angle around that centre passes a threshold before the straight-line travel gets large; otherwise it stays a normal pan. Pure math, unit-testable.
- **Frozen/Smart Focus:** rotating writes the order with `setAppOrderBatch` and then sets the lens frozen (same effect as the existing “Freeze positions”). A lens that is **already frozen still rotates** and stays frozen: frozen protects against automatic re-sorting, not against the owner's explicit action.
- **Accessibility alternative:** two host actions on `LensView`, `Rotate order forward` and `Rotate order back`, calling the same one-step rotation. **Owner-approved TalkBack exception extension (2026-11-06):** exactly these two host actions, in addition to FISH-029's `Toggle focus lock`. A11Y-002/A11Y-003 stay declined; no other TalkBack work.
- Rotation is available only when the lens has ≥ 2 apps and focus is pinned for the gesture; the two accessibility actions work whenever the lens has ≥ 2 apps (no pin needed, since TalkBack users cannot draw a circle).

## Architecture

1. **Pure `util/RingRotation.kt`** (Context-free):
   - `angleStepsFor(sweepRadians, stepRadians)` → signed whole steps, truncating toward zero.
   - `rotate(apps: List<T>, steps: Int): List<T>` — cyclic shift, `steps mod size`, empty/single-element safe, never mutates the input.
   - `classify(...)` returning `PAN` / `ROTATE` / `UNDECIDED` from: distance from the pinned centre, accumulated signed sweep angle, accumulated straight-line travel, and the constants `MIN_RADIUS_DP`, `SWEEP_THRESHOLD_RAD`, `PAN_TRAVEL_DP`.
   - `shortestAngleDelta(prev, next)` so the sweep accumulator handles wrap-around at ±π.
2. **`LensView`**:
   - New small gesture state (`RotateTracker`: last angle, accumulated sweep, accumulated travel, decided flag) separate from `mTouchX/Y`, held only for the current gesture and reset on UP/CANCEL/detach/pause/pinch.
   - `ACTION_MOVE` runs the classifier only while `mFocusLockPinned && !mMoving && lens has ≥ 2 apps`. `ROTATE` locks the gesture into rotation (no pan, no search/edge-swipe, no ViewPager hand-off, no long-press), updates a transient live offset, and on each whole step calls the rotation callback; `PAN` falls through to the existing logic unchanged.
   - Rotation never changes `mTouchX/Y` hit-testing; the focus ring/pin stays at its stored centre.
3. **Commit (host)**: a new `OnRingRotatedListener` (like `OnAppMovedToLensListener`) tells `ActHome` which signed step count occurred; `ActHome` computes the rotated list from `lensViews.displayedApps`, calls `AppPersistent.setAppOrderBatch(rotated, lensId)`, then `settings.saveLensFrozen(lensId, true)` and `lensViews.refreshSmartFocus()`, exactly like `LensMenuCoordinator.toggleFreezeForLens` does when freezing. Rotation writes once per gesture end (accumulated steps), not per step, so a drag never floods the DB; the displayed order updates live from the in-memory list.
4. **Accessibility**: `LensAccessibilityHelper` gets two host actions beside the existing focus-lock one (new action ids `action_rotate_order_forward` / `action_rotate_order_back`, two action titles (forward/back) plus one announcement string, in all 17 locales) wired to the same host-side function the gesture uses, with a one-step rotation and an announcement of the new first app.
5. **State**: no new persistence beyond the existing order rows and frozen flag. Rotation state is gesture-local and cleared on Home pause, detach, ACTION_CANCEL and pinch.

## Error and edge behavior

- Fewer than 2 apps, unpinned focus (gesture), or a lens whose apps are not yet loaded: ignore, treat as normal gesture.
- A rotation of `steps % size == 0` is a no-op and writes nothing.
- Two-finger input cancels rotation and becomes pinch as today.
- If `setAppOrderBatch` fails, its existing rollback restores the in-memory order; the view reverts to the persisted order on the next refresh.
- Non-finite coordinates never classify as rotation.
- Locked lens (UI-035) never reaches the view with apps, so rotation cannot touch a locked lens's order.

## Tests (RED before code)

### Unit
- `RingRotation.rotate`: forward/back, wrap, `steps > size`, negative, empty, singleton, does not mutate input.
- `angleStepsFor`: truncation toward zero, sign, exact multiples.
- `shortestAngleDelta`: wrap across ±π both directions.
- `classify`: straight drag → `PAN`; circular arc beyond radius and sweep → `ROTATE`; inside the minimum radius never `ROTATE`; large straight travel before sweep → `PAN`; non-finite → never `ROTATE`.
- Exhaustive truth table that `PAN` and `ROTATE` are mutually exclusive.

### Widget / integration (TECNO)
- Pinned focus + synthetic circular `MotionEvent` stream rotates the displayed order by the expected number of steps; unpinned the same stream is a normal pan and order is unchanged.
- Straight drag with pinned focus stays a pan; icon tap, long-press menu, pinch, edge-swipe and pull-down search unchanged.
- Commit goes through `setAppOrderBatch` once per gesture and sets frozen; an already-frozen lens rotates and stays frozen; Smart Focus does not reshuffle afterwards.
- Accessibility host exposes exactly the two rotate actions plus the focus-lock one; invoking them rotates one step in each direction and returns false on a lens with < 2 apps.
- State clears on pause/detach/cancel.
- Persistence round trip: after rotation and a process-like reload (`switchLens`), displayed order matches the written order.

### Smoke (TECNO, adb)
Backup APK/data/hashes first; pin focus with `input swipe x y x y 80` double tap, perform a circular drag with a scripted multi-segment `input swipe` path or a `sendevent`-free sequence of short swipes along a circle, screenshot before/after and compare icon order; verify straight drag still pans; restore and compare hashes/settings. Stop on any ad overlay (R4); never tap “Start (Ads may appear)”. If adb cannot synthesize a faithful circular drag, say so and rely on the instrumented MotionEvent tests rather than claim a smoke pass.

## Scope / non-goals

- No per-app reorder, no ring subset rotation, no drag-to-slot, no new Settings toggle, no persistence beyond the existing order/frozen state, no new dependency.
- No other TalkBack actions; A11Y-002/A11Y-003 remain declined.
- FISH-040 (chromatic aberration) is unrelated and not started.

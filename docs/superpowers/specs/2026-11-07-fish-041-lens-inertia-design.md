# FISH-041 — Lens centre inertia (spring follow) — Design

Date: 2026-11-07  
Story: not yet created (`doc/task/todo/` gets `p3-fish-fish-041-lens-inertia.md` at the start of the plan, moved to `inprogress/` at Task 1).  
Branch: `dev` only (owner rule). Device: **TECNO KJ7 `115333744A005844` only** (owner 2026-11-07, "chỉ dùng tecno"); the main app is installed there, so back up APK + data + hash before any install and restore exactly. Owner allows closing ads via adb, never click ad content. No push unless the owner asks, never stage `.idea`.

## Goal

An opt-in feel effect: while the finger pans the lens, the optical effects follow the finger through a short exponential lag instead of snapping to it, so the lens has weight. It must never change which icon is under the finger, launch, haptics, the ring gesture or the pager, and it must stay inside the 120 Hz frame budget.

## Decisions (owner, 2026-11-07)

- **What lags: the optical effects only.** Icon warp/scale, depth-of-field distance, vignette, focus peaking and the chromatic-aberration direction use a lagged "visual centre". Hit-test, `mSelectIndex`, `launchApp`, haptics, ring rotation, search swipe and pager keep the real finger centre `mTouchX/Y`.
- **Lag: one fixed constant, `INERTIA_TIME_CONSTANT_MS = 100`**, no slider, no levels.
- **Switch:** own setting `lens_inertia`, default **off**, included in reset-to-defaults, 17 locale strings, Settings switch row. Independent of FISH-034/039/040.
- **Cost rule:** measure off vs on three times each on the TECNO and do not claim a pass without a valid (more than 1600 frames) measurement. If it exceeds the budget, apply the fallback in "Frame budget" below and ask the owner, do not ship silently.

## Design details settled here (derived from the code, not asked)

1. **Gate.** `hasActiveTouch && mMoving && mDrawType == APPS && !reduceMotion`. Not while focus is pinned (FISH-029), not on tilt (FISH-033), not idle, not during pinch or ring rotation. Reduced motion disables it like `LensOverscrollPhysics.isEnabled`.
2. **Two centres in `drawGrid`.** Today the loop computes each rect with `lensCenterX/Y` and then tests `isInsideRect(mTouchX, mTouchY, rect)` against that same rect (`LensView.kt` hit test, ~line 2242). With inertia the loop computes the **selection rect with the finger centre** (this is what picks `selectIndex`) and the **drawn rect with the lagged centre**. When the two centres are less than `INERTIA_SAME_CENTRE_PX = 0.5` apart, only one rect is computed (so a settled lens costs exactly what it costs today).
3. **Launch bounds.** `mRectToSelect` is used as the bounds of the app-open transition. It is set to the **drawn** rect of the selected icon (what the user sees), while the index still comes from the finger. This is the one place where the two centres meet; it needs its own test.
4. **No new animation driver.** `LensAnimation` runs only for its configured duration; after that the view redraws only on `ACTION_MOVE`. Inertia needs frames after the finger stops, so `onDraw` calls `postInvalidateOnAnimation()` while `|visual − finger| > INERTIA_EPSILON_PX = 1`. It stops when settled, on release, on detach and in `resetTouchState`. It never starts a second `Animation`.
5. **Time step.** `dt` comes from `drawingTime`/`System.nanoTime()` deltas, clamped to `MAX_DT_MS = 50` so a hitch cannot teleport the centre. `visual += (target − visual) · (1 − e^(−dt/τ))` is frame-rate independent; no overshoot, no oscillation, no stability threshold needed.
6. **Release.** On `ACTION_UP`/cancel the visual centre snaps to the finger centre before the hide animation and `launchApp`, so the hide animation and the launch are identical to today.
7. **Down.** On `ACTION_DOWN` and when a pan is confirmed, the visual centre is set to the finger so there is no initial sweep from a stale position.
8. **Flag sampling.** Read once per lens show in `LensAnimation.init` next to the other flags, never per frame, reset on detach.
9. **Elastic overscroll.** The target is the finger coordinate **after** `LensOverscrollPhysics` (the existing `mTouchX/Y`), so the two effects compose.
10. **API < 31.** Nothing differs; no new platform API.

## Architecture

1. **Pure `views/LensInertia.kt`** (Context-free, unit-tested): constants `TIME_CONSTANT_MS`, `EPSILON_PX`, `SAME_CENTRE_PX`, `MAX_DT_MS`; `shouldApply(enabled, panning, reduceMotion)`; `step(visual, target, dtMs): Float`; `isSettled(visualX, visualY, targetX, targetY): Boolean`; `sameCentre(...)`. No Android types.
2. **`LensView`**: two floats `mVisualX/Y`, one `Long` last-frame time, the gate computed once per frame next to `chromaOn`, the second rect inside the cell loop only when the centres differ, and `postInvalidateOnAnimation()` at the end of `drawGrid` while unsettled. No new objects per frame.
3. **Settings**: `UtilSettings` key/default/getter, `ResetEntries` entry, `frm_settings.xml` row + divider, `FrmSettings` listener, 17 locale strings (same shape as FISH-034/039/040).

## Frame budget

The second rect computation touches every cell while the lag is visible. It is arithmetic on already-resolved values and allocates nothing, but the render path was already close to the 120 Hz budget (FISH-040 had to lower its cap). Measure with the FISH-040 method: `dumpsys gfxinfo` after `reset`, eight diagonal drag loops, more than 1600 frames per run, off and on interleaved, three runs each, on the TECNO. Pass: jank does not rise by more than 1 percentage point and the mean frame bin does not rise by more than 0.5 ms. Fallback if it fails: compute the selection rect only for cells whose base rect lies within the lens-hood radius of the finger (the only ones that can contain it); re-measure; if it still fails, stop and ask the owner. A pass is only claimed for the TECNO.

## Out of scope

No slider or levels, no per-lens override, no inertia while pinned/tilted/idle/pinching, no change to ring rotation, search swipe, pager, haptics or selection rules, no spring overshoot, no new dependency.

## Testing

- **Unit (`LensInertiaTest`)**: `step` converges, is frame-rate independent (one 32 ms step equals two 16 ms steps), never overshoots, dt 0 keeps the value, non-finite input is ignored, `dt` clamp; `isSettled` and `sameCentre` truth tables; `shouldApply` truth table (enabled × panning × reduce-motion).
- **Source guard (`LensInertiaNoAllocationTest`)**: the new `LensView` functions contain no allocation keywords (static check; honest about being static).
- **Widget (touch path on a bare view)**: with a lagged centre set apart from the finger, `selectedIndexForTest` is the finger's cell, not the drawn one; `mRectToSelect` equals the drawn rect of that index; centres equal gives the identical rects to inertia off; release snaps the centre; idle, pinned, tilt and reduced motion never lag; geometry/`LensGridCache` identical off vs on; `postInvalidateOnAnimation` stops when settled.
- **Integration (attached view)**: flag sampled at `LensAnimation.init` not per frame; detach/`resetTouchState` clears the centre and the redraw loop; a real `ACTION_DOWN`/`MOVE`/`UP` sequence ends with the finger's icon launched and the centre snapped; ring rotation and pager swipe unaffected.
- **Settings widget**: switch default off, saved true restores on, toggle persists, reset returns to off.
- **Device (TECNO only)**: smoke by real touch through `UiAutomation.injectInputEvent` with screenshots (mid-drag lens offset from the finger, released = idle 0 px), frame budget as above, mutation checks (gate without `mMoving`; selection rect using the lagged centre; no release snap; no flag sampling), exact backup/restore.

## Limitations to state in the audit

The 100 ms constant is a first guess tuned on synthetic evidence, not on real hands; the owner has not used it by hand. A fast flick can leave the drawn icon visibly away from the finger for about 100 ms by design. Frame numbers come from one device. The 17 translations are AI-written.

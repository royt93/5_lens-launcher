# FISH-042 — Focus-boundary haptics — Design

Date: 2026-11-07  
Story: not yet created (`doc/task/todo/` gets `p3-fish-fish-042-focus-boundary-haptics.md` at the start of the implementation cycle).  
Branch: `dev` only. Device: **Samsung A50s `R58MA6WYRPE` only** (owner, 2026-11-07: "dùng samsung a50s cho mọi phiên"). Before any install/test, make a fresh APK + app-data + hash backup and restore exactly. Owner permits dismissing ads via adb, never click ad content. No push unless owner asks; never stage `.idea`.

## Goal

During a real lens pan, give one subtle tactile pulse when the currently hovered app crosses into or out of the sharp focus zone. Reuse the existing hover-haptics setting; add no setting or rendering work. Do not change app selection, launch, haptic cadence for app hover/launch, FISH-020 intensity selection, or any optical effect.

## Decisions (owner, 2026-11-07)

- **Boundary:** use `DepthOfField.FOCUS_ZONE` and the existing normalized focus distance. Track the **currently hovered/selected app** (`selectIndex`), not any icon in the cluster and not the nearest icon.
- **Both directions:** issue a pulse when that same selected app crosses from outside to inside the sharp zone, or from inside to outside.
- **Existing switch only:** gate on `UtilSettings.KEY_VIBRATE_APP_HOVER` (default off); do not add an independent setting, UI row, reset entry, locale strings or DB state.
- **Intensity:** fixed `HapticIntensity.LIGHT`, independent of the user's global LIGHT/MEDIUM/STRONG selection for normal hover/launch haptics.
- **Per-frame cap:** at most one haptic per rendered frame. If a focus-boundary pulse and ordinary hover-entry pulse would occur in the same frame, the boundary pulse has priority and the hover pulse is suppressed for that frame.
- **Reduced motion:** follow the existing haptic path. Do not emit when the current gesture's reduced-motion policy suppresses haptics.

## Design details settled here (derived from the code, not asked)

1. **State machine.** Keep the previous `insideSharpZone` boolean and the previous selected app identity for the current pan. On every live-pan frame with a valid selection, compute `DepthOfField.focusDistance` from the real finger centre (`mTouchX/Y`) to that selected app's base-rect centre. Hysteresis thresholds are `FOCUS_ZONE - HYSTERESIS = 0.18` to enter and `FOCUS_ZONE + HYSTERESIS = 0.22` to exit, with named `HYSTERESIS = 0.02f`. Between thresholds, retain prior state. This prevents chatter at the boundary.
2. **First observation is baseline.** When a gesture begins or selected app identity changes, record inside/outside state without vibrating. Only a subsequent state transition on the **same app during the same pan** emits a focus-boundary haptic. Changing selection never looks like a boundary crossing.
3. **Haptic path.** Add a small helper in `LensView` that emits fixed `HapticIntensity.LIGHT` through the existing vibration implementation, but does not mutate the user's selected `HapticIntensity`. Reuse existing test seam (`onHapticPerformed`) and catch/fallback behavior from FISH-020. Refactor only the minimal shared primitive needed so normal hover haptics keep their current configured intensity unchanged.
4. **One haptic per frame.** In the frame's selected-icon decision, calculate boundary transition before calling `performHoverVibration()`. If boundary haptic is emitted, consume `mMustVibrate` for that frame so hover-entry cannot also fire. No timers, Handler, allocation or new animation.
5. **Gate and reset.** Only an active `DrawType.APPS` live pan with non-null selected icon, `KEY_VIBRATE_APP_HOVER=true`, and reduced-motion haptics allowed. Reset previous app identity/state on ACTION_DOWN, ACTION_UP/CANCEL, selection loss, new gesture and `resetTouchState`/detach. No haptic on tap, focus pin, idle, tilt, pinch, ring rotation, app launch, or when the switch is off.
6. **DoF mode independence.** Compute the focus distance for this detector even when DoF rendering is disabled, just like the chromatic-aberration gate did; the boundary is defined by the user's finger and `FOCUS_ZONE`, not by whether blur is drawn.
7. **No new render effect.** The feature adds arithmetic and one boolean transition check in the existing selection loop. No changes to rects, `LensGridCache`, icon painting, app names, labels or accessibility tree.

## Architecture

One small pure helper, `views/FocusBoundaryHaptics.kt`, handles hysteresis and app-change baselining:

- `HYSTERESIS = 0.02f`, `ENTER_DISTANCE = FOCUS_ZONE - HYSTERESIS`, `EXIT_DISTANCE = FOCUS_ZONE + HYSTERESIS`.
- A stateful `Tracker` owned by `LensView` (allocated once), with `reset()`, `observe(appKey, distance): Boolean`, and a false result for first observation or app identity change. It returns true only on a real inside/outside transition for the same app.
- `LensView` remains owner of haptic emission, switch/reduced-motion gate and per-frame haptic priority.

The code addition must not allocate inside `drawGrid`; do not use lambdas, collection creation or formatted strings on the hot path.

## Testing

- **Pure unit tests:** first observation never pulses; inside transition pulses once; outside transition pulses once; hysteresis prevents chatter inside `[0.18,0.22]`; changing app identity re-baselines without a pulse; NaN/Infinity are ignored/reset safely; repeated same-side samples never pulse.
- **Widget tests with a real vibrator seam:** with hover setting on, a slow pan crossing the boundary for the same app emits exactly one LIGHT pulse on each crossing direction; hovering a different app over the boundary emits no boundary pulse; ordinary hover haptic and boundary haptic never double-fire in one frame; setting off and reduced-motion suppress pulse; selection/hit-test/launch geometry remain unchanged.
- **Integration tests:** continuous MotionEvent drag into and out of the focus zone emits boundary pulses; ACTION_CANCEL, ACTION_UP, detach and a new gesture reset tracker; focus pin, idle, tilt, pinch and ring rotation do not emit boundary pulses.
- **Mutation checks:** remove hysteresis; emit on every near-boundary sample; let app selection changes count as crossings; emit both hover and boundary pulse in one frame; ignore the existing hover switch; use global STRONG instead of LIGHT. Each mutation must be caught.
- **Device smoke:** Samsung A50s only; backup/restore as above. With system animator scale 0, the drag lens animation is duration 0 and a real pan may not activate (observed in FISH-041); the smoke must either prove the normal pan path under the A50s's original configuration or clearly record that boundary haptics cannot be manually reached there without temporarily changing animator scale. Never silently leave system scale changed.

## Limitations to state in the audit

The `0.02` hysteresis band and fixed LIGHT pulse are first values, not tuned with users who rely on haptics. User must enable the existing `vibrate_app_hover` switch. Rapid back-and-forth boundary crossings can still intentionally produce a pulse per crossing (at most one each frame); the hysteresis only removes jitter near the boundary, not repeated deliberate crossings. Device haptic motors vary, and A50s has system animator scale 0 in its restored baseline.

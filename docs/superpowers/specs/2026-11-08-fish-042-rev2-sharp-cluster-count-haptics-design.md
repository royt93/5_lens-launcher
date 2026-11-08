# FISH-042 rev2 — Sharp-cluster change haptics — Design

Date: 2026-11-08  
Supersedes the *trigger* of `docs/superpowers/specs/2026-11-07-fish-042-focus-boundary-haptics-design.md` (rev1). Everything rev1 says about the switch, intensity, reduced motion, per-frame cap and reset lifecycle is **kept**; only *what counts as an event* changes.  
Branch: `dev` only. Device: **Samsung S24 Ultra `R5CX613VZBR` only** (owner, 2026-11-08), `--user 0`, fresh backup before install and exact restore after. No push unless the owner asks, never stage `.idea`.

## Why rev1 is being replaced

rev1 pulsed when the **hovered** icon crossed `DepthOfField.FOCUS_ZONE`. An on-device probe (S24U, 2026-11-08, `doc/task/evidence/fish-042/boundary-probe-s24u.txt`) swept 1,521 touch points on grids of 40, 120 and 270 apps and found that the hovered icon is always within 0.054 of the finger (zone = 0.2): `hoveredOutsideZone=0`, `sameIconCrossings=0`. The system vibration log of a real 60-step pan also held only ordinary hover pulses, no LIGHT pulse. rev1 is correct code that cannot fire in real use. rev1's tests proved wiring, not reachability, because they forced state through a priming seam.

## What does change with the finger (measured)

The **sharp cluster** (icons whose base-rect centre has `focusDistance <= FOCUS_ZONE`) does change as the finger moves. One slow horizontal sweep, 160 steps (`doc/task/evidence/fish-042/cluster-probe-s24u.txt`):

| Apps | cluster size min / max / average | number of size changes |
|---|---|---|
| 40 | 2 / 4 / 2.7 | 12 |
| 120 | 4 / 8 / 6.1 | 36 |
| 270 | 8 / 16 / 12.4 | 41 |

## Decisions (owner, 2026-11-08)

- **Signal:** a haptic pulse when the **number of icons in the sharp cluster changes** (cluster grows or shrinks), not when one icon crosses a line. The owner first chose "finger crosses the cluster outline", then accepted the change of meaning after being shown that the finger can never leave the cluster while an icon is hovered.
- **Merge rule:** at most one pulse per change event and **at least 120 ms between two pulses**. A change that happens inside the 120 ms window after a pulse is not queued and does not produce a later pulse; the cluster size is simply re-read on the next eligible frame.
- **Kept from rev1:** existing `vibrate_app_hover` switch (default off), fixed `HapticIntensity.LIGHT`, live real pan only, reduced motion off, one haptic per frame with the cluster pulse winning over an ordinary hover pulse, tracker reset on `ACTION_UP`, `ACTION_CANCEL` and detach, optical centre (`mOpticX/Y`) so it agrees with DoF, vignette, peaking and fringes.
- **No new setting, UI, locale or DB state.**

## Design details derived from the code

1. **Counting.** In the per-cell loop, `drawGrid` already computes `focusDistance` from the optical centre for every cell when DoF, vignette or fringes are on. The counter reuses that value; when none of those is on, the haptic path computes the same distance for the cells (still arithmetic only, no allocation). The count is the number of cells with `focusDistance <= FOCUS_ZONE` for the frame.
2. **Hysteresis against flicker.** A cell is counted "in" at distance `<= ENTER_DISTANCE` (0.18) and stays in until `>= EXIT_DISTANCE` (0.22), as in rev1. The per-cell in/out state needs one `BooleanArray` sized to the grid, allocated once and resized only when the cell count changes (never in the draw loop).
3. **Event.** `observe(count)` returns true when the count differs from the previous frame's count. The first frame of a pan establishes the baseline and never pulses; `reset()` clears the baseline.
4. **120 ms spacing.** The pure tracker takes the frame time as an argument (`nowMs`), so it stays Context-free and unit-testable without a clock. `LensView` passes `drawingTime` / `SystemClock.uptimeMillis()`. A change inside the window is **dropped, not deferred**. The baseline still moves to the new count, so a pulse is never emitted later for a change the user has already moved past.
5. **Gate and priority.** Same as rev1: `hasActiveTouch && mMoving && gestureState == PANNING`, `KEY_VIBRATE_APP_HOVER` on, gesture reduced motion off. If a cluster pulse fires in a frame, the ordinary hover pulse of that frame is suppressed.
6. **Not an app-identity event.** Because the count is cluster-wide, selection changes (`selectIndex`) no longer matter and the "re-baseline on app change" rule of rev1 disappears.
7. **No render cost beyond arithmetic.** One extra compare per cell and one counter; the same cost class as the peaking and fringe checks that already run.

## Architecture

1. **Pure `views/SharpCluster.kt`** (Context-free, unit-tested): `ENTER_DISTANCE`, `EXIT_DISTANCE`, `MIN_PULSE_GAP_MS = 120L`; `Tracker` with `reset()`, `beginFrame()`, `markCell(index, focusDistance)`, `endFrame(nowMs): Boolean`, plus `count`. Fixed arrays, allocated once, resized outside the draw path.
2. **`LensView`**: replaces rev1's `mBoundaryTracker` usage and the per-selection distance block with one call per cell and one `endFrame` per frame; keeps `performLightHaptic()`, `FocusBoundaryHaptics.pulseIntensity` and the reset calls.
3. **Retire rev1 pieces** that become dead: `FocusBoundaryHaptics.Tracker.observe(appKey, distance)` and its app-identity logic. The `HYSTERESIS`/`ENTER`/`EXIT` constants and `pulseIntensity` stay (moved or reused).

## Testing

- **Pure unit tests:** no pulse on the first frame; pulse when the count rises and when it falls; no pulse when the count is unchanged; **hysteresis** (a cell hovering between 0.18 and 0.22 does not toggle); **120 ms rule** (a second change at 119 ms is dropped, at 120 ms is allowed); a change dropped inside the window moves the baseline (no late pulse); several cells changing in one frame give exactly one pulse; non-finite distances are ignored; `reset()` makes the next frame a baseline; per-cell state survives grid resize without a pulse.
- **Reachability test (the missing piece of rev1):** drive a real `LensView` with a real continuous sweep (no priming seam) over a 120-app and a 270-app grid and assert that **at least one pulse is emitted** and **no more than one per 120 ms**. This test fails if the feature is unreachable, which is exactly what rev1's tests could not detect.
- **Gate tests (kept, strengthened):** each gate (switch off, reduced motion, no real pan) is tested during a sweep that does produce a pulse when the gate is open, so only the gate under test can silence it.
- **Source guards:** the pulse intensity comes from the pure decision; the count uses the optical centre; `ACTION_UP` and `ACTION_CANCEL` reset the tracker.
- **Mutation checks:** drop the 120 ms gap; defer a dropped change instead of dropping it; remove hysteresis; count the hovered icon instead of the cluster; use the raw touch centre; emit two pulses in one frame; pulse on the baseline frame; ignore the switch / reduced motion / pan gate; hard-code STRONG. Each must be caught by a test that fails for that reason only.
- **Device (S24U only):** the same real-touch sweep through `UiAutomation.injectInputEvent`, then check `dumpsys vibrator_manager` for LIGHT pulses (`Step=20ms`, amplitude 80), spacing, and that none appear with the switch off. Never leave a system setting changed.

## Limitations to state in the audit

The pulse tells the user the cluster grew or shrank, not that a finger crossed a line; for a dense grid it may fire often (bounded by the 120 ms rule, at most about 8 per second). The count step, 120 ms and LIGHT are first guesses with no hand-tuning. The owner has not felt it. Sparse grids change the cluster size rarely, so the pulse is rare there. The cluster is computed from base-rect centres with the optical centre, not from the warped rects.

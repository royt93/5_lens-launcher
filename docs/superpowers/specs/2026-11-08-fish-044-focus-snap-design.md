# FISH-044 — Focus snap-to-icon — Design

Date: 2026-11-08  
Branch: `dev` only. Device policy: TECNO KJ7 `115333744A005844` only; fresh backup before any install and exact restore afterward. No push unless owner asks; never stage `.idea`.

## Goal

When the user pans slowly, gently bias the optical centre toward the nearest app icon centre, making focus positioning easier without changing selection or interaction.

## Owner decisions

- Trigger only during slow finger movement, threshold **120 dp/s**.
- Move only the optical centre. Hit-test, selection, launch, haptic and other finger-driven behavior stay on the real finger centre.
- Smooth pull, capped at **16 dp displacement** toward the nearest icon centre.
- Enable only when slow; gain fades smoothly to zero at 120 dp/s.

## Probe before implementation

Before writing code, a host/Robolectric geometry probe used KJ7 physical geometry (1080×2436 px, 480 dpi), default 18 dp icon size, and a 9 px screen sampling grid. At each sampled point it measured distance to the nearest base-rect centre:

| App count | Grid cells | Nearest distance p25 | p50 | p75 | Maximum |
|---:|---:|---:|---:|---:|---:|
| 20 | 20 | 34.2 dp | 51.4 dp | 68.3 dp | 145.5 dp |
| 40 | 40 | 24.1 dp | 34.5 dp | 45.5 dp | 99.2 dp |
| 120 | 120 | 14.0 dp | 19.6 dp | 25.0 dp | 54.1 dp |
| 270 | 276 | 9.2 dp | 13.0 dp | 16.2 dp | 32.5 dp |

Evidence: `doc/task/evidence/fish-044/nearest-icon-distance-probe.txt`. The 16 dp value is a **maximum movement**, not an activation radius. Therefore sparse grids can still receive a full 16 dp directional pull; dense grids often need less movement. This probe models grid geometry, not a human usability trial.

## Behavior

1. The setting is an independent `focus_snap` preference, default off. Add it to the Settings switch list, reset-to-defaults, and all 17 locale resources. No Room state or dependency.
2. Apply only during a real pan (`mMoving && gestureState == PANNING`) while gesture reduced-motion is off. Do not apply during pinching, tilt, ring rotation, pager interaction or idle.
3. Determine the nearest **valid app** base-rect centre in the current lens. Ignore unused grid cells. Resolve equal distances deterministically by the lowest app index.
4. Compute finger speed from consecutive raw touch positions in dp and their event timestamps. Reset the velocity sample on `ACTION_DOWN`, `ACTION_UP`, `ACTION_CANCEL`, and touch-state reset. Non-positive elapsed time or non-finite input produces no snap for that sample; update baseline safely so malformed input cannot poison later samples.
5. For speed `v`, define gain as `clamp(1 - v / 120, 0, 1)`. Let `d` be the vector from the real finger centre to the nearest icon centre. Optical snap offset is `d * min(1, 16dp / |d|) * gain`; if `|d|` is zero, offset is zero. This is continuous at 120 dp/s and never exceeds 16 dp.
6. Add snap offset to the real touch centre, then pass the result through `LensOverscrollPhysics`. Keep the unsnapped finger centre for hit-test and selection.
7. If FISH-041 inertia is enabled, its visual centre follows the snapped optical target as one stage; do not add a second independent lag filter. On release, existing inertia behavior returns optical centre to the finger.
8. Disabled setting, reduced motion, no real pan, invalid timing, and speed at/above 120 dp/s produce zero snap offset.

## Architecture

- Add a pure `FocusSnap` calculator with named constants for the 120 dp/s threshold and 16 dp cap. Keep the hot path allocation-free; avoid per-event collection creation.
- `LensView` owns the event-time/position baseline and invokes the calculator only with valid app base rectangles during the existing pan path. Preserve strict separation between raw finger centre and optical centre.
- Add a default-off `KEY_FOCUS_SNAP` preference and matching Settings row/resources, following existing lens-effect setting patterns.

## Tests

- Pure unit tests: zero speed/full capped pull; partial linear gain; speed at and above 120 gives zero; cap at 16 dp; near-target movement is shorter than the cap; zero-distance target; empty app list; invalid/non-finite values; non-positive elapsed time; deterministic nearest/tie handling where represented by the helper API.
- Widget/integration test: drive a real `LensView` with `ACTION_DOWN`, multiple timestamped `ACTION_MOVE`s and `ACTION_UP`; establish real pan beyond touch slop; assert slow sweep moves optical centre toward nearest valid icon by no more than 16 dp while `mSelectIndex`/hit-test still follow finger; assert fast sweep, disabled setting, reduced motion and non-pan gestures do not snap.
- Reachability test must use a real sweep, not a priming/test seam. Include sparse and dense grids so the behavior is demonstrably reachable at 40, 120 and 270 apps.
- Add mutation checks for setting gate, reduced-motion gate, pan-state gate, speed threshold/units, 16 dp cap, direction/sign, selection-centre separation, valid-app bounds, and overscroll ordering. Each mutation must be caught by a test that fails for that reason.
- Measure FISH-044 off/on frame behavior on KJ7 with sufficiently long, interleaved runs; record frame count and latency percentiles. Do not attribute geometry-only probe as performance or human-usability evidence.

## Risks and limits

- The geometry probe indicates likely stronger perceived effect on sparse grids, where nearest centres are farther away. The 16 dp movement cap and linear speed gain are first estimates, not hand-tuned values.
- A nearest-icon snap can bias focus away from the finger while preserving selection at the finger; test this separation explicitly.
- `baseRects` may include cells without apps, so production code must restrict candidates to valid app indices.
- Velocity samples depend on Android event timing; invalid or repeated timestamps must fail closed for that sample.
- No code, UI, preference, or feature tracker change is included in this design-only stage.

# FISH-009 — Live pinch-to-adjust lens curvature

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | High |
| Dependencies | FISH-004 (already shipped, physics preset baseline) |

## Context and evidence

Distortion/scale/animation-time are currently only adjustable via sliders in
`FrmLens` settings (`FISH-004`'s preset buttons sit over these same sliders).
This story makes the lens itself directly manipulable on the home screen — a
pinch gesture live-adjusts curvature/focus radius, turning a settings-menu
value into a tactile, in-place interaction. This is the flagship idea judged
**highest interaction-risk** of the three: it adds a second multi-touch
gesture to a view that already owns drag/pan fisheye physics.

## User story

As a user, I want to pinch directly on the home screen to adjust how strongly
the lens distorts, without opening settings.

## Acceptance criteria

- [ ] Gesture disambiguation from the very start: a `ScaleGestureDetector`
      (or equivalent) running alongside the existing single-pointer pan
      handling, with an explicit decision on precedence — a second pointer
      arriving mid-pan must not fight the in-progress pan, and a pinch must
      not be misread as two independent single-pointer drags. Write this as
      a state machine (mirrors `UI-022`'s own gesture-disambiguation
      requirement), not timing heuristics.
- [ ] Pinch adjusts a *temporary, session-only* curvature value by default —
      does not silently overwrite the user's saved `FrmLens` slider value
      unless they explicitly confirm "save as default" (never surprise a
      user by changing their settings via an accidental pinch).
- [ ] Respects `LensPhysicsPolicy.shouldReduceLensMotion()` — under reduced
      motion, either the live-pinch preview is disabled entirely (falls back
      to the existing settings-slider-only flow) or renders without
      continuous re-layout animation; decide and document which, don't leave
      it ambiguous.
- [ ] Clamped to the same min/max curvature bounds the `FrmLens` slider
      already enforces — no new out-of-range state reachable only via pinch.
- [ ] `LensGridCache`'s geometry cache is correctly invalidated/recomputed on
      curvature change without exceeding `PERF-001`'s per-frame allocation
      guarantee during the live pinch itself (this is the highest-frequency
      geometry-recompute trigger this app will have ever shipped — profile
      it, don't assume it's fine).

## Required test matrix

- [ ] Unit: gesture state machine (idle/panning/pinching transitions,
      pan-then-second-pointer, pinch-then-pointer-lift), curvature clamping.
- [ ] Widget/UI: "save as default" confirmation flow; reduced-motion fallback
      behavior (whichever was decided above).
- [ ] Integration: real multi-touch `MotionEvent` sequences proving pan and
      pinch don't fight each other; `LensGridCache` invalidation correctness
      across a live pinch sequence.
- [ ] Smoke: on the designated device, confirm pinch feels responsive and
      doesn't fight the existing drag/pan feel; `dumpsys gfxinfo` frame
      timing during an active pinch, not just a static comparison.

## Loop end condition

Same rubric as every story (`doc/task/README.md`): score the diff, not the
claim. Given this story's gesture-conflict risk is the highest of the three
flagship proposals, the integration-test evidence for pan/pinch
non-interference is weighted accordingly during audit — a score claiming
`> 9.0` without that specific evidence should be treated as unverified.
Unit + widget + integration + designated-device smoke required before any
push consideration.

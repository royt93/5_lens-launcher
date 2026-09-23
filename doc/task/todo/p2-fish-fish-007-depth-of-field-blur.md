# FISH-007 — Depth-of-field blur by focus distance

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | High |
| Dependencies | PERF-001 (already shipped, render hot-path baseline) |

## Context and evidence

`LensView`/`LensGridCache` already warp icon size/position by distance from
the fisheye focus point (`PERF-001`). No blur/opacity falloff exists — icons
far from focus are smaller but equally sharp. A real camera lens has depth of
field: things outside the focal plane blur. This makes the app's own name
literal rather than metaphorical, and no other grid launcher does this
(competitors use flat grids or simple size/opacity fades, not a lens-accurate
depth cue) — a genuine, ownable visual signature, not a utility feature.

This is the **flagship idea judged lowest-risk of the three flagship
proposals**, but "lowest risk of the flagship set" still means touching the
single highest-risk file in the codebase (`PERF-001`'s own characterization
of `LensView`'s hot path, continuous while dragging).

## User story

As a user, I want icons away from my current focus point to visually recede
(blur/soften) like a real camera's depth of field, reinforcing the lens
metaphor this launcher is named after.

## Acceptance criteria

- [ ] Blur/softening intensity is a pure function of existing distance-from-
      focus data already computed for size/position warp — no second
      geometry pass.
- [ ] Real `RenderEffect` blur (API 31+, same primitive `UI-007` already used
      for the search overlay) where available; a cheap non-blur fallback
      (e.g. opacity falloff only) below API 31 — never silently do nothing on
      older devices.
- [ ] Respects `LensPhysicsPolicy.shouldReduceLensMotion()` — blur amount is a
      static per-frame render cost either way, but the *transition* into/out
      of blur as focus changes must not animate under reduced motion.
- [ ] `PERF-001`'s no-new-per-frame-allocation guarantee holds: profile with
      `dumpsys gfxinfo` on the designated device before/after, not just
      "looks smooth."
- [ ] Off-focus icons stay legibly tappable — this is a visual effect, not a
      functional zone; hit-test geometry is completely unaffected (verify
      explicitly, this is the easiest way to silently break tap targets).

## Required test matrix

- [ ] Unit: blur-intensity-by-distance pure function — monotonic, clamps at
      the focus point (zero blur) and at max distance, degrades to the
      documented fallback below API 31.
- [ ] Widget/UI: reduced-motion path renders without a blur-transition
      animation; hit-test targets unaffected by blur rendering (a tap on a
      blurred icon still resolves to that icon).
- [ ] Integration: real-device frame-timing (`dumpsys gfxinfo`) before/after,
      matching `PERF-001`'s own verification method, across the documented
      60/90/120 Hz budgets.
- [ ] Smoke: real-device screenshot/video showing the depth-of-field effect
      live while dragging, on the designated device.

## Loop end condition

Every implementation round for this story ends with: an audit of the actual
code diff scored against `doc/task/README.md`'s existing rubric (Correctness,
Unit/Widget/Integration test quality, smoke results, security/privacy,
performance/regression risk, documentation truth) — never scored from a
completion claim alone. Unit + widget + integration tests covering every
branch above are required before scoring, not optional. Push only if the
score is `> 9.0/10` with a clean secret scan; otherwise keep iterating in
`inprogress` and record what's missing in the story's own audit notes.

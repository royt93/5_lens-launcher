# PERF-001 — Optimize the LensView render hot path

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye performance |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | CORE-001, CORE-002 |

## Context and evidence

`LensView.drawGrid` has cyclomatic complexity 14 and performs nested work per frame. `onDraw` reads settings and creates geometry that can be cached until viewport, settings or app state changes.

## User story

As a user, I need lens movement to remain smooth with hundreds of apps.

## Acceptance criteria

- [ ] Snapshot settings and precompute stable grid/labels/geometry outside the frame loop.
- [ ] Reuse draw objects and invalidate only affected regions/state.
- [ ] Define frame-time budgets for 60/90/120 Hz and a large app list.
- [ ] Preserve lens geometry and touch selection within numeric tolerances.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover geometry boundaries and cached-state invalidation.
- [ ] Widget/integration tests compare selection and launch behavior before/after optimization.
- [ ] Macrobenchmark/JankStats results show no regression and meet the agreed percentile budget.
- [ ] Tecno continuous-scrub smoke records frame timing, temperature and battery behavior.

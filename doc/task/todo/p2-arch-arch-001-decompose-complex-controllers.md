# ARCH-001 — Decompose complex UI controllers

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Maintainability |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | TEST-001 |

## Context and evidence

`ActSettings.onOptionsItemSelected` has cyclomatic complexity 20/cognitive 128; VIP listeners and `ScreenshotEditor` also combine many state transitions and side effects.

## User story

As a maintainer, I need behavior isolated into testable units so small changes do not cause unrelated regressions.

## Acceptance criteria

- [ ] Move menu routing, dialog coordination, ad state, VIP actions and export orchestration into cohesive collaborators/state reducers.
- [ ] Keep Activities/components focused on rendering and event forwarding.
- [ ] Define explicit interfaces for SDK, persistence, clock and file/export dependencies.
- [ ] Reduce target methods below agreed cognitive-complexity thresholds without changing UX.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Characterization unit/widget/integration tests pass before and after refactor.
- [ ] Tecno smoke covers all moved menu and lifecycle paths.
- [ ] Static complexity report and audit score improve.

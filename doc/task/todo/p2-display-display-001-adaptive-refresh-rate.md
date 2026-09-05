# DISPLAY-001 — Make refresh-rate policy adaptive

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Battery and smoothness |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | PERF-001 |

## Context and evidence

`BaseActivity` requests the highest available refresh rate on resume, including static screens, which may increase heat and power use.

## User story

As a user, I need smooth lens animation without unnecessary battery drain on static screens.

## Acceptance criteria

- [ ] Let the system choose on static/settings screens.
- [ ] Request a suitable mode only while interaction/animation benefits and release it afterward.
- [ ] Handle unsupported modes, battery saver, thermal constraints and lifecycle changes.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover mode-selection policy.
- [ ] Widget/integration tests cover lifecycle state changes.
- [ ] Tecno smoke records frame smoothness, mode transitions and thermal/battery observations.

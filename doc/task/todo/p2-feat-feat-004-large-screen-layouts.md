# FEAT-004 — Support landscape, tablets and foldables

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Adaptive UI |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | PERF-001, A11Y-001 |

## User story and value

As a large-screen user, I want the lens and settings to use available space and survive folds/rotation without reset.

## Acceptance criteria

- [ ] Remove unnecessary portrait locks and define compact/medium/expanded layouts.
- [ ] Recalculate grid/lens geometry on resize while preserving focused app and scroll state.
- [ ] Respect cutouts, hinges, taskbar and multi-window bounds.
- [ ] Avoid duplicated/hidden controls on configuration changes.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover geometry; screenshot/widget tests cover window classes and font scales.
- [ ] Integration tests cover resize/fold/rotation/process recreation.
- [ ] Tecno smoke covers portrait/landscape where supported; emulator/device tests cover foldable postures.

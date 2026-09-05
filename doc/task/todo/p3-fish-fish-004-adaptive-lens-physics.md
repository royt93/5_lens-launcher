# FISH-004 — Add adaptive lens physics and haptic profiles

| Field | Value |
|---|---|
| Type | exclusive |
| Status | todo |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | PERF-001, DISPLAY-001, A11Y-001 |

## User story and value

As a user, I want distinct lens movement/haptic profiles that feel responsive while respecting reduced-motion and battery settings.

## Acceptance criteria

- [ ] Ship a small set of mathematically specified profiles with stable selection geometry.
- [ ] Separate visual distortion from hit testing so every displayed selection launches correctly.
- [ ] Respect reduced motion, haptic disablement, battery saver and thermal limits.
- [ ] Preview changes safely and restore defaults instantly.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Property-based unit tests validate transform bounds/inverses.
- [ ] Widget/integration tests cover touch, cancel, lifecycle and accessibility fallbacks.
- [ ] Tecno benchmark/smoke meets frame, latency, thermal and battery budgets.

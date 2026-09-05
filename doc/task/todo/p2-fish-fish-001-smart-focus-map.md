# FISH-001 — Build a private Smart Focus Map

| Field | Value |
|---|---|
| Type | exclusive |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | CORE-001, DB-001, PERF-001, INSIGHT-001 |

## User story and value

As a returning user, I want likely apps subtly easier to reach at the current time while keeping spatial control and privacy.

## Acceptance criteria

- [ ] Rank locally from explicit favorites plus bounded recency/frequency/time-window signals; no cloud profile.
- [ ] Adapt magnification/attention without silently moving pinned apps or destroying spatial memory.
- [ ] Provide “Why this app?”, disable/reset controls and a deterministic non-personalized fallback.
- [ ] Meet frame-time and battery budgets with 300+ apps.
- [ ] Run an opt-in experiment with success metrics: launch time, wrong launches, disable rate and retention.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover ranking, cold start, ties, reset and clock boundaries.
- [ ] Widget/integration tests cover focus rendering, pinning and process death.
- [ ] Tecno smoke/benchmark passes offline at 60 Hz and available high-refresh mode.
- [ ] Product audit scores >9 and experiment guardrails are approved before default enablement.

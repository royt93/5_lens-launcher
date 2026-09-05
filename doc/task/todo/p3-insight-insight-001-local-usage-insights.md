# INSIGHT-001 — Offer private on-device usage insights

| Field | Value |
|---|---|
| Type | idea |
| Status | todo |
| Priority | P3 |
| Evidence | idea |
| Epic | User insight |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | DB-001, REL-002 |

## User story and value

As a user, I want optional local insights about frequently/rarely used apps so I can organize my launcher without uploading behavior.

## Acceptance criteria

- [ ] Feature is opt-in or clearly disclosed, stays on device and works offline.
- [ ] User can inspect, reset, export and disable collected data.
- [ ] Retention and backup rules are explicit; no ad-personalization use.
- [ ] Recommendations explain which local signals caused them.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover aggregation/retention/reset; UI tests cover disclosure and empty states.
- [ ] Integration tests prove reset/export/backup behavior.
- [ ] Network inspection during Tecno smoke shows no insight data leaving the device.

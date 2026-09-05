# PREF-001 — Replace fragile preference encodings with stable IDs

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Settings persistence |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | DB-001 |

## Context and evidence

Settings store enum ordinals and English display strings such as background mode. Reordering enums, changing copy or corrupt preferences can change meaning or crash lookup.

## User story

As a returning user, I need settings to survive upgrades and language changes.

## Acceptance criteria

- [ ] Persist stable, locale-independent IDs for every enum/mode.
- [ ] Safely migrate known legacy ordinal/string values and default unknown values.
- [ ] Separate stored domain values from translated labels.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Parameterized unit tests cover every legacy/current/corrupt value.
- [ ] Widget test switches locale without changing the selected behavior.
- [ ] Upgrade integration test preserves a representative settings fixture.
- [ ] Tecno upgrade smoke confirms lens/background/sort preferences.

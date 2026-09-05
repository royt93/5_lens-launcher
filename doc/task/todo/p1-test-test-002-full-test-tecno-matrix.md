# TEST-002 — Require full test layers and Tecno smoke coverage

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P1 |
| Evidence | decision |
| Epic | Quality engineering |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | TEST-001 |
| External prerequisites | Access to designated Tecno device |

## Context and evidence

The release process does not currently define one traceable matrix connecting each acceptance criterion to unit, widget/UI, integration and physical-device smoke coverage.

## User story

As a release owner, I need every behavior validated at the cheapest reliable layer and the final build exercised on Tecno hardware.

## Acceptance criteria

- [ ] Add a traceability matrix mapping every implemented case to unit, widget/UI, integration and smoke tests.
- [ ] Test happy path, validation/error path, lifecycle interruption, process recreation, offline/online transitions, accessibility and locale-sensitive behavior where relevant.
- [ ] Define Tecno smoke suite: clean install, upgrade, cold/warm start, default-launcher selection, lens touch/launch, install/remove app refresh, settings, icon pack, hide/lock, VIP/ads test mode, WebView policy, locale/RTL and background/foreground.
- [ ] Record Tecno model, chipset/RAM, Android/HiOS version, refresh rate, build SHA, variant, network state and timestamps.
- [ ] Capture logcat/crash/ANR evidence and frame/memory observations for failures.

## Implementation notes

Unit tests remain the default for deterministic logic. Widget/UI and integration tests must prove user-visible and boundary behavior without duplicating assertions mechanically. Physical smoke tests validate packaging, OEM behavior and real SDK integration.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Required test layers pass with no quarantined failure.
- [ ] Tecno smoke checklist is signed off for the exact release candidate.
- [ ] Any `Not applicable` entry has a written reason and audit approval.

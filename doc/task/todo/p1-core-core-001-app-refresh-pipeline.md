# CORE-001 — Serialize the installed-app refresh pipeline

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Launcher state |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | None |

## Context and evidence

Every `TaskUpdateApps.execute()` launches an untracked application-scope job. Bursts of package broadcasts can complete out of order and stale data can overwrite newer state in `RAppsSingleton`.

## User story

As a launcher user, I need installs, updates and removals to appear once and in the correct final state.

## Acceptance criteria

- [ ] One owned pipeline consumes package events with explicit debounce/conflate/cancel-previous behavior.
- [ ] App lists are immutable snapshots and only the newest generation may commit.
- [ ] State has loading/ready/error semantics and survives Activity recreation.
- [ ] Process death triggers a deterministic rebuild without blank or duplicated icons.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Stress test package add/change/remove bursts and out-of-order fake loaders.
- [ ] UI observers receive consistent snapshots on the main thread.
- [ ] No leaked job or Activity/Application weak-reference workaround remains without a documented need.

# LEAK-001 — Complete Fragment view lifecycle cleanup

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Lifecycle reliability |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | TEST-002 |

## Context and evidence

`FrmApps.onDestroyView()` clears `appAdapter` but retains RecyclerView/progress/utility references and does not explicitly detach the adapter. Existing memory-leak documentation incorrectly claims complete remediation.

## User story

As a user, I need repeated navigation and rotation to avoid retaining destroyed views or adapters.

## Acceptance criteria

- [ ] Detach adapters/listeners and clear all view-lifecycle references in `onDestroyView`.
- [ ] Use view binding scoped between `onCreateView` and `onDestroyView`.
- [ ] Ensure pending callbacks/coroutines cannot touch destroyed views.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit test lifecycle-owned state where meaningful.
- [ ] Fragment widget test rotates/navigates repeatedly without stale UI access.
- [ ] Integration test reloads app state across recreation.
- [ ] Tecno LeakCanary/manual smoke shows no retained `FrmApps` instance.

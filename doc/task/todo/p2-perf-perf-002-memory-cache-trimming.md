# PERF-002 — Bound bitmap memory and respond to trim events

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Memory performance |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | CORE-002 |

## Context and evidence

The manifest enables `largeHeap=true`; bitmap cache sizing follows max heap and lacks an explicit `onTrimMemory` policy. This can mask pressure and increase process footprint.

## User story

As a user on a memory-constrained phone, I need the launcher to avoid OOM and recover icons predictably.

## Acceptance criteria

- [ ] Define cache budget by measured icon dimensions/device class rather than relying on large heap.
- [ ] Trim or clear appropriate cache tiers for Android memory callbacks.
- [ ] Reload evicted icons asynchronously without blanking the launcher.
- [ ] Remove `largeHeap` when profiling proves the normal heap budget is sufficient.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover trim levels, eviction and reload keys.
- [ ] Integration test simulates memory callbacks during visible/hidden states.
- [ ] Tecno low-memory/background-reclaim smoke has no OOM or permanently missing icon.

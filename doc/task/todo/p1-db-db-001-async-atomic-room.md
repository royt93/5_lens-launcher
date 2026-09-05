# DB-001 — Move Room off the main thread and make usage updates atomic

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Persistence |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | CORE-001 |

## Context and evidence

`AppDatabase` enables `allowMainThreadQueries()`. `AppPersistent.incrementAppCount()` performs read–modify–write as separate operations, which can lose increments and block UI. Room also warns that schema export is not configured.

## User story

As a user, I need launching and sorting apps to remain responsive while usage data stays correct.

## Acceptance criteria

- [ ] DAO reads/writes expose suspend/Flow interfaces and run outside the main thread.
- [ ] Usage count increments atomically in SQL or a transaction.
- [ ] Component identity has a stable uniqueness rule and conflict strategy.
- [ ] Export Room schemas and preserve Sugar→Room migration behavior.
- [ ] Remove destructive downgrade behavior unless a documented product decision requires it.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] StrictMode detects no launcher database access on main.
- [ ] Concurrent increment and migration fixture tests pass.
- [ ] Upgrade/downgrade behavior and recovery are documented.

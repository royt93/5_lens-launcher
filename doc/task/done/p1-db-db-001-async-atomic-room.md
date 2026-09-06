# DB-001 — Move Room off the main thread and make usage updates atomic

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
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

- [x] DAO reads/writes are suspend APIs; public UI calls use optimistic memory updates and application-scope IO persistence.
- [x] Usage count increments atomically in a Room transaction.
- [x] Component identity has a unique index and insert-if-absent conflict strategy.
- [x] Room schemas 9 and 10 are exported and the Sugar 7→Room 10 migration fixture passes.
- [x] Destructive downgrade fallback was removed.

## Required test matrix

- [x] Unit tests cover atomic concurrency, targeted upserts, batch ordering and migration defaults.
- [x] Widget/UI tests exercise persistence callers through launcher/settings regressions.
- [x] Integration tests cover StrictMode main-thread safety and installed candidate startup.
- [x] Exact candidate smoke passed on the designated TECNO KJ7; evidence is in `AUDIT_FEAT_002_2026-09-06.md`.

## Verification and Definition of Done

- [x] A physical-device StrictMode instrumentation test detects no public persistence API disk access on main.
- [x] The 100-way concurrent increment, batch-order and migration fixture tests pass.
- [x] Upgrade uses explicit 7→8→9→10 migrations; downgrade fails safely instead of silently deleting user state.

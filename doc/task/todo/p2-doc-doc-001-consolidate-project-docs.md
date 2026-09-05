# DOC-001 — Consolidate stale project documentation

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Documentation truth |
| Estimate | 5 SP |
| Risk | Low |
| Dependencies | AUDIT-001 |

## Context and evidence

Test documents repeat an old 55-test count while 136 unit tests exist. `memory_leak.md` claims zero issues despite remaining lifecycle work; fix summaries conflict with current cache/view behavior; changelog and README are stale.

## User story

As a collaborator, I need documentation to describe the current verified system rather than historical claims.

## Acceptance criteria

- [ ] Make `doc/task/README.md` the status source and mark superseded audit reports clearly.
- [ ] Generate test counts/build status from CI artifacts.
- [ ] Update README with architecture, build/test, privacy, release and store-assets workflows.
- [ ] Reconcile changelog and remove unrelated project notes only after preservation review.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Link checker and documented commands pass.
- [ ] No conflicting “fixed/zero issue/test count” claim remains.
- [ ] Audit score is above 9.0 before push.

# TEST-001 — Establish trustworthy automated quality gates

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Quality engineering |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | None |
| External prerequisites | P0/P1 stories define required scenarios |

## Context and evidence

136 JVM tests pass, but historical docs still claim 55. Several UI tests only assert view existence/use sleeps, and `store-assets` has no test runner. Critical consent, race, security, process-death and export paths are uncovered.

## User story

As a maintainer, I need CI failures to represent real user regressions and security violations.

## Acceptance criteria

- [ ] CI runs Android unit tests, lint, selected instrumentation tests, release build and store-assets build/tests.
- [ ] Replace tautological, sleep-based and nondeterministic GC assertions with behavioral synchronization.
- [ ] Add matrices for consent, package bursts, cache invalidation, Room concurrency/migration, malicious WebView intents, shortcuts and process death.
- [ ] Add store route, migration, autosave race, export dimension and pixel-regression tests.
- [ ] Generate test counts/status for documentation instead of maintaining them manually.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] A deliberately injected representative failure is caught by each gate.
- [ ] CI artifacts include reports and failed-scenario evidence.
- [ ] Required checks are documented and enforced before release.

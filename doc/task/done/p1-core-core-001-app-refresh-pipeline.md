# CORE-001 — Serialize the installed-app refresh pipeline

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
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

- [x] One application-owned pipeline consumes package events with 150 ms debounce and cancel-previous behavior.
- [x] App lists and icon maps are immutable snapshots; a generation guard permits only the newest request to commit.
- [x] Application-owned state exposes loading/ready/error semantics independently of Activity recreation.
- [x] `RApplication.onCreate()` deterministically rebuilds after process death; cold-start instrumentation passed without blank or duplicate icon regressions.

## Required test matrix

- [x] Unit tests cover immutable commits, cancellation, newest-generation wins and failure preservation.
- [x] Widget/UI regression suite covers launcher/settings rendering, app events and lifecycle.
- [x] Integration tests cover package/app-state refreshes, activity boundaries and cold startup.
- [x] Exact candidate smoke passed on the designated TECNO KJ7; evidence is in `AUDIT_FEAT_002_2026-09-06.md`.

## Verification and Definition of Done

- [x] Cancellation and out-of-order fake-loader tests pass.
- [x] Commits and ready/error publication are dispatched on the injected main dispatcher.
- [x] The single application-owned job is cancelled explicitly; it holds no Activity reference.

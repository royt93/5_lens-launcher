# AUDIT-001 — Audit and score every change round before push

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P1 |
| Evidence | decision |
| Epic | Delivery governance |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | TEST-001, TEST-002 |

## Context and evidence

Historical documents contain stale “fixed” and “zero issue” claims. A repeatable score and evidence gate is required after every implementation round.

## User story

As a repository owner, I need an independent, evidence-based review before changes reach the shared branch.

## Acceptance criteria

- [ ] Create one audit record per round with commit/diff scope, findings, commands, test artifacts, Tecno results, residual risks and score breakdown.
- [ ] Score the eight dimensions defined in `doc/task/README.md`; explain every deduction.
- [ ] Re-audit after any fix caused by the review; never reuse the previous score.
- [ ] Block push at `<=9.0`, on any failed required test, missing Tecno smoke, secret finding, or unresolved in-scope P0/P1.
- [ ] On qualification, commit with task IDs and push the current branch; record remote ref and commit SHA.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] A second reader can reproduce every score from linked evidence.
- [ ] Git diff contains only reviewed files.
- [ ] Pushed SHA exactly matches the audited SHA.

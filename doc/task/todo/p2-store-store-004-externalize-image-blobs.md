# STORE-004 — Keep image blobs out of canonical project JSON

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Store tooling storage |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | STORE-001, STORE-002 |

## Context and evidence

When upload fails, screenshot selection can retain large data URIs in editor state. JSON and localStorage can exceed quota and make autosave/version control impractical.

## User story

As a designer, I need large screenshots stored durably without corrupting or bloating the project file.

## Acceptance criteria

- [ ] Canonical state references content-addressed assets by path/hash, never full image data URI.
- [ ] Failed upload remains an explicit retryable temporary state and cannot be silently persisted as canonical.
- [ ] Deduplicate assets and define safe orphan cleanup.
- [ ] Migrate legacy data-URI projects with progress and rollback.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover hashing, migration and quota failures.
- [ ] Component/integration tests cover offline retry and reload.
- [ ] Large multi-locale deck saves, clones and exports successfully.

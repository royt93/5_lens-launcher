# REL-002 — Establish a Play and privacy release gate

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Release governance |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | ADS-001, VIP-001, SEC-003 |

## Context and evidence

The app includes advertising identifiers and a third-party ad wrapper, requests permissions that lack a demonstrated runtime need, enables backup without extraction rules, and lacks a reproducible privacy/release checklist.

## User story

As a publisher, I need each release to match Play declarations and protect user data.

## Acceptance criteria

- [ ] Inventory merged permissions/components/SDK data collection for every release variant.
- [ ] Remove unused permissions or document and test the user-facing need.
- [ ] Define backup/data-extraction rules for app inventory, hidden/locked state, database and VIP entitlement.
- [ ] Keep Data Safety, privacy policy, consent UI and SDK behavior consistent.
- [ ] Generate SBOM/license inventory and verify release R8, signing, 16 KB alignment and target API.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Release checklist is executable and linked from project README.
- [ ] Merged-manifest and Play Console review have named evidence and owner.
- [ ] Production AAB passes build, lint and pre-launch smoke checks.

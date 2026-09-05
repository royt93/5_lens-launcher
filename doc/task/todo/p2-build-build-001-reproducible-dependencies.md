# BUILD-001 — Make dependencies and release packaging reproducible

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Supply chain |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | SEC-002, REL-002 |

## Context and evidence

The ad wrapper comes from JitPack, broad keep rules reduce R8 effectiveness, and `pickFirsts += ['**/*.so']` hides native collisions. Build output also warns about SDK/tooling and KAPT compatibility.

## User story

As a release engineer, I need identical reviewed inputs to produce a traceable artifact.

## Acceptance criteria

- [ ] Enable dependency verification/checksums and generate SBOM/license inventory.
- [ ] Pin and review advertising/security-sensitive SDK provenance and update policy.
- [ ] Resolve each native duplicate at its dependency source; remove wildcard `pickFirst`.
- [ ] Narrow ProGuard keep rules using release tests and update compatible AGP/Kotlin/Room tooling.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Clean release builds twice with matching dependency graph and documented artifact checksums.
- [ ] Unit/widget/integration suites pass against minified release-compatible code.
- [ ] Tecno release smoke detects no reflection/R8/native loading failure.
